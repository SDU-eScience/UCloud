package dashboard

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
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
}
