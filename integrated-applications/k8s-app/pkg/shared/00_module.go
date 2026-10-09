package shared

import (
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"log"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed scripts
var scriptFiles embed.FS

const GroupControlPlane = "control-plane"

const StackType = "Kubernetes"

const (
	StackGroupingLabel  = "ucloud.dk/k8s-node-group"
	NodeAllocationLabel = "ucloud.dk/k8s-node-id"
	K8sVersionLabel     = "ucloud.dk/k8s-version"

	TopologyOperationLabel = "ucloud.dk/k8s-topology-operation"
)

const (
	ClusterRecordPath               = "management/cluster.json"
	KubeconfigTemplatePath          = "management/kubeconfig.tpl"
	KubernetesConfigurationFileName = "management/kubeconfig"
	KubernetesTokenFileName         = "management/kube-api-token"
)

const controllerRegistrationTokenFile = "controller-token"

const nodeDecommissionedMarker = "decommissioned"

const (
	managementDir       = "management"
	managementMountPath = "/etc/ucloud-k8s/management"
	nodesDir            = "nodes"
	nodesMountPath      = "/etc/ucloud-k8s/nodes"
	backupsDir          = "backups"
	backupsMountPath    = "/etc/ucloud-k8s/backups"
	storageDir          = "k3s-storage"
	storageMountPath    = "/etc/ucloud-stack/k3s/storage"
	vmDisksDir          = "vm-disks"
	bundleMountPath     = "/etc/ucloud-k8s/bundle"
	inputMountPath      = "/etc/ucloud-k8s/input"
	recoveryMountPath   = "/etc/ucloud-k8s/recovery"
	launcherPath        = bundleMountPath + "/launcher.sh"

	ManagementMountPath = managementMountPath
	NodesMountPath      = nodesMountPath
	BackupsMountPath    = backupsMountPath
)

const (
	ApiPort         = 6443
	HeadlampPort    = 30500
	IngressNodePort = 30080
	customUiPort    = 43102
)

const clusterRecordSchemaRevision = 3

func Script(name string) string {
	data, err := scriptFiles.ReadFile("scripts/" + name)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}

const (
	clusterRecordPhaseProvisioning = "provisioning"
	clusterRecordPhaseCleanup      = "cleanup-pending"
	clusterRecordPhaseError        = "error"

	ClusterRecordPhaseCreated    = "created"
	ClusterRecordPhaseRecovering = "recovering"
)

const ScriptBundleRevision = 20

func BundlePathForRelease(release K3sRelease) string {
	return filepath.Join("bundles", strconv.Itoa(ScriptBundleRevision), SanitizeForPath(release.Release))
}

type ClusterPoolSpec struct {
	Name    string
	Machine accapi.ProductReference
	Nodes   int
	DiskGb  int
}

type ClusterSpec struct {
	ControlPlaneMachine accapi.ProductReference
	ControlPlaneNodes   int
	ControlPlaneDiskGb  int
	WorkerPools         []ClusterPoolSpec
	K8sVersion          string
}

type ClusterNodeRecord struct {
	AllocationId   int                     `json:"allocationId"`
	Group          string                  `json:"group"`
	Hostname       string                  `json:"hostname"`
	JobId          string                  `json:"jobId"`
	IpAddress      string                  `json:"ipAddress"`
	ReservationId  string                  `json:"reservationId"`
	Machine        accapi.ProductReference `json:"machine,omitempty"`
	DiskGb         int                     `json:"diskGb,omitempty"`
	DesiredVersion string                  `json:"desiredVersion,omitempty"`
}

type ClusterPoolRecord struct {
	Name    string                  `json:"name"`
	Machine accapi.ProductReference `json:"machine"`
	DiskGb  int                     `json:"diskGb"`
}

type ClusterRecord struct {
	SchemaRevision   int                     `json:"schemaRevision"`
	StackId          string                  `json:"stackId"`
	K8sVersion       string                  `json:"k8sVersion"`
	NetworkId        string                  `json:"networkId"`
	ServiceId        string                  `json:"serviceId"`
	ServiceDnsName   string                  `json:"serviceDnsName"`
	MachineProvider  string                  `json:"machineProvider"`
	Subnets          []string                `json:"subnets"`
	BundlePath       string                  `json:"bundlePath"`
	NextAllocationId int                     `json:"nextAllocationId"`
	Phase            string                  `json:"phase"`
	FailureReason    string                  `json:"failureReason,omitempty"`
	PendingCleanup   []ClusterPendingCleanup `json:"pendingCleanup,omitempty"`
	Pools            []ClusterPoolRecord     `json:"pools,omitempty"`
	Nodes            []ClusterNodeRecord     `json:"nodes"`

	stateRevision int64
}

type ClusterPendingCleanup struct {
	ReservationId string `json:"reservationId"`
	JobId         string `json:"jobId"`
	Hostname      string `json:"hostname"`
	IpAddress     string `json:"ipAddress"`
}

func ClusterRecordWrite(client ClusterStateClient, record *ClusterRecord) error {
	revision, err := ClusterStateRecordRevision(record)
	if err != nil {
		return err
	}

	newRevision, err := ClusterStateRecordWrite(client, record, revision)
	if err != nil {
		return err
	}

	ClusterStateRecordRevisionSet(record, newRevision)
	return nil
}

func ClusterRecordMarkFailed(client ClusterStateClient, record *ClusterRecord, reason string) error {
	record.Phase = clusterRecordPhaseError
	record.FailureReason = reason
	return ClusterRecordWrite(client, record)
}

type ClusterTokens struct {
	ServerToken string
	AgentToken  string
}

type poolPlan struct {
	name    string
	machine accapi.ProductReference
	nodes   int
	diskGb  int
}

func planPools(spec ClusterSpec) []poolPlan {
	plans := []poolPlan{{
		name:    GroupControlPlane,
		machine: spec.ControlPlaneMachine,
		nodes:   spec.ControlPlaneNodes,
		diskGb:  spec.ControlPlaneDiskGb,
	}}
	for _, pool := range spec.WorkerPools {
		plans = append(plans, poolPlan{
			name:    pool.Name,
			machine: pool.Machine,
			nodes:   pool.Nodes,
			diskGb:  pool.DiskGb,
		})
	}
	return plans
}

func clusterActiveNodeCount(stack *ucxsvc.Stack, record *ClusterRecord) (int, bool) {
	active := 0
	for _, node := range record.Nodes {
		if node.JobId == "" {
			active++
			continue
		}

		job, err := ucxsvc.JobRetrieve(stack, node.JobId)
		if err != nil {
			return 0, false
		}

		if job.Status.State.IsFinal() {
			continue
		}

		active++
	}
	return active, true
}

func ClusterCreate(app ucx.Application, stackId string, spec ClusterSpec) (*ucxsvc.Stack, bool) {
	release, ok := ReleaseByExactVersion(spec.K8sVersion)
	if !ok {
		ucxsvc.UiSendFailure(app, "Unknown Kubernetes version: "+spec.K8sVersion)
		return &ucxsvc.Stack{}, false
	}

	if spec.ControlPlaneNodes < 1 || spec.ControlPlaneNodes > 7 || spec.ControlPlaneNodes%2 != 1 {
		ucxsvc.UiSendFailure(app, "The cluster control plane needs an odd number of nodes, between 1 and 7")
		return &ucxsvc.Stack{}, false
	}

	if !clusterNodesRequireUcxLabels(app) {
		return &ucxsvc.Stack{}, false
	}

	totalNodes := 0
	for _, pool := range planPools(spec) {
		totalNodes += pool.nodes
	}
	if totalNodes > ClusterMaxNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster supports at most %d nodes, but %d were requested", ClusterMaxNodes, totalNodes))
		return &ucxsvc.Stack{}, false
	}

	stack, ok := ucxsvc.StackCreateWithDrive(app, stackId, StackType)
	if !ok {
		return stack, false
	}

	session := *app.Session()

	stateClient, clientErr := ClusterStateClientNewSession(session, stack.InstanceId)
	if clientErr != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not open the cluster state client: %s", clientErr))
		return stack, false
	}

	existingState, readErr := ClusterStateRead(stateClient)
	if readErr != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not check the existing cluster state: %s", readErr))
		return stack, false
	}
	if existingState.Found {
		ucxsvc.UiSendFailure(app, "A cluster record already exists for this stack. Resource creation was not restarted")
		return stack, false
	}

	stopHeartbeat, heartbeatFailed := ucxsvc.StackStartHeartbeat(stack)
	defer func() {
		_ = stopHeartbeat()
	}()

	linkProducts, err := ucxapi.PublicLinksRetrieveProducts.Invoke(session, util.Empty{})
	if err != nil || len(linkProducts) == 0 {
		ucxsvc.UiSendFailure(app, "Could not find a suitable public link product, but this cluster requires it.")
		return stack, false
	}
	linkDomain := orcapi.IngressSupport{
		Prefix:  linkProducts[0].Support.Prefix,
		Suffix:  linkProducts[0].Support.Suffix,
		Product: linkProducts[0].Product.ToReference(),
	}

	stackIdLower := strings.ToLower(stackId)
	apiLinkDomain := fmt.Sprintf("%s%s-api%s", linkDomain.Prefix, stackIdLower, linkDomain.Suffix)

	computeProducts, err := ucxapi.JobsRetrieveProducts.Invoke(session, util.Empty{})
	if err != nil || len(computeProducts) == 0 {
		ucxsvc.UiSendFailure(app, "Could not find a suitable compute product, but this cluster requires it.")
		return stack, false
	}
	computeProvider := computeProducts[0].Product.ToReference().Provider
	if spec.ControlPlaneMachine.Provider != "" && spec.ControlPlaneMachine.Provider != computeProvider {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The machine must come from the provider that runs the cluster (%s), but %s was selected",
			computeProvider,
			spec.ControlPlaneMachine.Provider,
		))
		return stack, false
	}

	network, networkOk := ucxsvc.PrivateNetworkCreateWithCidr(stack, stackId, util.OptValue(ClusterVmCidr))
	if !networkOk {
		return stack, false
	}

	servicePorts := []orcapi.ServicePort{
		{
			Name:                "api",
			Port:                ApiPort,
			Protocol:            orcapi.ServicePortProtocolTcp,
			ApplicationProtocol: util.OptValue("HTTPS"),
			BackendTls:          util.OptValue(orcapi.ServiceBackendTls{InsecureSkipVerify: true}),
		},
		{
			Name:                "headlamp",
			Port:                HeadlampPort,
			Protocol:            orcapi.ServicePortProtocolTcp,
			ApplicationProtocol: util.OptValue("HTTP"),
			HealthCheck: util.OptValue(orcapi.ServiceHealthCheck{
				Type:               orcapi.ServiceHealthCheckTypeHttp,
				Path:               "/",
				IntervalSeconds:    5,
				TimeoutSeconds:     5,
				HealthyThreshold:   2,
				UnhealthyThreshold: 2,
			}),
		},
	}
	clusterService := ucxsvc.ServiceCreate(stack, stackId+"-k8s", servicePorts, util.OptValue(network.Id))
	if !stack.Ok {
		return stack, false
	}

	serviceReady, serviceDnsName := ucxsvc.ServiceWaitReady(stack, clusterService.Id, 2*time.Minute)
	if !serviceReady || serviceDnsName == "" {
		stack.Ok = false
		ucxsvc.UiSendFailure(app, "The cluster service never became ready with an internal address")
		return stack, false
	}

	record := &ClusterRecord{
		SchemaRevision:   clusterRecordSchemaRevision,
		StackId:          stackId,
		K8sVersion:       release.Release,
		NetworkId:        network.Id,
		ServiceId:        clusterService.Id,
		ServiceDnsName:   serviceDnsName,
		MachineProvider:  spec.ControlPlaneMachine.Provider,
		Subnets:          []string{ClusterVmCidr, ClusterPodCidr, ClusterServiceCidr},
		BundlePath:       BundlePathForRelease(release),
		NextAllocationId: 1,
		Phase:            clusterRecordPhaseProvisioning,
	}

	for _, pool := range planPools(spec) {
		record.Pools = append(record.Pools, ClusterPoolRecord{
			Name:    pool.name,
			Machine: pool.machine,
			DiskGb:  pool.diskGb,
		})
	}

	if err := ClusterRecordWrite(stateClient, record); err != nil {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not create the cluster record: %s", err))
		return stack, false
	}

	tokens := ClusterTokens{
		ServerToken: util.SecureToken(),
		AgentToken:  util.SecureToken(),
	}

	writeBundle(stack, record.BundlePath)
	if !stack.Ok {
		return stack, false
	}

	ucxsvc.StackWriteFile(stack, KubeconfigTemplatePath, fmt.Sprintf(Script("kubeconfig.tpl"), apiLinkDomain))
	if !stack.Ok {
		return stack, false
	}

	ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-api", stackId), ucxsvc.PublicLinkCreateOptions{
		ServiceTarget: util.OptValue(orcapi.PublicLinkServiceTarget{
			ServiceId: clusterService.Id,
			Port:      "api",
		}),
	})
	ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-dashboard", stackId), ucxsvc.PublicLinkCreateOptions{
		ServiceTarget: util.OptValue(orcapi.PublicLinkServiceTarget{
			ServiceId: clusterService.Id,
			Port:      "headlamp",
		}),
	})
	if !stack.Ok {
		return stack, false
	}

	customUi := ClusterNodeCustomUi(stack)
	if !stack.Ok {
		return stack, false
	}

	for _, pool := range planPools(spec) {
		for i := 1; i <= pool.nodes; i++ {
			if heartbeatFailed() {
				ucxsvc.UiSendFailure(app, "The stack lease was lost during creation, the failed resources are cleaned up automatically.")
				return stack, false
			}

			allocationId := record.NextAllocationId
			hostname := ClusterNodeHostname(pool.name, allocationId)

			created, failReason := clusterCreateNode(stack, clusterNodeOptions{
				record:          record,
				release:         release,
				tokens:          tokens,
				group:           pool.name,
				machine:         pool.machine,
				diskGb:          pool.diskGb,
				allocationId:    allocationId,
				customUi:        customUi,
				session:         session,
				stateClient:     stateClient,
				heartbeatFailed: heartbeatFailed,
			})
			if !created {
				clusterCreateCleanupFailed(stateClient, stack, record, failReason)
				ucxsvc.UiSendFailure(app, "Could not create node "+hostname+". The failed resources are cleaned up automatically.")
				return stack, false
			}
		}
	}

	if err := stopHeartbeat(); err != nil {
		stack.Ok = false
		ucxsvc.UiSendFailure(app, "The stack lease was lost during creation, the failed resources are cleaned up automatically.")
		return stack, false
	}

	if err := ucxsvc.StackConfirm(stack); err != nil {
		stack.Ok = false
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster resources were created, but the stack could not be confirmed: %s", err))
		return stack, false
	}

	record.Phase = ClusterRecordPhaseCreated
	if err := ClusterRecordWrite(stateClient, record); err != nil {
		stack.Ok = false
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster resources were created, but the cluster record could not be finalized: %s", err))
		return stack, false
	}

	if _, err := ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId}); err != nil {
		return stack, true
	}

	return stack, true
}

func clusterCreateCleanupFailed(client ClusterStateClient, stack *ucxsvc.Stack, record *ClusterRecord, reason string) {
	if reason != "" {
		record.FailureReason = reason
	}
	record.Phase = clusterRecordPhaseError
	_ = ClusterRecordWrite(client, record)
}

type clusterNodeOptions struct {
	record          *ClusterRecord
	release         K3sRelease
	tokens          ClusterTokens
	group           string
	machine         accapi.ProductReference
	diskGb          int
	allocationId    int
	customUi        ucxsvc.UcxCustomUiServiceInit
	session         *ucx.Session
	stateClient     ClusterStateClient
	heartbeatFailed func() bool
}

func clusterNodesRequireUcxLabels(app ucx.Application) bool {
	if len(ucxsvc.UcxPortLabel(0)) == 2 {
		return true
	}

	ucxsvc.UiSendFailure(app, "The UCX application name and version labels are unavailable, but all nodes need them")
	return false
}

func clusterControlPlaneWiring(
	stack *ucxsvc.Stack,
	group string,
	allocationId int,
	customUi ucxsvc.UcxCustomUiServiceInit,
	attachments []orcapi.AppParameterValue,
	labels map[string]string,
) ([]orcapi.AppParameterValue, map[string]string) {
	if group != GroupControlPlane {
		labels = util.MapMerge(labels, ucxsvc.UcxPortLabel(0))
		labels[orcapi.ResourceLabelInitScript] = launcherPath
		return attachments, labels
	}

	attachments = append(attachments,
		ucxsvc.StackSubtreeMount(stack, managementDir, managementMountPath, false),
		ucxsvc.StackSubtreeMount(stack, nodesDir, nodesMountPath, false),
		ucxsvc.StackSubtreeMount(stack, backupsDir, backupsMountPath, false),
	)

	labels = util.MapMerge(labels, customUi.Labels)
	labels[orcapi.ResourceLabelStackController] = "true"

	initScript := Script("launcher.sh") + "\n" + customUi.InitScript
	initLabels := ucxsvc.StackWriteInitScriptAt(stack, initScript, inputDirFor(allocationId), inputMountPath)
	labels = util.MapMerge(labels, initLabels)

	return attachments, labels
}

func clusterCreateNode(stack *ucxsvc.Stack, opts clusterNodeOptions) (bool, string) {
	ipAddress := NodeIpForAllocation(opts.allocationId)
	hostname := ClusterNodeHostname(opts.group, opts.allocationId)

	clusterEnsureDir(stack, storageDir)
	if opts.group == GroupControlPlane {
		clusterEnsureDir(stack, nodesDir)
		clusterEnsureDir(stack, backupsDir)
	}
	if !stack.Ok {
		return false, "could not prepare the node directories"
	}

	if !clusterWriteNodeInput(stack, opts.record, opts.release, clusterNodeInputSpec{
		group:        opts.group,
		allocationId: opts.allocationId,
		firstServer:  opts.group == GroupControlPlane && opts.allocationId == 1,
		tokens:       opts.tokens,
	}) {
		return false, "could not write the input files of the node"
	}

	record := opts.record
	record.NextAllocationId = opts.allocationId + 1
	if err := ClusterRecordWrite(opts.stateClient, record); err != nil {
		return false, "could not persist the node allocation"
	}

	reservation, err := ucxsvc.PrivateNetworkIpReserveRetry(stack, record.NetworkId, ipAddress, 30*time.Second)
	if err != nil {
		if reservation.Id != "" {
			clusterReleaseReservation(opts, stack, record, reservation.Id, hostname)
		}
		return false, ""
	}

	record.Nodes = append(record.Nodes, ClusterNodeRecord{
		AllocationId:   opts.allocationId,
		Group:          opts.group,
		Hostname:       hostname,
		JobId:          "",
		IpAddress:      ipAddress,
		ReservationId:  reservation.Id,
		Machine:        opts.machine,
		DiskGb:         opts.diskGb,
		DesiredVersion: opts.release.Release,
	})
	if err := ClusterRecordWrite(opts.stateClient, record); err != nil {
		clusterReleaseReservation(opts, stack, record, reservation.Id, hostname)
		return false, ""
	}

	attachments := []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(opts.record.NetworkId, ipAddress),
		ucxsvc.StackSubtreeMount(stack, opts.record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(opts.allocationId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}

	labels := map[string]string{
		StackGroupingLabel:  opts.group,
		NodeAllocationLabel: strconv.Itoa(opts.allocationId),
		K8sVersionLabel:     opts.release.Release,
	}

	attachments, labels = clusterControlPlaneWiring(stack, opts.group, opts.allocationId, opts.customUi, attachments, labels)

	job, err := clusterCreateNodeJob(stack, orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: opts.machine,
			Labels:  labels,
		},
		Application: ucxsvc.VmImageUbuntu26_04,
		Name:        hostname,
		Hostname:    util.OptValue[string](hostname),
		Parameters:  clusterNodeParameters(stack, hostname, opts.diskGb),
		Replicas:    1,
		Resources:   attachments,
	})
	if err != nil {
		clusterReleaseReservation(opts, stack, record, reservation.Id, hostname)
		return false, ""
	}

	for i := range opts.record.Nodes {
		if opts.record.Nodes[i].AllocationId == opts.allocationId {
			opts.record.Nodes[i].JobId = job.Id
			break
		}
	}

	if !clusterWriteStackIdentity(stack, opts.allocationId, job) {
		if clusterCreateAuthorityHeld(opts) {
			_ = ucxsvc.JobTerminate(stack, job.Id)
			clusterReleaseReservation(opts, stack, record, reservation.Id, hostname)
		}
		return false, "could not write the stack identity of node " + hostname
	}

	if opts.group == GroupControlPlane {
		if !ucxsvc.ServiceAddMembers(stack, opts.record.ServiceId, []string{job.Id}) {
			if clusterCreateAuthorityHeld(opts) {
				_ = ucxsvc.JobTerminate(stack, job.Id)
				clusterReleaseReservation(opts, stack, record, reservation.Id, hostname)
			}
			return false, "could not add the node to the cluster service"
		}
	}

	if err := ClusterRecordWrite(opts.stateClient, record); err != nil {
		_ = ClusterRecordMarkFailed(opts.stateClient, record, "could not persist the job id of node "+hostname+
			". Job id "+job.Id+" and reservation id "+reservation.Id+" may need manual cleanup")
		return false, ""
	}

	if opts.group == GroupControlPlane {
		if !clusterWriteControllerToken(stack, opts.session, job.Id, opts.allocationId) {
			return false, "could not write the controller registration token"
		}
	}

	return true, ""
}

type ClusterRecoveryNodeSpec struct {
	AllocationId   int
	FirstServer    bool
	Release        K3sRelease
	BackupId       string
	SnapshotSha256 string
	ServerToken    string
	AgentToken     string
	CustomUi       ucxsvc.UcxCustomUiServiceInit
	Session        *ucx.Session
	Progress       func(string)
}

func ClusterNodeCustomUi(stack *ucxsvc.Stack) ucxsvc.UcxCustomUiServiceInit {
	return ucxsvc.UcxInitCustomUiServiceAt(stack, customUiPort, "", managementDir, managementMountPath)
}

func ClusterRecoveryFirstServerAllocation(record *ClusterRecord) int {
	lowest := 0
	if record == nil {
		return lowest
	}

	for _, node := range record.Nodes {
		if node.Group != GroupControlPlane {
			continue
		}
		if lowest == 0 || node.AllocationId < lowest {
			lowest = node.AllocationId
		}
	}

	return lowest
}

func ClusterRecoveryCreateNode(
	stack *ucxsvc.Stack,
	client ClusterStateClient,
	record *ClusterRecord,
	spec ClusterRecoveryNodeSpec,
) error {
	if stack == nil || !stack.Ok {
		return errors.New("the cluster stack is not available")
	}
	if record == nil {
		return errors.New("the cluster record is nil")
	}
	if spec.Release.Release == "" {
		return errors.New("the recovery release is empty")
	}
	if !AllocationIdIsValid(spec.AllocationId) {
		return fmt.Errorf("the allocation id %d is not valid", spec.AllocationId)
	}
	if spec.FirstServer {
		if spec.BackupId == "" || spec.SnapshotSha256 == "" || spec.ServerToken == "" || spec.AgentToken == "" {
			return errors.New("the recovery first server needs a backup, its checksum and the backup credentials")
		}
	}

	active, err := ClusterRecoveryActive(client)
	if err != nil {
		return fmt.Errorf("could not read the recovery state: %s", err)
	}
	if !active {
		return errors.New("no recovery operation is active for this cluster")
	}

	nodeIndex := -1
	for i := range record.Nodes {
		if record.Nodes[i].AllocationId == spec.AllocationId {
			nodeIndex = i
			break
		}
	}
	if nodeIndex < 0 {
		return fmt.Errorf("the cluster record has no node with allocation id %d", spec.AllocationId)
	}

	node := record.Nodes[nodeIndex]
	if node.Group != GroupControlPlane {
		return fmt.Errorf("node %s is not a control-plane node", node.Hostname)
	}
	if node.ReservationId == "" {
		return fmt.Errorf("node %s has no address reservation in the cluster record", node.Hostname)
	}

	bundleTarget := BundlePathForRelease(spec.Release)
	if record.BundlePath != bundleTarget || record.K8sVersion != spec.Release.Release {
		writeBundle(stack, bundleTarget)
		if !stack.Ok {
			return errors.New("could not write the bootstrap bundle for release " + spec.Release.Release)
		}
		record.BundlePath = bundleTarget
		record.K8sVersion = spec.Release.Release
	}

	var recovery *clusterRecoveryInput
	if spec.FirstServer {
		recovery = &clusterRecoveryInput{
			backupId:       spec.BackupId,
			snapshotSha256: spec.SnapshotSha256,
		}
	}

	if !clusterWriteNodeInput(stack, record, spec.Release, clusterNodeInputSpec{
		group:        GroupControlPlane,
		allocationId: spec.AllocationId,
		firstServer:  spec.FirstServer,
		tokens:       ClusterTokens{ServerToken: spec.ServerToken, AgentToken: spec.AgentToken},
		recovery:     recovery,
	}) {
		return errors.New("could not write the input files of node " + node.Hostname)
	}

	ucxsvc.StackWriteFileEx(
		stack,
		filepath.Join(inputDirFor(spec.AllocationId), nodeDecommissionedMarker),
		"",
		0600,
	)
	if !stack.Ok {
		return errors.New("could not clear the decommissioned marker of node " + node.Hostname)
	}

	attachments := []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(record.NetworkId, node.IpAddress),
		ucxsvc.StackSubtreeMount(stack, record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(spec.AllocationId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}
	if spec.FirstServer {
		attachments = append(attachments, ucxsvc.StackSubtreeMount(
			stack,
			filepath.Join(backupsDir, spec.BackupId),
			recoveryMountPath,
			true,
		))
	}

	labels := map[string]string{
		StackGroupingLabel:  GroupControlPlane,
		NodeAllocationLabel: strconv.Itoa(spec.AllocationId),
		K8sVersionLabel:     spec.Release.Release,
	}
	attachments, labels = clusterControlPlaneWiring(stack, GroupControlPlane, spec.AllocationId, spec.CustomUi, attachments, labels)

	jobSpec := orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: node.Machine,
			Labels:  labels,
		},
		Application: ucxsvc.VmImageUbuntu26_04,
		Name:        node.Hostname,
		Hostname:    util.OptValue[string](node.Hostname),
		Parameters:  clusterNodeParameters(stack, node.Hostname, node.DiskGb),
		Replicas:    1,
		Resources:   attachments,
	}
	job, err := clusterCreateNodeJobWithAddressWait(stack, jobSpec, func() {
		if spec.Progress != nil {
			spec.Progress("Waiting for IP address " + node.IpAddress + " to be released")
		}
	})
	if err != nil {
		return fmt.Errorf("could not create the replacement job of node %s: %s", node.Hostname, err)
	}

	if !clusterWriteStackIdentity(stack, spec.AllocationId, job) {
		_ = ucxsvc.JobTerminate(stack, job.Id)
		return errors.New("could not write the stack identity of node " + node.Hostname)
	}

	if record.ServiceId != "" {
		if !ucxsvc.ServiceAddMembers(stack, record.ServiceId, []string{job.Id}) {
			_ = ucxsvc.JobTerminate(stack, job.Id)
			return errors.New("could not add node " + node.Hostname + " to the cluster service")
		}
	}

	record.Nodes[nodeIndex].JobId = job.Id
	record.Nodes[nodeIndex].DesiredVersion = spec.Release.Release
	if err := ClusterRecordWrite(client, record); err != nil {
		_ = ucxsvc.JobTerminate(stack, job.Id)
		return fmt.Errorf("could not record the replacement job of node %s: %s", node.Hostname, err)
	}

	if !clusterWriteControllerToken(stack, spec.Session, job.Id, spec.AllocationId) {
		return errors.New("could not write the controller registration token of node " + node.Hostname)
	}

	return nil
}

func clusterWriteControllerToken(stack *ucxsvc.Stack, session *ucx.Session, jobId string, allocationId int) bool {
	if session == nil {
		return false
	}

	response, err := ucxapi.StackControlToken.Invoke(session, ucxapi.StackControlTokenRequest{JobId: jobId})
	if err != nil {
		return false
	}

	path := filepath.Join(inputDirFor(allocationId), controllerRegistrationTokenFile)
	ucxsvc.StackWriteFileAtomicEx(stack, path, response.Token, 0600)

	return stack.Ok
}

func clusterWriteStackIdentity(stack *ucxsvc.Stack, allocationId int, job orcapi.Job) bool {
	ownerProject := ""
	if job.Owner.Project.Present {
		ownerProject = strings.TrimSpace(job.Owner.Project.Value)
	}

	identity := map[string]string{
		"stackId":        strings.TrimSpace(stack.InstanceId),
		"provider":       strings.TrimSpace(job.Specification.Product.Provider),
		"ownerCreatedBy": strings.TrimSpace(job.Owner.CreatedBy),
		"ownerProject":   ownerProject,
	}

	data, err := json.MarshalIndent(identity, "", "  ")
	if err != nil {
		log.Fatal(err)
	}

	ucxsvc.StackWriteFileAtomicEx(
		stack,
		filepath.Join(inputDirFor(allocationId), "stack-identity.json"),
		string(data),
		0600,
	)
	return stack.Ok
}

func clusterCreateNodeJob(stack *ucxsvc.Stack, spec orcapi.JobSpecification) (orcapi.Job, error) {
	return clusterCreateNodeJobWithAddressWait(stack, spec, nil)
}

func clusterCreateNodeJobWithAddressWait(stack *ucxsvc.Stack, spec orcapi.JobSpecification, addressWait func()) (orcapi.Job, error) {
	deadline := time.Now().Add(30 * time.Second)
	addressDeadline := time.Time{}

	for {
		job, err := ucxsvc.JobCreateJob(stack, spec)
		if err == nil {
			return job, nil
		}
		if addressWait != nil && strings.Contains(err.Error(), "The pinned address ") && strings.Contains(err.Error(), " is already in use") {
			if addressDeadline.IsZero() {
				addressDeadline = time.Now().Add(5 * time.Minute)
			}
			if time.Now().After(addressDeadline) {
				return orcapi.Job{}, fmt.Errorf("the IP address was not released within 5 minutes: %w", err)
			}
			addressWait()
			time.Sleep(5 * time.Second)
			continue
		}

		if !strings.Contains(err.Error(), "is not ready yet") || time.Now().After(deadline) {
			return orcapi.Job{}, err
		}

		time.Sleep(2 * time.Second)
	}
}

func clusterNodeParameters(stack *ucxsvc.Stack, hostname string, diskGb int) map[string]orcapi.AppParameterValue {
	parameters := map[string]orcapi.AppParameterValue{
		"diskSize": orcapi.AppParameterValueInteger(int64(diskGb)),
	}

	diskFolder := ucxsvc.StackSubtreeMount(stack, filepath.Join(vmDisksDir, hostname), "", false)
	if diskFolder.Type == orcapi.AppParameterValueTypeFile {
		parameters[orcapi.JobParameterVmDiskFolder] = diskFolder
	}

	return parameters
}

func clusterReleaseReservation(opts clusterNodeOptions, stack *ucxsvc.Stack, record *ClusterRecord, reservationId string, hostname string) {
	if !clusterCreateAuthorityHeld(opts) {
		return
	}

	err := ucxsvc.PrivateNetworkIpDelete(stack, reservationId)
	if err != nil {
		record.PendingCleanup = append(record.PendingCleanup, ClusterPendingCleanup{
			ReservationId: reservationId,
			Hostname:      hostname,
		})
		_ = ClusterRecordMarkFailed(opts.stateClient, record, "could not release the reservation of node "+hostname+
			". Reservation id "+reservationId+" needs manual cleanup")
	} else {
		_ = ClusterRecordWrite(opts.stateClient, record)
	}
}

func clusterCreateAuthorityHeld(opts clusterNodeOptions) bool {
	return opts.heartbeatFailed == nil || !opts.heartbeatFailed()
}

func clusterEnsureDir(stack *ucxsvc.Stack, dir string) {
	ucxsvc.StackWriteFileEx(stack, filepath.Join(dir, ".keep"), "", 0660)
}

func writeBundle(stack *ucxsvc.Stack, bundlePath string) {
	err := fs.WalkDir(scriptFiles, "scripts", func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}

		relPath, _ := filepath.Rel("scripts", path)
		data, readErr := scriptFiles.ReadFile(path)
		if readErr != nil {
			return readErr
		}

		ucxsvc.StackWriteFileEx(stack, filepath.Join(bundlePath, relPath), string(data), 0550)
		return nil
	})
	if err != nil {
		stack.Ok = false
	}
}

type clusterNodeInputSpec struct {
	group        string
	allocationId int
	firstServer  bool
	tokens       ClusterTokens
	recovery     *clusterRecoveryInput
}

type clusterRecoveryInput struct {
	backupId       string
	snapshotSha256 string
}

func clusterWriteNodeInput(stack *ucxsvc.Stack, record *ClusterRecord, release K3sRelease, spec clusterNodeInputSpec) bool {
	ipAddress := NodeIpForAllocation(spec.allocationId)

	serverUrl := fmt.Sprintf("https://%s:%d", record.ServiceDnsName, ApiPort)

	nodeJson := map[string]any{
		"role":        spec.group,
		"firstServer": spec.firstServer,
		"hostname":    ClusterNodeHostname(spec.group, spec.allocationId),
		"ipAddress":   ipAddress,
		"serverUrl":   serverUrl,
		"serviceDns":  record.ServiceDnsName,
		"k8sVersion":  release.Release,
		"sha256Amd64": release.Sha256Amd64,
		"sha256Arm64": release.Sha256Arm64,
		"clusterCidr": ClusterPodCidr,
		"serviceCidr": ClusterServiceCidr,
		"generation":  util.SecureToken(),
	}

	if spec.recovery != nil {
		nodeJson["recoveryEnabled"] = true
		nodeJson["recoveryBackupId"] = spec.recovery.backupId
		nodeJson["recoverySnapshotSha256"] = spec.recovery.snapshotSha256
	}

	nodeData, err := json.MarshalIndent(nodeJson, "", "  ")
	if err != nil {
		log.Fatal(err)
	}

	inputDir := inputDirFor(spec.allocationId)
	ucxsvc.StackWriteFile(stack, filepath.Join(inputDir, "node.json"), string(nodeData))

	ucxsvc.StackWriteFileAtomicEx(
		stack,
		filepath.Join(inputDir, "maintenance-enrollment-secret"),
		util.SecureToken(),
		0600,
	)

	if spec.firstServer {
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "server-token"), spec.tokens.ServerToken, 0600)
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "agent-token"), spec.tokens.AgentToken, 0600)
	}

	return stack.Ok
}

func inputDirFor(allocationId int) string {
	return filepath.Join(nodesDir, strconv.Itoa(allocationId), "input")
}

func NodeDirForAllocation(allocationId int) string {
	return filepath.Join(nodesMountPath, strconv.Itoa(allocationId))
}

func ClusterNodeHostname(group string, allocationId int) string {
	return fmt.Sprintf("%s-%v", group, allocationId)
}
