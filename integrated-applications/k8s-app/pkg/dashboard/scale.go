package dashboard

import (
	"fmt"
	"sort"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

func dashboardSubmitPoolRemoval(
	app *stackUiApp,
	stack *ucxsvc.Stack,
	poolName string,
	count int,
) (maintenance.MaintenanceRun, error) {
	record, ok := app.readClusterRecordWith(*app.Session(), stack)
	if !ok {
		return maintenance.MaintenanceRun{}, fmt.Errorf("could not read the cluster record")
	}

	nodes := make([]shared.ClusterNodeRecord, 0, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.Group == poolName {
			nodes = append(nodes, node)
		}
	}

	sort.Slice(nodes, func(i, j int) bool {
		return nodes[i].InternalId > nodes[j].InternalId
	})

	if len(nodes) < count {
		return maintenance.MaintenanceRun{}, fmt.Errorf("the node group does not have enough nodes to remove")
	}

	targetNodes := make([]string, 0, count)
	for i := 0; i < count; i++ {
		targetNodes = append(targetNodes, nodes[i].Hostname)
	}

	return maintenance.SubmitRun(maintenance.RunRequest{
		Tasks: []maintenance.TaskRequest{{
			Kind:           k8sapi.MaintenanceTaskRemove,
			TimeoutSeconds: k8sapi.MaintenanceTimeoutDefault,
		}},
		Target:              maintenance.Target{Nodes: targetNodes},
		MaxParallelPerGroup: 1,
	}, "dashboard")
}
