package maintenance

import (
	"context"
	"fmt"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

const (
	nodeStateProbeTimeout = 2 * time.Second
	nodeStateResetTimeout = 30 * time.Second
)

type NodeState struct {
	Upgrade shared.NodeAgentStatus
	Os      shared.NodeAgentOsStatus
}

func NodeStateRead(nodeName string) (NodeState, error) {
	nodeRecord, recordErr := nodeStateRecord(nodeName)
	if recordErr != nil {
		return NodeState{}, recordErr
	}

	ctx, cancel := context.WithTimeout(context.Background(), nodeStateProbeTimeout)
	defer cancel()

	upgradeStatus, upgradeErr := shared.NodeAgentClientStatus(ctx, nodeName, nodeRecord.IpAddress)
	if upgradeErr != nil {
		return NodeState{}, fmt.Errorf("could not read the upgrade state of node %s: %s", nodeName, upgradeErr)
	}

	osStatus, osErr := shared.NodeAgentClientOsStatus(ctx, nodeName, nodeRecord.IpAddress)
	if osErr != nil {
		return NodeState{}, fmt.Errorf("could not read the operating-system state of node %s: %s", nodeName, osErr)
	}

	return NodeState{
		Upgrade: upgradeStatus,
		Os:      osStatus,
	}, nil
}

func (state NodeState) NeedsReset() bool {
	return state.Upgrade.NeedsStateRecovery || state.Os.NeedsStateRecovery
}

func (state NodeState) ResetDetail() string {
	details := []string{}
	if state.Upgrade.NeedsStateRecovery && state.Upgrade.Error != "" {
		details = append(details, "Kubernetes upgrade: "+state.Upgrade.Error)
	}
	if state.Os.NeedsStateRecovery && state.Os.Error != "" {
		details = append(details, "Operating system: "+state.Os.Error)
	}

	return strings.Join(details, ". ")
}

func NodeStateReset(nodeName string) error {
	nodeRecord, recordErr := nodeStateRecord(nodeName)
	if recordErr != nil {
		return recordErr
	}

	operations, snapshotErr := Snapshot()
	if snapshotErr != nil {
		return fmt.Errorf("could not read the maintenance state: %s", snapshotErr)
	}
	if operation, ok := operations[nodeName]; ok && PhaseActive(operation.Phase) {
		return fmt.Errorf("a maintenance operation is active on node %s", nodeName)
	}

	ctx, cancel := context.WithTimeout(context.Background(), nodeStateResetTimeout)
	defer cancel()

	resetErr := shared.NodeAgentClientResetState(ctx, nodeName, nodeRecord.IpAddress)
	if resetErr != nil {
		return fmt.Errorf("could not reset the state of node %s: %s", nodeName, resetErr)
	}

	return nil
}

func nodeStateRecord(nodeName string) (shared.ClusterNodeRecord, error) {
	record, found, err := readClusterRecord()
	if err != nil {
		return shared.ClusterNodeRecord{}, fmt.Errorf("could not read the cluster record: %s", err)
	}
	if !found {
		return shared.ClusterNodeRecord{}, fmt.Errorf("the cluster record does not exist")
	}

	nodeRecord, known := upgradeNodeRecord(record, nodeName)
	if !known {
		return shared.ClusterNodeRecord{}, fmt.Errorf("the node %s is not part of this cluster", nodeName)
	}

	return nodeRecord, nil
}
