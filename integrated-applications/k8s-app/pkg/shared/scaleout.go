package shared

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/util/version"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

const maxControlPlaneNodes = 7

const poolNameMaxLen = 30

var poolNameRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)

func clusterMinimumControlPlaneVersion(record *ClusterRecord) (string, error) {
	result := ""
	var resultParsed *version.Version

	for i := range record.Nodes {
		node := &record.Nodes[i]
		if node.Group != GroupControlPlane || node.DesiredVersion == "" {
			continue
		}

		parsed, err := version.ParseSemantic(node.DesiredVersion)
		if err != nil {
			return "", fmt.Errorf("the desired version %s of node %s could not be parsed", node.DesiredVersion, node.Hostname)
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
		return "", fmt.Errorf("the cluster version %s could not be parsed", record.K8sVersion)
	}

	return record.K8sVersion, nil
}

func clusterValidateRecordMutation(
	app ucx.Application,
	stack *ucxsvc.Stack,
	record *ClusterRecord,
	machine accapi.ProductReference,
	readyMessage string,
) bool {
	if record.StackId != stack.InstanceId {
		ucxsvc.UiSendFailure(app, "The cluster record belongs to a different stack")
		return false
	}

	if record.SchemaRevision != clusterRecordSchemaRevision {
		ucxsvc.UiSendFailure(app, "The cluster record was written by an incompatible version of the application")
		return false
	}

	if record.Phase != clusterRecordPhaseCreated {
		ucxsvc.UiSendFailure(app, "The cluster is not ready for "+readyMessage+" (current state: "+record.Phase+")")
		return false
	}

	if machine.Provider != record.MachineProvider {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The machine must come from the provider that runs the cluster (%s), but %s was selected",
			record.MachineProvider,
			machine.Provider,
		))
		return false
	}

	return true
}

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

	flow, releaseLock, locked := clusterOpenRecordFlow(app, "add-pool", "")
	if !locked {
		return false
	}
	defer releaseLock()

	record, ok := flow.Record()
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return false
	}

	if !clusterValidateRecordMutation(app, stack, record, pool.Machine, "new pools") {
		return false
	}
	for _, existing := range record.Pools {
		if existing.Name == name {
			ucxsvc.UiSendFailure(app, "A pool with this name already exists: "+name)
			return false
		}
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

	poolOperationUid := ClusterTopologyOperationUid()

	expectedHostnames := []string{}
	for i := 0; i < pool.Nodes; i++ {
		allocationId := record.NextAllocationId + i
		if !AllocationIdIsValid(allocationId) {
			ucxsvc.UiSendFailure(app, fmt.Sprintf(
				"The cluster has no free addresses left in its subnet for %d nodes (next allocation: %d)",
				pool.Nodes,
				record.NextAllocationId,
			))
			return false
		}
		expectedHostnames = append(expectedHostnames, ClusterNodeHostname(name, allocationId))
	}

	record.Pools = append(record.Pools, ClusterPoolRecord{
		Name:    name,
		Machine: pool.Machine,
		DiskGb:  pool.DiskGb,
	})

	if !flow.Commit(record) {
		ucxsvc.UiSendFailure(app, "Could not update the cluster record")
		return false
	}

	for i := 0; i < pool.Nodes; i++ {
		if _, nodeOk := ClusterAddNodeLocked(app, stack, flow, record, name, pool.Machine, pool.DiskGb, poolOperationUid); !nodeOk {
			return false
		}
	}

	return true
}

// ClusterAddNodeLocked adds a node to the given group. It expects the caller
// to hold the cluster record flow and to pass a record read through it.
func ClusterAddNodeLocked(
	app ucx.Application,
	stack *ucxsvc.Stack,
	flow *clusterRecordFlow,
	record *ClusterRecord,
	group string,
	machine accapi.ProductReference,
	diskGb int,
	operationUid string,
) (string, bool) {
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

	if !clusterValidateRecordMutation(app, stack, record, machine, "new nodes") {
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

	if !clusterNodesRequireUcxLabels(app) {
		return "", false
	}

	if !AllocationIdIsValid(record.NextAllocationId) {
		ucxsvc.UiSendFailure(app, "The cluster has no free addresses left in its subnet")
		return "", false
	}

	clusterVersion, versionErr := clusterMinimumControlPlaneVersion(record)
	if versionErr != nil {
		ucxsvc.UiSendFailure(app, versionErr.Error())
		return "", false
	}

	release, ok := ReleaseByExactVersion(clusterVersion)
	if !ok {
		ucxsvc.UiSendFailure(app, "The cluster runs an unknown Kubernetes version: "+clusterVersion)
		return "", false
	}

	bundleTarget := BundlePathForRelease(release)
	if clusterVersion != record.K8sVersion || record.BundlePath != bundleTarget {
		writeBundle(stack, bundleTarget)
		if !stack.Ok {
			ucxsvc.UiSendFailure(app, "Could not write the bootstrap bundle for release "+release.Release)
			return "", false
		}

		record.K8sVersion = clusterVersion
		record.BundlePath = bundleTarget
		if !flow.Commit(record) {
			ucxsvc.UiSendFailure(app, "Could not update the cluster record")
			return "", false
		}
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
		session:      *app.Session(),
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
	if !flow.Commit(record) {
		ucxsvc.UiSendFailure(app, "Could not update the cluster record")
		return "", false
	}

	reservation, err := ucxsvc.PrivateNetworkIpReserveRetry(stack, record.NetworkId, ipAddress, 30*time.Second)
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not reserve an address for the new node: %s", err))
		if reservation.Id != "" {
			clusterCleanupNewNode(stack, flow, record, &ClusterNodeRecord{
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

	if !flow.Commit(record) {
		clusterCleanupNewNode(stack, flow, record, &node, app)
		return "", false
	}

	attachments := []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(record.NetworkId, ipAddress),
		ucxsvc.StackSubtreeMount(stack, record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(allocationId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}

	labels := map[string]string{
		StackGroupingLabel:  trimmedGroup,
		NodeAllocationLabel: fmt.Sprintf("%d", allocationId),
		K8sVersionLabel:     release.Release,
		TopologyOperationLabel: operationUid,
	}

	if trimmedGroup == GroupControlPlane {
		customUi := ucxsvc.UcxInitCustomUiServiceAt(stack, customUiPort, "", managementDir, managementMountPath)
		if !stack.Ok {
			ucxsvc.UiSendFailure(app, "Could not prepare the custom UI service for the new control plane node")
			clusterCleanupNewNode(stack, flow, record, &node, app)
			return "", false
		}
		attachments, labels = clusterControlPlaneWiring(stack, trimmedGroup, allocationId, customUi, attachments, labels)
	} else {
		labels = util.MapMerge(labels, ucxsvc.UcxPortLabel(0))
		labels[orcapi.ResourceLabelInitScript] = launcherPath
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
		clusterCleanupNewNode(stack, flow, record, &node, app)
		return "", false
	}

	node.JobId = job.Id
	for i := range record.Nodes {
		if record.Nodes[i].AllocationId == allocationId {
			record.Nodes[i].JobId = job.Id
			break
		}
	}

	if !clusterWriteStackIdentity(stack, allocationId, job) {
		record.Phase = clusterRecordPhaseError
		record.FailureReason = "could not write the stack identity for job " + job.Id
		if !flow.Commit(record) {
			ucxsvc.UiSendFailure(app, "Could not write the stack identity of the new node. The cluster record is out of date and requires an explicit recovery")
			return "", false
		}
		ucxsvc.UiSendFailure(app, "Could not write the stack identity of the new node. The node was retained to avoid a partially provisioned input. Reprovision the node to recover.")
		return "", false
	}

	if !flow.Commit(record) {
		ucxsvc.UiSendFailure(app, "Could not record the new node job id")
		clusterCleanupNewNode(stack, flow, record, &node, app)
		return "", false
	}

	if trimmedGroup == GroupControlPlane {
		if !clusterWriteControllerToken(stack, opts.session, job.Id, allocationId) {
			record.Phase = clusterRecordPhaseError
			record.FailureReason = "could not issue or deliver the controller credential for job " + job.Id
			if !flow.Commit(record) {
				ucxsvc.UiSendFailure(app, "Could not issue or deliver the controller credential. The cluster record is out of date and requires an explicit recovery")
				return "", false
			}
			ucxsvc.UiSendFailure(app, "Could not issue or deliver the controller credential. The node was retained to avoid invalidating a possibly issued credential. Reprovision the cluster to recover.")
			return "", false
		}
	}

	if trimmedGroup == GroupControlPlane && record.ServiceId != "" {
		if !ucxsvc.ServiceAddMembers(stack, record.ServiceId, []string{job.Id}) {
			ucxsvc.UiSendFailure(app, "Could not add the new node to the cluster service")
			clusterCleanupNewNode(stack, flow, record, &node, app)
			return "", false
		}
	}

	if !flow.Commit(record) {
		clusterCleanupNewNode(stack, flow, record, &node, app)
		return "", false
	}

	return job.Id, true
}

func ClusterAddNode(app ucx.Application, stack *ucxsvc.Stack, group string, machine accapi.ProductReference, diskGb int, existingJobs []orcapi.Job) (string, bool) {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return "", false
	}

	flow, releaseLock, locked := clusterOpenRecordFlow(app, "add-node", "")
	if !locked {
		return "", false
	}
	defer releaseLock()

	record, ok := flow.Record()
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return "", false
	}

	jobId, nodeOk := ClusterAddNodeLocked(app, stack, flow, record, group, machine, diskGb, "")
	if !nodeOk {
		return "", false
	}

	return jobId, true
}

func ClusterAddNodesBatch(
	app ucx.Application,
	stack *ucxsvc.Stack,
	group string,
	machine accapi.ProductReference,
	diskGb int,
	count int,
) (string, bool) {
	if stack == nil || !stack.Ok {
		ucxsvc.UiSendFailure(app, "The cluster stack is not available")
		return "", false
	}

	if count < 1 {
		ucxsvc.UiSendFailure(app, "The count must be at least one")
		return "", false
	}

	flow, releaseLock, locked := clusterOpenRecordFlow(app, "add-nodes-batch", "")
	if !locked {
		return "", false
	}
	defer releaseLock()

	record, ok := flow.Record()
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not read the cluster record")
		return "", false
	}

	trimmedGroup := strings.TrimSpace(group)
	if trimmedGroup == "" {
		ucxsvc.UiSendFailure(app, "Please provide a node group")
		return "", false
	}

	knownGroup := trimmedGroup == GroupControlPlane
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

	if machine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine product before adding a node")
		return "", false
	}

	if diskGb < 10 {
		ucxsvc.UiSendFailure(app, "The node needs at least 10 GB of disk")
		return "", false
	}

	if !clusterValidateRecordMutation(app, stack, record, machine, "new nodes") {
		return "", false
	}

	if !clusterNodesRequireUcxLabels(app) {
		return "", false
	}

	expectedHostnames := []string{}
	for i := 0; i < count; i++ {
		allocationId := record.NextAllocationId + i
		if !AllocationIdIsValid(allocationId) {
			ucxsvc.UiSendFailure(app, fmt.Sprintf(
				"The cluster has no free addresses left in its subnet for %d nodes (next allocation: %d)",
				count,
				record.NextAllocationId,
			))
			return "", false
		}
		expectedHostnames = append(expectedHostnames, ClusterNodeHostname(trimmedGroup, allocationId))
	}

	operationUid := ClusterTopologyOperationUid()

	for i := 0; i < count; i++ {
		jobId, nodeOk := ClusterAddNodeLocked(app, stack, flow, record, trimmedGroup, machine, diskGb, operationUid)
		if !nodeOk {
			return "", false
		}
		_ = jobId
	}
	return "", true
}

func clusterCleanupNewNode(
	stack *ucxsvc.Stack,
	flow *clusterRecordFlow,
	record *ClusterRecord,
	node *ClusterNodeRecord,
	app ucx.Application,
) {
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
		if !flow.Commit(record) {
			ucxsvc.UiSendFailure(app, "The node could not be created and the cleanup outcome could not be recorded. The cluster requires an explicit recovery")
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

	if flow.Commit(record) {
		ucxsvc.UiSendFailure(app, "The node could not be created and these resources need manual cleanup: "+strings.Join(failures, ", "))
	} else {
		ucxsvc.UiSendFailure(app, "The node could not be created and cleanup failed, these resources need manual cleanup: "+strings.Join(failures, ", "))
	}
}

// A cluster record flow holds the cluster record and its revision. Every
// commit is a compare-and-swap; if another writer changed the record, the
// commit fails and the operation must be retried.
type clusterRecordFlow struct {
	client ClusterStateClient
}

func ClusterTopologyOperationUid() string {
	return fmt.Sprintf("%s-%d-%s", SanitizeForPath(clusterStateHolderLabel()), time.Now().UnixNano(), util.SecureToken())
}

func clusterStateHolderLabel() string {
	hostname, err := os.Hostname()
	holder := ""
	if err == nil {
		holder = strings.TrimSpace(hostname)
	}

	label := "k8s-app"
	if holder != "" {
		label = holder
	}

	if len(label) > 128 {
		label = label[:128]
	}
	return label
}

func clusterOpenRecordFlow(app ucx.Application, action string, nodeHostname string) (*clusterRecordFlow, func(), bool) {
	client, err := ClusterStateClientNewHost()
	if err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not open the cluster state: %s", err))
		return nil, func() {}, false
	}

	flow := &clusterRecordFlow{client: client}
	return flow, func() {}, true
}

func (f *clusterRecordFlow) Record() (*ClusterRecord, bool) {
	snapshot, err := ClusterStateRead(f.client)
	if err != nil || !snapshot.Found {
		return nil, false
	}

	record := snapshot.Record
	ClusterStateRecordRevisionSet(&record, snapshot.Revision)
	return &record, true
}

func (f *clusterRecordFlow) Commit(record *ClusterRecord) bool {
	revision, revErr := ClusterStateRecordRevision(record)
	if revErr != nil {
		return false
	}

	newRevision, err := ClusterStateRecordWrite(f.client, record, revision)
	if err != nil {
		return false
	}

	ClusterStateRecordRevisionSet(record, newRevision)
	return true
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
