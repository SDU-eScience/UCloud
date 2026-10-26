package dashboard

import (
	"context"
	"fmt"
	"sort"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

const (
	dashboardScalePollInterval = 5 * time.Second
	dashboardScaleRemoveLimit  = 30 * time.Minute
)

func dashboardPoolRecord(record shared.ClusterRecord, name string) (shared.ClusterPoolRecord, bool) {
	for _, pool := range record.Pools {
		if pool.Name == name {
			return pool, true
		}
	}
	return shared.ClusterPoolRecord{}, false
}

func dashboardRemovePoolNodes(
	ctx context.Context,
	app *stackUiApp,
	stack *ucxsvc.Stack,
	poolName string,
	count int,
) error {
	record, ok := app.readClusterRecordWith(*app.Session(), stack)
	if !ok {
		return fmt.Errorf("could not read the cluster record")
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
		return fmt.Errorf("the pool does not have enough nodes to remove")
	}

	for i := 0; i < count; i++ {
		node := nodes[i]
		ucxsvc.UiSendProgress(app, fmt.Sprintf("Removing node %s (%d/%d)", node.Hostname, i+1, count))

		err := maintenance.SubmitRemove(node.Hostname, "", maintenance.Options{
			TimeoutSeconds: k8sapi.MaintenanceTimeoutDefault,
			Cordon:         true,
			Drain:          true,
		})
		if err != nil {
			return fmt.Errorf("could not remove node %s: %w", node.Hostname, err)
		}

		if err := dashboardWaitRemoveNode(ctx, node.Hostname); err != nil {
			return err
		}

		ucxsvc.UiSendProgress(app, "Removed node "+node.Hostname)
	}

	return nil
}

func dashboardWaitRemoveNode(ctx context.Context, nodeName string) error {
	deadline := time.Now().Add(dashboardScaleRemoveLimit)

	for {
		record, recordFound, recordErr := shared.ClusterStateRecordRead()
		nodeMissing := recordErr == nil && recordFound && !clusterRecordHasNode(record, nodeName)

		snapshot, err := maintenance.Snapshot()
		if err != nil {
			return fmt.Errorf("could not read the maintenance state: %w", err)
		}

		operation, found := snapshot[nodeName]
		operationDone := !found
		if found && !maintenance.PhaseActive(operation.Phase) {
			if operation.Phase != maintenance.PhaseCompleted {
				message := operation.Error
				if message == "" {
					message = "the removal ended in phase " + operation.Phase
				}
				return fmt.Errorf("could not remove node %s: %s", nodeName, message)
			}
			operationDone = true
		}

		if nodeMissing && operationDone {
			return nil
		}

		if !found && recordErr != nil {
			return fmt.Errorf("could not read the cluster record: %w", recordErr)
		}

		if time.Now().After(deadline) {
			return fmt.Errorf("timed out waiting for node %s to be removed", nodeName)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(dashboardScalePollInterval):
		}
	}
}

func clusterRecordHasNode(record shared.ClusterRecord, nodeName string) bool {
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			return true
		}
	}
	return false
}
