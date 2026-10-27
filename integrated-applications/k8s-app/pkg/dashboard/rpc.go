package dashboard

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

func (app *stackUiApp) registerRpcHandlers() {
	session := *app.Session()

	k8sapi.Status.Handler(session, func(ctx context.Context, _ util.Empty) (k8sapi.StatusResponse, error) {
		app.mu.Lock()
		session := app.session
		stack := app.Stack
		app.mu.Unlock()

		record, ok := app.readClusterRecordWith(session, stack)
		if !ok {
			return k8sapi.StatusResponse{}, fmt.Errorf("could not read the cluster record")
		}

		name := record.StackId
		if name == "" && stack != nil {
			name = stack.InstanceId
		}

		response := k8sapi.StatusResponse{
			Name:            name,
			Phase:           record.Phase,
			FailureReason:   record.FailureReason,
			K8sVersion:      record.K8sVersion,
			MachineProvider: record.MachineProvider,
			Pools:           []k8sapi.StatusPool{},
			Nodes:           []k8sapi.StatusNode{},
			Maintenance:     []k8sapi.MaintenanceOperation{},
			NodeTotal:       len(record.Nodes),
		}

		poolNodeCount := map[string]int{}
		for _, node := range record.Nodes {
			poolNodeCount[node.Group]++
		}

		for _, pool := range record.Pools {
			response.Pools = append(response.Pools, k8sapi.StatusPool{
				Name:         pool.Name,
				Machine:      pool.Machine,
				Disk:         pool.DiskGb,
				Nodes:        poolNodeCount[pool.Name],
				ControlPlane: pool.Name == shared.GroupControlPlane,
			})
		}

		for _, node := range record.Nodes {
			response.Nodes = append(response.Nodes, k8sapi.StatusNode{
				Hostname:       node.Hostname,
				Group:          node.Group,
				AllocationId:   node.AllocationId,
				IpAddress:      node.IpAddress,
				JobId:          node.JobId,
				Machine:        node.Machine,
				Disk:           node.DiskGb,
				DesiredVersion: node.DesiredVersion,
			})
		}

		client, clientErr := K8sClientFromKubeconfig(LocalKubeconfigPath())
		if clientErr == nil {
			healthCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
			health, healthErr := client.NodeHealth(healthCtx)
			cancel()

			if healthErr == nil {
				response.NodeHealthKnown = true
				response.NodeReady = health.Ready
				response.NodeNotReady = health.NotReady
				response.NodeUnknown = health.Unknown
				response.ControlPlaneVersion = health.ControlPlaneVersion

				for i := range response.Nodes {
					node := &response.Nodes[i]
					node.Version = health.Versions[node.Hostname]
					node.State = health.States[node.Hostname]
				}
			}
		}

		snapshot, snapshotErr := maintenance.Snapshot()
		if snapshotErr != nil {
			response.MaintenanceError = snapshotErr.Error()
		} else {
			response.MaintenanceKnown = true

			names := make([]string, 0, len(snapshot))
			for name := range snapshot {
				names = append(names, name)
			}
			sort.Strings(names)

			for _, name := range names {
				operation := snapshot[name]
				response.Maintenance = append(response.Maintenance, k8sapi.MaintenanceOperation{
					Uid:      operation.Uid,
					NodeName: name,
					Kind:     operation.Kind,
					Phase:    operation.Phase,
					Error:    operation.Error,
				})
			}
		}

		upgrade, _, upgradeFound, upgradeErr := maintenance.RollingUpgradeSnapshot()
		if upgradeErr != nil {
			response.RollingUpgradeError = upgradeErr.Error()
		} else if upgradeFound {
			upgradeStatus := &k8sapi.StatusRollingUpgrade{
				Uid:           upgrade.Uid,
				TargetRelease: upgrade.TargetRelease,
				Phase:         upgrade.Phase,
				Error:         upgrade.Error,
				Nodes:         []k8sapi.StatusUpgradeNode{},
			}
			for _, node := range upgrade.Nodes {
				upgradeStatus.Nodes = append(upgradeStatus.Nodes, k8sapi.StatusUpgradeNode{
					Name:         node.Name,
					Group:        node.Group,
					Phase:        node.Phase,
					OperationUid: node.OperationUid,
				})
			}
			response.RollingUpgrade = upgradeStatus
		}

		backupStatus, backupErr := app.backupStatusRead()
		if backupErr != nil {
			response.BackupError = backupErr.Error()
		} else {
			backupInfo := &k8sapi.StatusBackup{
				StateFound:   backupStatus.StateFound,
				RequestFound: backupStatus.RequestFound,
			}
			if backupStatus.StateFound {
				backupInfo.ClaimUid = backupStatus.State.ClaimUid
				backupInfo.ClaimRequestUid = backupStatus.State.ClaimRequestUid
				backupInfo.LastAttemptPhase = backupStatus.State.LastAttempt.Phase
				backupInfo.LastAttemptError = backupStatus.State.LastAttempt.Error
				backupInfo.LastAttemptFinishedAt = dashboardUnixSeconds(backupStatus.State.LastAttempt.FinishedAt)
				backupInfo.LastSuccessFinishedAt = dashboardUnixSeconds(backupStatus.State.LastSuccess.FinishedAt)
				backupInfo.NextDueAt = dashboardUnixSeconds(backupStatus.State.DueAt)
				backupInfo.Retained = len(backupStatus.State.Retained)
			}
			if backupStatus.RequestFound {
				backupInfo.RequestUid = backupStatus.Request.Uid
				backupInfo.RequestPhase = backupStatus.Request.Phase
				backupInfo.RequestError = backupStatus.Request.Error
			}
			response.Backup = backupInfo
		}

		return response, nil
	})

	k8sapi.ProvisioningStatus.Handler(session, func(ctx context.Context, _ util.Empty) (k8sapi.ProvisioningStatusResponse, error) {
		app.mu.Lock()
		session := app.session
		stack := app.Stack
		app.mu.Unlock()

		record, ok := app.readClusterRecordWith(session, stack)
		if !ok {
			return k8sapi.ProvisioningStatusResponse{}, fmt.Errorf("could not read the cluster record")
		}

		entries := make([]provisioningEntry, 0, len(record.Nodes))
		for _, node := range record.Nodes {
			if node.Hostname == "" {
				continue
			}
			entries = append(entries, provisioningEntry{
				Hostname: node.Hostname,
				Group:    node.Group,
				Status:   k8sapi.ProvisioningStatusProvisioning,
				JobId:    node.JobId,
			})
		}

		health := provisioningResolve(ctx, session, app.provisioningClient(), entries)

		response := k8sapi.ProvisioningStatusResponse{
			Phase:           record.Phase,
			FailureReason:   record.FailureReason,
			NodeHealthKnown: health.Known,
			AllReady:        health.Known && len(entries) > 0,
			Nodes:           make([]k8sapi.ProvisioningNodeStatus, 0, len(entries)),
		}

		for _, entry := range entries {
			state := ""
			ready := false
			if health.Known {
				state = health.States[entry.Hostname]
				ready = state == k8sapi.NodeStateReady
			}
			if !ready {
				response.AllReady = false
			}

			response.Nodes = append(response.Nodes, k8sapi.ProvisioningNodeStatus{
				Hostname: entry.Hostname,
				Group:    entry.Group,
				JobId:    entry.JobId,
				Status:   entry.Status,
				State:    state,
			})
		}

		return response, nil
	})

	k8sapi.Kubeconfig.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.KubeconfigResponse, error) {
		path := filepath.Join(shared.ManagementMountPath, filepath.Base(shared.KubernetesConfigurationFileName))
		content, err := os.ReadFile(path)
		if err != nil {
			return k8sapi.KubeconfigResponse{}, fmt.Errorf("could not read the kubeconfig: %w", err)
		}

		return k8sapi.KubeconfigResponse{Content: string(content)}, nil
	})

	k8sapi.ScaleOut.Handler(session, func(ctx context.Context, request k8sapi.ScaleOutRequest) (k8sapi.ScaleOutResponse, error) {
		app.mu.Lock()
		stack := app.Stack
		app.mu.Unlock()

		if stack == nil || !stack.Ok {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf("the cluster stack is not available")
		}

		poolName := strings.TrimSpace(request.Pool)
		response := k8sapi.ScaleOutResponse{Pool: poolName, Nodes: request.Nodes}

		record, ok := app.readClusterRecordWith(session, stack)
		if !ok {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf("could not read the cluster record")
		}

		pool, found := dashboardPoolRecord(record, poolName)
		if !found {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf("unknown node pool: %s", poolName)
		}
		response.Pool = pool.Name

		if request.Nodes < 1 {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf("the target node count must be at least one")
		}
		if pool.Name == shared.GroupControlPlane {
			switch request.Nodes {
			case 1, 3, 5, 7:
			default:
				return k8sapi.ScaleOutResponse{}, fmt.Errorf(
					"the control plane must have an odd number of nodes from %d to %d",
					k8sapi.ControlPlaneMinNodes,
					k8sapi.ControlPlaneMaxNodes,
				)
			}
		} else if request.Nodes > k8sapi.MaxPoolNodes {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf(
				"the pool supports at most %d nodes",
				k8sapi.MaxPoolNodes,
			)
		}

		current := 0
		for _, node := range record.Nodes {
			if node.Group == pool.Name {
				current++
			}
		}

		if request.Nodes == current {
			response.Ok = true
			return response, nil
		}

		if request.Nodes > current {
			disk := request.Disk
			if disk == 0 {
				disk = pool.DiskGb
			}

			if !shared.ClusterScalePool(app, stack, pool.Name, request.Nodes-current, disk) {
				return k8sapi.ScaleOutResponse{}, fmt.Errorf("the pool could not be scaled")
			}
			response.Ok = true
			return response, nil
		}

		if pool.Name == shared.GroupControlPlane && request.Nodes < 3 {
			return k8sapi.ScaleOutResponse{}, fmt.Errorf(
				"the control plane must keep at least three nodes when it is scaled down",
			)
		}

		if err := dashboardRemovePoolNodes(ctx, app, stack, pool.Name, current-request.Nodes); err != nil {
			return k8sapi.ScaleOutResponse{}, err
		}

		response.Ok = true
		return response, nil
	})

	k8sapi.AddPool.Handler(session, func(_ context.Context, request k8sapi.AddPoolRequest) (k8sapi.AddPoolResponse, error) {
		app.mu.Lock()
		stack := app.Stack
		app.mu.Unlock()

		if stack == nil || !stack.Ok {
			return k8sapi.AddPoolResponse{}, fmt.Errorf("the cluster stack is not available")
		}

		name := strings.TrimSpace(request.Name)
		ok := shared.ClusterAddPool(app, stack, shared.ClusterPoolSpec{
			Name:    name,
			Machine: request.Machine,
			Nodes:   request.Nodes,
			DiskGb:  request.Disk,
		})
		if !ok {
			return k8sapi.AddPoolResponse{}, fmt.Errorf("the pool could not be added")
		}

		return k8sapi.AddPoolResponse{Name: name, Nodes: request.Nodes, Ok: true}, nil
	})

	k8sapi.NodeAction.Handler(session, func(ctx context.Context, request k8sapi.NodeActionRequest) (k8sapi.NodeActionResponse, error) {
		app.mu.Lock()
		stack := app.Stack
		app.mu.Unlock()

		if stack == nil || !stack.Ok {
			return k8sapi.NodeActionResponse{}, fmt.Errorf("the cluster stack is not available")
		}

		node := strings.TrimSpace(request.Node)
		if node == "" {
			return k8sapi.NodeActionResponse{}, fmt.Errorf("a node name is required")
		}

		action := strings.TrimSpace(request.Action)
		switch action {
		case k8sapi.NodeActionDrain,
			k8sapi.NodeActionCordon,
			k8sapi.NodeActionUncordon,
			k8sapi.NodeActionUpgrade,
			k8sapi.NodeActionRemove,
			k8sapi.NodeActionCancel:
		default:
			return k8sapi.NodeActionResponse{}, fmt.Errorf("unknown node action %q", action)
		}

		options := maintenance.Options{
			TimeoutSeconds:          request.TimeoutSeconds,
			Cordon:                  request.Cordon,
			Drain:                   request.Drain,
			DeleteVolatilePods:      request.DeleteVolatilePods,
			BypassDisruptionBudgets: request.BypassDisruptionBudgets,
			ForceDelete:             request.ForceDelete,
		}
		if options.TimeoutSeconds == 0 {
			options.TimeoutSeconds = k8sapi.MaintenanceTimeoutDefault
		}

		nodeUid := ""
		if action != k8sapi.NodeActionRemove && action != k8sapi.NodeActionCancel {
			client, err := K8sClientFromKubeconfig(LocalKubeconfigPath())
			if err != nil {
				return k8sapi.NodeActionResponse{}, fmt.Errorf("could not connect to Kubernetes: %w", err)
			}

			uid, err := client.NodeUid(ctx, node)
			if err != nil {
				return k8sapi.NodeActionResponse{}, fmt.Errorf("could not find node %s in Kubernetes: %w", node, err)
			}
			nodeUid = uid
		}

		var err error
		switch action {
		case k8sapi.NodeActionDrain:
			err = maintenance.SubmitCordonDrain(node, nodeUid, options)
		case k8sapi.NodeActionCordon:
			err = maintenance.SubmitCordon(node, nodeUid, options)
		case k8sapi.NodeActionUncordon:
			err = maintenance.SubmitUncordon(node, nodeUid)
		case k8sapi.NodeActionUpgrade:
			err = maintenance.SubmitUpgrade(node, nodeUid, request.TargetRelease, options)
		case k8sapi.NodeActionRemove:
			err = maintenance.SubmitRemove(node, nodeUid, options)
		case k8sapi.NodeActionCancel:
			err = maintenance.CancelActiveOperation(node)
		}
		if err != nil {
			return k8sapi.NodeActionResponse{}, err
		}

		response := k8sapi.NodeActionResponse{Node: node, Action: action}
		if snapshot, snapshotErr := maintenance.Snapshot(); snapshotErr == nil {
			if operation, ok := snapshot[node]; ok {
				response.OperationUid = operation.Uid
			}
		}
		return response, nil
	})

	k8sapi.RollingUpgrade.Handler(session, func(_ context.Context, request k8sapi.UpgradeRequest) (k8sapi.UpgradeResponse, error) {
		app.mu.Lock()
		stack := app.Stack
		app.mu.Unlock()

		if stack == nil || !stack.Ok {
			return k8sapi.UpgradeResponse{}, fmt.Errorf("the cluster stack is not available")
		}

		version := strings.TrimSpace(request.K8sVersion)
		if version == "" {
			return k8sapi.UpgradeResponse{}, fmt.Errorf("a target Kubernetes release is required")
		}

		timeout := request.TimeoutSeconds
		if timeout == 0 {
			timeout = k8sapi.MaintenanceTimeoutDefault
		}
		if timeout < k8sapi.MaintenanceTimeoutMin || timeout > k8sapi.MaintenanceTimeoutMax {
			return k8sapi.UpgradeResponse{}, fmt.Errorf(
				"the timeout must be from %d to %d seconds",
				k8sapi.MaintenanceTimeoutMin,
				k8sapi.MaintenanceTimeoutMax,
			)
		}

		record, ok := app.readClusterRecordWith(session, stack)
		if !ok {
			return k8sapi.UpgradeResponse{}, fmt.Errorf("could not read the cluster record")
		}

		selected := map[string]bool{}
		for _, pool := range request.Pools {
			name := strings.TrimSpace(pool)
			if name == "" {
				continue
			}
			if _, found := dashboardPoolRecord(record, name); !found {
				return k8sapi.UpgradeResponse{}, fmt.Errorf("unknown node pool: %s", name)
			}
			selected[name] = true
		}

		groups := map[string]maintenance.Options{}
		for _, node := range record.Nodes {
			options := maintenance.Options{TimeoutSeconds: timeout}
			if len(selected) == 0 || selected[node.Group] {
				options.Cordon = request.Cordon || request.Drain
				options.Drain = request.Drain
			}
			groups[node.Group] = options
		}
		if len(groups) == 0 {
			return k8sapi.UpgradeResponse{}, fmt.Errorf("the cluster has no nodes")
		}

		startCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()

		err := maintenance.RollingUpgradeStart(startCtx, LocalKubeconfigPath(), version, groups)
		if err != nil {
			return k8sapi.UpgradeResponse{}, err
		}

		response := k8sapi.UpgradeResponse{TargetRelease: version}
		if upgrade, _, found, snapshotErr := maintenance.RollingUpgradeSnapshot(); snapshotErr == nil && found {
			response.Uid = upgrade.Uid
		}
		return response, nil
	})

	k8sapi.BackupNow.Handler(session, func(_ context.Context, _ util.Empty) (k8sapi.BackupResponse, error) {
		uid, err := backupRequestSubmitUid(app)
		if err != nil {
			return k8sapi.BackupResponse{}, err
		}
		return k8sapi.BackupResponse{Uid: uid, Phase: k8sapi.BackupRequestPending}, nil
	})

	k8sapi.AutoMaintenance.Handler(session, func(_ context.Context, request k8sapi.AutoRequest) (k8sapi.AutoResponse, error) {
		action := strings.TrimSpace(request.Action)
		switch action {
		case k8sapi.AutoActionShow:
		case k8sapi.AutoActionSet:
			saveCtx, cancel := app.dashboardContext()
			err := app.autoPolicyApply(saveCtx, request)
			cancel()
			if err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionRun:
			runCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			err := maintenance.AutoOpenWindowNow(runCtx, LocalKubeconfigPath())
			cancel()
			if err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionStop:
			if err := maintenance.AutoStopRun(); err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionResume:
			if err := maintenance.AutoResume(); err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionPause:
			days := request.PauseDays
			if days < 1 {
				days = 1
			}
			if days > 30 {
				return k8sapi.AutoResponse{}, fmt.Errorf("the pause must be from 1 to 30 days")
			}
			until := time.Now().UTC().Add(time.Duration(days) * 24 * time.Hour)
			if err := maintenance.AutoDeferUntil(until); err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionUnpause:
			if err := maintenance.AutoDeferUntil(time.Time{}); err != nil {
				return k8sapi.AutoResponse{}, err
			}

		case k8sapi.AutoActionRetryOs:
			node := strings.TrimSpace(request.Node)
			if node == "" {
				return k8sapi.AutoResponse{}, fmt.Errorf("a node name is required")
			}

			retryCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			err := maintenance.AutoRetryOsUpdate(retryCtx, LocalKubeconfigPath(), node)
			cancel()
			if err != nil {
				return k8sapi.AutoResponse{}, err
			}

			if status, statusErr := app.autoStatusRead(); statusErr == nil &&
				status.StateFound &&
				maintenance.AutoStateBlocked(status.State) {
				if err := maintenance.AutoResume(); err != nil {
					return k8sapi.AutoResponse{}, err
				}
			}

		default:
			return k8sapi.AutoResponse{}, fmt.Errorf("unknown automatic maintenance action %q", action)
		}

		return app.autoResponse()
	})
}

func dashboardUnixSeconds(value time.Time) int64 {
	if value.IsZero() {
		return 0
	}
	return value.Unix()
}
