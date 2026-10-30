package dashboard

import (
	"fmt"
	"sort"

	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

func dashboardPoolRecord(record shared.ClusterRecord, name string) (shared.ClusterPoolRecord, bool) {
	for _, pool := range record.Pools {
		if pool.Name == name {
			return pool, true
		}
	}
	return shared.ClusterPoolRecord{}, false
}

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
		return nodes[i].AllocationId > nodes[j].AllocationId
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
			Kind:           maintenance.TaskRemove,
			TimeoutSeconds: maintenanceDefaultTimeoutSeconds,
		}},
		Target:              maintenance.Target{Nodes: targetNodes},
		MaxParallelPerGroup: 1,
	}, "dashboard")
}
