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
}
