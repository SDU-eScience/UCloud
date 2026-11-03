package shared

import (
	"fmt"
	"path/filepath"
	"strings"

	"k8s.io/apimachinery/pkg/util/version"

	accapi "ucloud.dk/shared/pkg/accounting"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func clusterMinimumControlPlaneVersion(record *ClusterRecord) (string, error) {
	result := ""
	var resultParsed *version.Version

	for i := range record.Nodes {
		node := &record.Nodes[i]
		if node.Group != k8sapi.GroupControlPlane || node.DesiredVersion == "" {
			continue
		}

		parsed, err := version.ParseSemantic(node.DesiredVersion)
		if err != nil {
			return "", fmt.Errorf("the desired Kubernetes version %s of node %s could not be parsed", node.DesiredVersion, node.Hostname)
		}

		if resultParsed == nil || parsed.LessThan(resultParsed) {
			result = node.DesiredVersion
			resultParsed = parsed
		}
	}

	if result != "" {
		return result, nil
	}

	if _, parseErr := version.ParseSemantic(record.K8sVersion); parseErr != nil {
		return "", fmt.Errorf("the cluster Kubernetes version %s could not be parsed", record.K8sVersion)
	}

	return record.K8sVersion, nil
}

func clusterValidateRecordMutation(
	app ucx.Application,
	stack *ucxsvc.Stack,
	client ClusterStateClient,
	record *ClusterRecord,
	machine accapi.ProductReference,
	readyMessage string,
) bool {
	if record.StackId != stack.InstanceId {
		ucxsvc.UiSendFailure(app, "The cluster record belongs to a different stack")
		return false
	}

	if record.Phase != k8sapi.ClusterPhaseCreated {
		ucxsvc.UiSendFailure(app, "The cluster is not ready for "+readyMessage+" (current state: "+record.Phase+")")
		return false
	}

	if len(record.PendingCleanup) > 0 {
		ucxsvc.UiSendFailure(app, "The cluster is removing the resources of a failed scale-out. Try again when the cleanup finishes.")
		return false
	}

	recoveryActive, recoveryErr := ClusterRecoveryActive(client)
	if recoveryErr != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not read the recovery state: %s", recoveryErr))
		return false
	}
	if recoveryActive {
		ucxsvc.UiSendFailure(app, "A recovery operation is in progress for this cluster")
		return false
	}

	if machine.Provider != record.MachineProvider {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The machine type must come from the provider that runs the cluster (%s), but %s was selected",
			record.MachineProvider,
			machine.Provider,
		))
		return false
	}

	return true
}

func clusterValidateNodeAddition(
	app ucx.Application,
	stack *ucxsvc.Stack,
	client ClusterStateClient,
	record *ClusterRecord,
	machine accapi.ProductReference,
	diskGb int,
	nodeCount int,
	machineFailure string,
	diskTarget string,
	readyMessage string,
) bool {
	if machine.Id == "" {
		ucxsvc.UiSendFailure(app, machineFailure)
		return false
	}

	if diskGb < k8sapi.MinDiskGb {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("%s needs at least %d GB of disk", diskTarget, k8sapi.MinDiskGb))
		return false
	}
	if diskGb > k8sapi.MaxDiskGb {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("%s supports at most %d GB of disk", diskTarget, k8sapi.MaxDiskGb))
		return false
	}

	if !clusterValidateRecordMutation(app, stack, client, record, machine, readyMessage) {
		return false
	}

	if !clusterNodesRequireUcxLabels(app) {
		return false
	}

	activeNodes, activeOk := clusterActiveNodeCount(stack, record)
	if !activeOk {
		ucxsvc.UiSendFailure(app, "Could not determine the active nodes of the cluster, try again later")
		return false
	}
	if activeNodes+nodeCount > k8sapi.ClusterMaxNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster supports at most %d nodes", k8sapi.ClusterMaxNodes))
		return false
	}

	for i := 0; i < nodeCount; i++ {
		internalId := record.NextInternalId + i
		if !InternalIdIsValid(internalId) {
			ucxsvc.UiSendFailure(app, fmt.Sprintf(
				"The cluster has no free addresses left in its subnet for %d nodes (next internal id: %d)",
				nodeCount,
				record.NextInternalId,
			))
			return false
		}
	}

	return true
}

func ClusterAddPool(app ucx.Application, stack *ucxsvc.Stack, pool ClusterPoolSpec) bool {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return false
	}

	name := strings.TrimSpace(pool.Name)

	client, err := ClusterStateClientNewHost()
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not open the cluster state: %s", err))
		return false
	}

	snapshot, err := ClusterStateRead(client)
	if err != nil {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record: "+err.Error())
		return false
	}
	if !snapshot.Found {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return false
	}
	record := &snapshot.Record

	if err := k8sapi.ValidatePoolSpec(k8sapi.PoolSpec{
		Name:    name,
		Machine: pool.Machine,
		Nodes:   pool.Nodes,
		Disk:    pool.DiskGb,
	}, record.MachineProvider); err != nil {
		ucxsvc.UiSendFailure(app, err.Error())
		return false
	}

	if !clusterValidateNodeAddition(
		app,
		stack,
		client,
		record,
		pool.Machine,
		pool.DiskGb,
		pool.Nodes,
		"Select a machine type for the worker pool",
		"The worker pool",
		"new worker pools",
	) {
		return false
	}

	for _, existing := range record.Pools {
		if existing.Name == name {
			ucxsvc.UiSendFailure(app, "A worker pool with this name already exists: "+name)
			return false
		}
	}

	record.Pools = append(record.Pools, ClusterPoolRecord{
		Name:    name,
		Machine: pool.Machine,
		DiskGb:  pool.DiskGb,
	})

	if err := ClusterRecordReplace(client, record); err != nil {
		ucxsvc.UiSendFailure(app, "Could not update the cluster record: "+err.Error())
		return false
	}

	ucxsvc.UiSendProgress(app, "Creating worker pool "+name)

	for i := 0; i < pool.Nodes; i++ {
		ucxsvc.UiSendProgress(app, fmt.Sprintf("Creating worker pool %s node %d/%d", name, i+1, pool.Nodes))
		if !ClusterAddNodeLocked(app, stack, client, record, name, pool.Machine, pool.DiskGb) {
			return false
		}
	}

	return true
}

// ClusterAddNodeLocked adds a node to the given group. It expects the caller
// to pass a record read through the same client.
func ClusterAddNodeLocked(
	app ucx.Application,
	stack *ucxsvc.Stack,
	client ClusterStateClient,
	record *ClusterRecord,
	group string,
	machine accapi.ProductReference,
	diskGb int,
) bool {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return false
	}

	clusterVersion, versionErr := clusterMinimumControlPlaneVersion(record)
	if versionErr != nil {
		ucxsvc.UiSendFailure(app, versionErr.Error())
		return false
	}

	release, ok := ReleaseByExactVersion(clusterVersion)
	if !ok {
		ucxsvc.UiSendFailure(app, "The cluster runs an unknown Kubernetes version: "+clusterVersion)
		return false
	}

	tokens, ok := clusterReadManagementTokens(group)
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the join tokens of the cluster")
		return false
	}

	bundleTarget := BundlePathForRelease(release)
	if clusterVersion != record.K8sVersion || record.BundlePath != bundleTarget {
		writeBundle(stack, bundleTarget)
		if !stack.Ok {
			ucxsvc.UiSendFailure(app, "Could not prepare the setup files for Kubernetes version "+release.Release)
			return false
		}

		record.K8sVersion = clusterVersion
		record.BundlePath = bundleTarget
		if err := ClusterRecordReplace(client, record); err != nil {
			ucxsvc.UiSendFailure(app, "Could not update the cluster record: "+err.Error())
			return false
		}
	}

	internalId := record.NextInternalId

	customUi := ucxsvc.UcxCustomUiServiceInit{}
	if group == k8sapi.GroupControlPlane {
		customUi = ClusterNodeCustomUi(stack)
		if !stack.Ok {
			ucxsvc.UiSendFailure(app, "Could not prepare the custom UI service for the new control-plane node")
			return false
		}
	}

	opts := clusterNodeOptions{
		record:      record,
		release:     release,
		tokens:      tokens,
		writeTokens: true,
		group:       group,
		machine:     machine,
		diskGb:      diskGb,
		internalId:  internalId,
		customUi:    customUi,
		session:     *app.Session(),
		stateClient: client,
	}

	opts.onFailure = func(stage clusterNodeProvisionStage, node ClusterNodeRecord, jobId string, cause string) string {
		if stage == clusterNodeStageIdentity {
			record.Phase = k8sapi.ClusterPhaseError
			record.FailureReason = "could not write the stack identity for job " + jobId
			if err := ClusterRecordReplace(client, record); err != nil {
				ucxsvc.UiSendFailure(app, "Could not write the stack identity of the new node. The cluster record is out of date and requires an explicit recovery: "+err.Error())
				return ""
			}
			ucxsvc.UiSendFailure(app, "Could not write the stack identity of the new node. The node was retained to avoid a partially provisioned input. Reprovision the node to recover.")
			return ""
		}

		if stage == clusterNodeStageCredential {
			record.Phase = k8sapi.ClusterPhaseError
			record.FailureReason = "could not issue or deliver the controller credential for job " + jobId
			if err := ClusterRecordReplace(client, record); err != nil {
				ucxsvc.UiSendFailure(app, "Could not issue or deliver the controller credential. The cluster record is out of date and requires an explicit recovery: "+err.Error())
				return ""
			}
			ucxsvc.UiSendFailure(app, "Could not issue or deliver the controller credential. The node was retained to avoid invalidating a possibly issued credential. Reprovision the cluster to recover.")
			return ""
		}

		ucxsvc.UiSendFailure(app, cause)

		if stage == clusterNodeStagePrepare || stage == clusterNodeStageInternalId {
			return ""
		}
		if stage == clusterNodeStageReserve && node.PrivateIpIdentifier == "" {
			return ""
		}

		clusterCleanupNewNode(client, &node, app)
		return ""
	}

	provisionOk, _ := clusterProvisionNode(stack, opts)
	return provisionOk
}

func ClusterAddNodesBatch(
	app ucx.Application,
	stack *ucxsvc.Stack,
	group string,
	machine accapi.ProductReference,
	diskGb int,
	count int,
) bool {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return false
	}

	if count < 1 {
		ucxsvc.UiSendFailure(app, "The count must be at least one")
		return false
	}

	client, err := ClusterStateClientNewHost()
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not open the cluster state: %s", err))
		return false
	}

	snapshot, err := ClusterStateRead(client)
	if err != nil {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record: "+err.Error())
		return false
	}
	if !snapshot.Found {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return false
	}
	record := &snapshot.Record

	if group == "" {
		ucxsvc.UiSendFailure(app, "Please provide a node group")
		return false
	}

	knownGroup := group == k8sapi.GroupControlPlane
	for _, pool := range record.Pools {
		if pool.Name == group {
			knownGroup = true
			break
		}
	}
	if !knownGroup {
		ucxsvc.UiSendFailure(app, "Unknown node group: "+group)
		return false
	}

	currentNodes := 0
	for _, node := range record.Nodes {
		if node.Group == group {
			currentNodes++
		}
	}

	if group == k8sapi.GroupControlPlane {
		targetNodes := currentNodes + count
		if targetNodes > k8sapi.ControlPlaneMaxNodes {
			ucxsvc.UiSendFailure(app, fmt.Sprintf(
				"The cluster control plane supports at most %d nodes",
				k8sapi.ControlPlaneMaxNodes,
			))
			return false
		}
		if targetNodes%2 == 0 {
			ucxsvc.UiSendFailure(app, "The control plane must have an odd number of nodes")
			return false
		}
	} else if currentNodes+count > k8sapi.MaxPoolNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The worker pool supports at most %d nodes", k8sapi.MaxPoolNodes))
		return false
	}

	if !clusterValidateNodeAddition(
		app,
		stack,
		client,
		record,
		machine,
		diskGb,
		count,
		"Select a machine type before adding a node",
		"The node",
		"new nodes",
	) {
		return false
	}

	for i := range count {
		if group == k8sapi.GroupControlPlane {
			ucxsvc.UiSendProgress(app, fmt.Sprintf("Adding control-plane node %d/%d", i+1, count))
		} else {
			ucxsvc.UiSendProgress(app, fmt.Sprintf("Adding node %d/%d to worker pool %s", i+1, count, group))
		}

		if !ClusterAddNodeLocked(app, stack, client, record, group, machine, diskGb) {
			return false
		}
	}
	return true
}

func clusterCleanupNewNode(client ClusterStateClient, node *ClusterNodeRecord, app ucx.Application) {
	entry := ClusterPendingCleanup{
		PrivateIpIdentifier: node.PrivateIpIdentifier,
		JobId:               node.JobId,
		Hostname:            node.Hostname,
		IpAddress:           node.IpAddress,
	}

	err := ClusterRecordUpdate(client, func(record *ClusterRecord) bool {
		changed := false
		found := false
		for _, pending := range record.PendingCleanup {
			if clusterPendingCleanupSame(pending, entry) {
				found = true
				break
			}
		}
		if !found {
			record.PendingCleanup = append(record.PendingCleanup, entry)
			changed = true
		}
		if record.Phase == k8sapi.ClusterPhaseCreated {
			record.Phase = k8sapi.ClusterPhaseCleanup
			changed = true
		}
		if record.Phase != k8sapi.ClusterPhaseRecovering && node.Hostname != "" {
			nodes := clusterRemoveNodeForPending(record.Nodes, entry)
			if len(nodes) != len(record.Nodes) {
				record.Nodes = nodes
				changed = true
			}
		}
		return changed
	})
	if err != nil {
		ucxsvc.UiSendFailure(app, "The node could not be created and the cleanup state could not be recorded. "+
			"The resources of "+node.Hostname+" may need manual cleanup: "+err.Error())
		return
	}

	_ = ClusterCleanupSweep()

	ucxsvc.UiSendFailure(app, "The node could not be created. The application is removing the leftover resources of "+
		node.Hostname+". New nodes cannot be added until the cleanup finishes.")
}

func clusterReadManagementTokens(group string) (ClusterTokens, bool) {
	if group == k8sapi.GroupControlPlane {
		serverToken, ok := util.ReadFileTrimmed(filepath.Join(ManagementMountPath, "tokens", "server"))
		if !ok {
			return ClusterTokens{}, false
		}

		agentToken, ok := util.ReadFileTrimmed(filepath.Join(ManagementMountPath, "tokens", "agent"))
		if !ok {
			return ClusterTokens{}, false
		}

		return ClusterTokens{ServerToken: serverToken, AgentToken: agentToken}, true
	}

	agentToken, ok := util.ReadFileTrimmed(filepath.Join(ManagementMountPath, "tokens", "agent"))
	if !ok {
		return ClusterTokens{}, false
	}
	return ClusterTokens{AgentToken: agentToken}, true
}
