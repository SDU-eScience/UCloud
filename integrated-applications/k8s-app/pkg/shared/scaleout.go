package shared

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

const maxControlPlaneNodes = 7

const poolNameMaxLen = 30

var poolNameRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)

func ClusterAddPool(app ucx.Application, stack *ucxsvc.Stack, pool ClusterPoolSpec) bool {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return false
	}

	name := strings.TrimSpace(pool.Name)
	if name == "" {
		ucxsvc.UiSendFailure(app, "Please provide a pool name")
		return false
	}
	if len(name) > poolNameMaxLen {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Pool names must be at most %d characters: %s", poolNameMaxLen, name))
		return false
	}
	if !poolNameRe.MatchString(name) {
		ucxsvc.UiSendFailure(app, "Pool names may only contain a-z, 0-9 and dashes: "+name)
		return false
	}
	if name == GroupControlPlane {
		ucxsvc.UiSendFailure(app, "The name is reserved for the control plane: "+name)
		return false
	}
	if pool.Nodes < 1 {
		ucxsvc.UiSendFailure(app, "The pool needs at least one node")
		return false
	}
	if pool.DiskGb < 10 {
		ucxsvc.UiSendFailure(app, "The pool needs at least 10 GB of disk")
		return false
	}
	if pool.Machine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine type for the pool")
		return false
	}

	releaseLock, locked := clusterLockRecord()
	if !locked {
		ucxsvc.UiSendFailure(app, "Could not lock the cluster record, another operation may be running")
		return false
	}
	defer releaseLock()

	record, ok := clusterReadRecordLocal()
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return false
	}

	if record.SchemaRevision != clusterRecordSchemaRevision {
		ucxsvc.UiSendFailure(app, "The cluster record was written by an incompatible version of the application")
		return false
	}

	if record.StackId != stack.InstanceId {
		ucxsvc.UiSendFailure(app, "The cluster record belongs to a different stack")
		return false
	}

	if record.Phase != clusterRecordPhaseCreated {
		ucxsvc.UiSendFailure(app, "The cluster is not ready for new pools (current state: "+record.Phase+")")
		return false
	}

	for _, existing := range record.Pools {
		if existing.Name == name {
			ucxsvc.UiSendFailure(app, "A pool with this name already exists: "+name)
			return false
		}
	}

	if pool.Machine.Provider != record.MachineProvider {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The machine must come from the provider that runs the cluster (%s), but %s was selected",
			record.MachineProvider,
			pool.Machine.Provider,
		))
		return false
	}

	activeNodes, activeOk := clusterActiveNodeCount(stack, record)
	if !activeOk {
		ucxsvc.UiSendFailure(app, "Could not determine the active nodes of the cluster, try again later")
		return false
	}
	if activeNodes+pool.Nodes > ClusterMaxNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster supports at most %d nodes", ClusterMaxNodes))
		return false
	}

	record.Pools = append(record.Pools, ClusterPoolRecord{
		Name:    name,
		Machine: pool.Machine,
		DiskGb:  pool.DiskGb,
	})

	if !clusterWriteRecordLocal(record) {
		ucxsvc.UiSendFailure(app, "Could not update the cluster record")
		return false
	}

	for i := 0; i < pool.Nodes; i++ {
		if _, nodeOk := ClusterAddNodeLocked(app, stack, record, name, pool.Machine, pool.DiskGb); !nodeOk {
			return false
		}
	}

	return true
}

// ClusterAddNodeLocked adds a node to the given group. It expects the caller to hold the cluster
// record lock and to pass a record read while holding it.
func ClusterAddNodeLocked(app ucx.Application, stack *ucxsvc.Stack, record *ClusterRecord, group string, machine accapi.ProductReference, diskGb int) (string, bool) {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return "", false
	}

	trimmedGroup := strings.TrimSpace(group)
	if trimmedGroup == "" {
		ucxsvc.UiSendFailure(app, "Please provide a node group")
		return "", false
	}

	if machine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine product before adding a node")
		return "", false
	}

	if diskGb < 10 {
		ucxsvc.UiSendFailure(app, "The node needs at least 10 GB of disk")
		return "", false
	}

	if record.StackId != stack.InstanceId {
		ucxsvc.UiSendFailure(app, "The cluster record belongs to a different stack")
		return "", false
	}

	if record.SchemaRevision != clusterRecordSchemaRevision {
		ucxsvc.UiSendFailure(app, "The cluster record was written by an incompatible version of the application")
		return "", false
	}

	if record.Phase != clusterRecordPhaseCreated {
		ucxsvc.UiSendFailure(app, "The cluster is not ready for new nodes (current state: "+record.Phase+")")
		return "", false
	}

	knownGroup := false
	for _, pool := range record.Pools {
		if pool.Name == trimmedGroup {
			knownGroup = true
			break
		}
	}
	if !knownGroup {
		ucxsvc.UiSendFailure(app, "Unknown node group: "+trimmedGroup)
		return "", false
	}

	if machine.Provider != record.MachineProvider {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The machine must come from the provider that runs the cluster (%s), but %s was selected",
			record.MachineProvider,
			machine.Provider,
		))
		return "", false
	}

	if trimmedGroup == GroupControlPlane {
		controlPlane := 0
		for _, node := range record.Nodes {
			if node.Group == GroupControlPlane {
				controlPlane++
			}
		}
		if controlPlane >= maxControlPlaneNodes {
			ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster control plane supports at most %d nodes", maxControlPlaneNodes))
			return "", false
		}
	}

	if !AllocationIdIsValid(record.NextAllocationId) {
		ucxsvc.UiSendFailure(app, "The cluster has no free addresses left in its subnet")
		return "", false
	}

	release, ok := ReleaseByExactVersion(record.K8sVersion)
	if !ok {
		ucxsvc.UiSendFailure(app, "The cluster runs an unknown Kubernetes version: "+record.K8sVersion)
		return "", false
	}

	tokens, ok := clusterReadManagementTokens(trimmedGroup)
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the join tokens of the cluster")
		return "", false
	}

	allocationId := record.NextAllocationId
	hostname := ClusterNodeHostname(trimmedGroup, allocationId)
	ipAddress := NodeIpForAllocation(allocationId)

	opts := clusterNodeOptions{
		record:       record,
		release:      release,
		tokens:       ClusterTokens{},
		group:        trimmedGroup,
		machine:      machine,
		diskGb:       diskGb,
		allocationId: allocationId,
	}

	if !clusterWriteNodeInput(stack, record, release, opts.tokens, opts) {
		ucxsvc.UiSendFailure(app, "Could not write the input files for the new node")
		return "", false
	}

	inputDir := inputDirFor(allocationId)
	if trimmedGroup == GroupControlPlane {
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "server-token.ca"), tokens.ServerToken, 0600)
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "agent-token.ca"), tokens.AgentToken, 0600)
	} else {
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "agent-token.ca"), tokens.AgentToken, 0600)
	}
	if !stack.Ok {
		ucxsvc.UiSendFailure(app, "Could not write the input files for the new node")
		return "", false
	}

	record.NextAllocationId = allocationId + 1
	if !clusterWriteRecordLocal(record) {
		ucxsvc.UiSendFailure(app, "Could not update the cluster record")
		return "", false
	}

	reservation, err := ucxsvc.PrivateNetworkIpReserveRetry(stack, record.NetworkId, ipAddress, 30*time.Second)
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not reserve an address for the new node: %s", err))
		if reservation.Id != "" {
			clusterCleanupNewNode(stack, record, &ClusterNodeRecord{
				AllocationId:  allocationId,
				Group:         trimmedGroup,
				Hostname:      hostname,
				IpAddress:     ipAddress,
				ReservationId: reservation.Id,
			}, app)
		}
		return "", false
	}

	node := ClusterNodeRecord{
		AllocationId:   allocationId,
		Group:          trimmedGroup,
		Hostname:       hostname,
		JobId:          "",
		IpAddress:      ipAddress,
		ReservationId:  reservation.Id,
		Machine:        machine,
		DiskGb:         diskGb,
		DesiredVersion: release.Release,
	}
	record.Nodes = append(record.Nodes, node)

	if !clusterWriteRecordLocal(record) {
		clusterCleanupNewNode(stack, record, &node, app)
		return "", false
	}

	attachments := []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(record.NetworkId, ipAddress),
		ucxsvc.StackSubtreeMount(stack, record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(allocationId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}

	labels := map[string]string{
		StackGroupingLabel:             trimmedGroup,
		NodeAllocationLabel:            fmt.Sprintf("%d", allocationId),
		K8sVersionLabel:                release.Release,
		orcapi.ResourceLabelInitScript: launcherPath,
	}

	job, err := clusterCreateNodeJob(stack, orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: machine,
			Labels:  labels,
		},
		Application: ucxsvc.VmImageUbuntu26_04,
		Name:        hostname,
		Hostname:    util.OptValue[string](hostname),
		Parameters: map[string]orcapi.AppParameterValue{
			"diskSize": orcapi.AppParameterValueInteger(int64(diskGb)),
		},
		Replicas:  1,
		Resources: attachments,
	})
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not create the node: %s", err))
		clusterCleanupNewNode(stack, record, &node, app)
		return "", false
	}

	node.JobId = job.Id
	for i := range record.Nodes {
		if record.Nodes[i].AllocationId == allocationId {
			record.Nodes[i].JobId = job.Id
			break
		}
	}

	if !clusterWriteRecordLocal(record) {
		node.JobId = job.Id
		clusterCleanupNewNode(stack, record, &node, app)
		return "", false
	}

	return job.Id, true
}

func ClusterAddNode(app ucx.Application, stack *ucxsvc.Stack, group string, machine accapi.ProductReference, diskGb int, existingJobs []orcapi.Job) (string, bool) {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return "", false
	}

	releaseLock, locked := clusterLockRecord()
	if !locked {
		ucxsvc.UiSendFailure(app, "Could not lock the cluster record, another operation may be running")
		return "", false
	}
	defer releaseLock()

	record, ok := clusterReadRecordLocal()
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return "", false
	}

	return ClusterAddNodeLocked(app, stack, record, group, machine, diskGb)
}

func clusterCleanupNewNode(stack *ucxsvc.Stack, record *ClusterRecord, node *ClusterNodeRecord, app ucx.Application) {
	failures := []string{}

	if node.JobId != "" {
		if err := ucxsvc.JobTerminate(stack, node.JobId); err != nil {
			failures = append(failures, "job "+node.JobId)
		}
	}

	if err := ucxsvc.PrivateNetworkIpDelete(stack, node.ReservationId); err != nil {
		failures = append(failures, "reservation "+node.ReservationId)
	}

	for i := range record.Nodes {
		if record.Nodes[i].AllocationId == node.AllocationId {
			record.Nodes = append(record.Nodes[:i], record.Nodes[i+1:]...)
			break
		}
	}

	if len(failures) == 0 {
		record.Phase = clusterRecordPhaseCreated
		if !clusterWriteRecordLocal(record) {
			ucxsvc.UiSendFailure(app, "Could not update the cluster record after cleanup")
		}
		return
	}

	record.Phase = clusterRecordPhaseCleanup
	record.PendingCleanup = append(record.PendingCleanup, ClusterPendingCleanup{
		ReservationId: node.ReservationId,
		JobId:         node.JobId,
		Hostname:      node.Hostname,
		IpAddress:     node.IpAddress,
	})

	if clusterWriteRecordLocal(record) {
		ucxsvc.UiSendFailure(app, "The node could not be created and these resources need manual cleanup: "+strings.Join(failures, ", "))
	} else {
		ucxsvc.UiSendFailure(app, "The node could not be created and cleanup failed, these resources need manual cleanup: "+strings.Join(failures, ", "))
	}
}

func clusterRecordLocalPath() string {
	return filepath.Join(managementMountPath, filepath.Base(ClusterRecordPath))
}

func clusterReadRecordLocal() (*ClusterRecord, bool) {
	data, err := os.ReadFile(clusterRecordLocalPath())
	if err != nil {
		return nil, false
	}

	var record ClusterRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, false
	}

	if record.StackId == "" || record.NetworkId == "" || record.BundlePath == "" || record.NextAllocationId < 1 {
		return nil, false
	}

	return &record, true
}

func clusterWriteRecordLocal(record *ClusterRecord) bool {
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return false
	}

	path := clusterRecordLocalPath()
	tmpPath := path + ".tmp"

	if err := os.WriteFile(tmpPath, data, 0660); err != nil {
		return false
	}

	if err := os.Rename(tmpPath, path); err != nil {
		_ = os.Remove(tmpPath)
		return false
	}

	return true
}

func clusterLockRecord() (func(), bool) {
	lockPath := clusterRecordLocalPath() + ".lock"
	file, err := os.OpenFile(lockPath, os.O_CREATE|os.O_RDWR, 0660)
	if err != nil {
		return nil, false
	}

	if err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		_ = file.Close()
		return nil, false
	}

	return func() {
		_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN)
		_ = file.Close()
	}, true
}

func clusterReadManagementTokens(group string) (ClusterTokens, bool) {
	if group == GroupControlPlane {
		serverToken, ok := readFirstLine(filepath.Join(managementMountPath, "tokens", "server"))
		if !ok {
			return ClusterTokens{}, false
		}

		agentToken, ok := readFirstLine(filepath.Join(managementMountPath, "tokens", "agent"))
		if !ok {
			return ClusterTokens{}, false
		}

		return ClusterTokens{ServerToken: serverToken, AgentToken: agentToken}, true
	}

	agentToken, ok := readFirstLine(filepath.Join(managementMountPath, "tokens", "agent"))
	if !ok {
		return ClusterTokens{}, false
	}
	return ClusterTokens{AgentToken: agentToken}, true
}

func readFirstLine(path string) (string, bool) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", false
	}

	line := strings.TrimSpace(string(data))
	if line == "" {
		return "", false
	}
	return line, true
}
