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
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed scripts
var scriptFiles embed.FS

const GroupControlPlane = k8sapi.GroupControlPlane

const StackType = k8sapi.StackType

const (
	StackGroupingLabel  = k8sapi.LabelNodeGroup
	NodeAllocationLabel = k8sapi.LabelNodeAllocation
	K8sVersionLabel     = k8sapi.LabelK8sVersion

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
	clusterRecordPhaseProvisioning = k8sapi.ClusterPhaseProvisioning
	clusterRecordPhaseCleanup      = k8sapi.ClusterPhaseCleanup
	clusterRecordPhaseError        = k8sapi.ClusterPhaseError

	ClusterRecordPhaseCreated    = k8sapi.ClusterPhaseCreated
	ClusterRecordPhaseRecovering = k8sapi.ClusterPhaseRecovering
)

const ScriptBundleRevision = 24

func BundlePathForRelease(release K3sRelease) string {
	return filepath.Join("bundles", strconv.Itoa(ScriptBundleRevision), SanitizeForPath(release.Release))
}

type ClusterPoolSpec struct {
	Name    string
	Machine accapi.ProductReference
	Nodes   int
	DiskGb  int
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

func ClusterCreate(app ucx.Application, request k8sapi.DeployRequest) (*ucxsvc.Stack, bool, string) {
	release, ok := ReleaseByExactVersion(request.K8sVersion)
	if !ok {
		return &ucxsvc.Stack{}, false, "unknown Kubernetes version: " + request.K8sVersion
	}

	if !clusterNodesRequireUcxLabels(app) {
		return &ucxsvc.Stack{}, false, ""
	}

	plans := make([]k8sapi.PoolSpec, 0, len(request.WorkerPools)+1)
	plans = append(plans, k8sapi.PoolSpec{
		Name:    GroupControlPlane,
		Machine: request.ControlPlane.Machine,
		Nodes:   request.ControlPlane.Nodes,
		Disk:    request.ControlPlane.Disk,
	})
	plans = append(plans, request.WorkerPools...)

	ucxsvc.UiSendProgress(app, "Creating cluster network and service")

	session := *app.Session()

	linkProducts, err := ucxapi.PublicLinksRetrieveProducts.Invoke(session, util.Empty{})
	if err != nil || len(linkProducts) == 0 {
		return &ucxsvc.Stack{}, false, "could not find a suitable public link product, but this cluster requires it"
	}
	linkDomain := orcapi.IngressSupport{
		Prefix:  linkProducts[0].Support.Prefix,
		Suffix:  linkProducts[0].Support.Suffix,
		Product: linkProducts[0].Product.ToReference(),
	}

	stackIdLower := strings.ToLower(request.Name)
	apiLinkDomain := fmt.Sprintf("%s%s-api%s", linkDomain.Prefix, stackIdLower, linkDomain.Suffix)

	stack, failure := ucxsvc.StackCreateWithDriveEx(app, request.Name, StackType)
	if failure != "" {
		return stack, false, failure
	}

	stateClient, clientErr := ClusterStateClientNewSession(session, stack.InstanceId)
	if clientErr != nil {
		return stack, false, fmt.Sprintf("could not open the cluster state client: %s", clientErr)
	}

	existingState, readErr := ClusterStateRead(stateClient)
	if readErr != nil {
		return stack, false, fmt.Sprintf("could not check the existing cluster state: %s", readErr)
	}
	if existingState.Found {
		return stack, false, "a cluster record already exists for this cluster; resource creation was not restarted"
	}

	stopHeartbeat, heartbeatFailed := ucxsvc.StackStartHeartbeat(stack)
	defer func() {
		_ = stopHeartbeat()
	}()

	network, networkOk := ucxsvc.PrivateNetworkCreateWithCidr(stack, request.Name, util.OptValue(ClusterVmCidr))
	if !networkOk {
		return stack, false, ""
	}

	servicePorts := []orcapi.ServicePort{
		{
			Name:                "api",
			Port:                ApiPort,
			Protocol:            orcapi.ServicePortProtocolTcp,
			ApplicationProtocol: util.OptValue("HTTPS"),
			BackendTls:          util.OptValue(orcapi.ServiceBackendTls{InsecureSkipVerify: true}),
		},
	}
	clusterService := ucxsvc.ServiceCreate(stack, request.Name+"-k8s", servicePorts, util.OptValue(network.Id))
	if !stack.Ok {
		return stack, false, ""
	}

	ucxsvc.UiSendProgress(app, "Waiting for the cluster service")

	serviceReady, serviceDnsName := ucxsvc.ServiceWaitReady(stack, clusterService.Id, 2*time.Minute)
	if !serviceReady || serviceDnsName == "" {
		stack.Ok = false
		return stack, false, "the cluster service never became ready with an internal address"
	}

	record := &ClusterRecord{
		SchemaRevision:   clusterRecordSchemaRevision,
		StackId:          request.Name,
		K8sVersion:       release.Release,
		NetworkId:        network.Id,
		ServiceId:        clusterService.Id,
		ServiceDnsName:   serviceDnsName,
		MachineProvider:  request.Provider,
		Subnets:          []string{ClusterVmCidr, ClusterPodCidr, ClusterServiceCidr},
		BundlePath:       BundlePathForRelease(release),
		NextAllocationId: 1,
		Phase:            clusterRecordPhaseProvisioning,
	}

	for _, pool := range plans {
		record.Pools = append(record.Pools, ClusterPoolRecord{
			Name:    pool.Name,
			Machine: pool.Machine,
			DiskGb:  pool.Disk,
		})
	}

	if err := ClusterRecordWrite(stateClient, record); err != nil {
		return stack, false, fmt.Sprintf("could not create the cluster record: %s", err)
	}

	tokens := ClusterTokens{
		ServerToken: util.SecureToken(),
		AgentToken:  util.SecureToken(),
	}

	writeBundle(stack, record.BundlePath)
	ucxsvc.StackWriteFile(stack, KubeconfigTemplatePath, fmt.Sprintf(Script("kubeconfig.tpl"), apiLinkDomain))
	ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-api", request.Name), ucxsvc.PublicLinkCreateOptions{
		ServiceTarget: util.OptValue(orcapi.PublicLinkServiceTarget{
			ServiceId: clusterService.Id,
			Port:      "api",
		}),
	})
	customUi := ClusterNodeCustomUi(stack)
	if !stack.Ok {
		return stack, false, ""
	}

	for _, pool := range plans {
		for i := 1; i <= pool.Nodes; i++ {
			if heartbeatFailed() {
				return stack, false, "the cluster creation lease was lost; the failed resources are cleaned up automatically"
			}

			allocationId := record.NextAllocationId
			hostname := ClusterNodeHostname(pool.Name, allocationId)

			if pool.Name == GroupControlPlane {
				ucxsvc.UiSendProgress(app, fmt.Sprintf("Creating control plane node %d/%d", i, pool.Nodes))
			} else {
				ucxsvc.UiSendProgress(app, fmt.Sprintf("Creating worker pool %s node %d/%d", pool.Name, i, pool.Nodes))
			}

			created, failReason := clusterCreateNode(stack, clusterNodeOptions{
				record:          record,
				release:         release,
				tokens:          tokens,
				group:           pool.Name,
				machine:         pool.Machine,
				diskGb:          pool.Disk,
				allocationId:    allocationId,
				customUi:        customUi,
				session:         session,
				stateClient:     stateClient,
				heartbeatFailed: heartbeatFailed,
			})
			if !created {
				cleanupErr := clusterCreateCleanupFailed(stateClient, stack, record, failReason)
				if cleanupErr != nil {
					return stack, false, fmt.Sprintf(
						"could not create node %s; the cluster record with the cleanup state could not be saved, so the failed resources need manual cleanup: %s",
						hostname,
						cleanupErr,
					)
				}

				return stack, false, "could not create node " + hostname + "; the failed resources are cleaned up automatically"
			}
		}
	}

	if err := stopHeartbeat(); err != nil {
		stack.Ok = false
		return stack, false, "the cluster creation lease was lost; the failed resources are cleaned up automatically"
	}

	if err := ucxsvc.StackConfirm(stack); err != nil {
		stack.Ok = false
		return stack, false, fmt.Sprintf("the cluster resources were created, but the cluster could not be confirmed: %s", err)
	}

	record.Phase = ClusterRecordPhaseCreated
	if err := ClusterRecordWrite(stateClient, record); err != nil {
		stack.Ok = false
		return stack, false, fmt.Sprintf("the cluster resources were created, but the cluster record could not be finalized: %s", err)
	}

	ucxsvc.UiSendSuccess(app, "Cluster resources created")

	if _, err := ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId}); err != nil {
		return stack, true, ""
	}

	return stack, true, ""
}

func clusterCreateCleanupFailed(client ClusterStateClient, stack *ucxsvc.Stack, record *ClusterRecord, reason string) error {
	if reason != "" {
		record.FailureReason = reason
	}
	record.Phase = clusterRecordPhaseError
	return ClusterRecordWrite(client, record)
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

func clusterNodeJobSpec(
	stack *ucxsvc.Stack,
	hostname string,
	group string,
	allocationId int,
	machine accapi.ProductReference,
	diskGb int,
	release K3sRelease,
	customUi ucxsvc.UcxCustomUiServiceInit,
	extraLabels map[string]string,
	resources []orcapi.AppParameterValue,
) orcapi.JobSpecification {
	labels := map[string]string{
		StackGroupingLabel:                    group,
		NodeAllocationLabel:                   strconv.Itoa(allocationId),
		K8sVersionLabel:                       release.Release,
		orcapi.ResourceLabelAntiAffinityGroup: clusterAntiAffinityGroup(stack, group),
	}
	labels = util.MapMerge(labels, extraLabels)

	resources, labels = clusterControlPlaneWiring(stack, group, allocationId, customUi, resources, labels)

	return orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: machine,
			Labels:  labels,
		},
		Application: ucxsvc.VmImageUbuntu26_04,
		Name:        hostname,
		Hostname:    util.OptValue[string](hostname),
		Parameters:  clusterNodeParameters(stack, hostname, diskGb),
		Replicas:    1,
		Resources:   resources,
	}
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
			if releaseErr := clusterReleaseReservation(opts, stack, record, reservation.Id, hostname); releaseErr != nil {
				return false, "could not reserve the node address and could not save the cleanup state: " + releaseErr.Error()
			}
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
		if releaseErr := clusterReleaseReservation(opts, stack, record, reservation.Id, hostname); releaseErr != nil {
			return false, "could not persist the node record and could not save the cleanup state: " + releaseErr.Error()
		}
		return false, ""
	}

	attachments := []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(opts.record.NetworkId, ipAddress),
		ucxsvc.StackSubtreeMount(stack, opts.record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(opts.allocationId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}

	job, err := clusterCreateNodeJob(stack, clusterNodeJobSpec(
		stack,
		hostname,
		opts.group,
		opts.allocationId,
		opts.machine,
		opts.diskGb,
		opts.release,
		opts.customUi,
		nil,
		attachments,
	))
	if err != nil {
		if releaseErr := clusterReleaseReservation(opts, stack, record, reservation.Id, hostname); releaseErr != nil {
			return false, "could not create the node job and could not save the cleanup state: " + releaseErr.Error()
		}
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
			if releaseErr := clusterReleaseReservation(opts, stack, record, reservation.Id, hostname); releaseErr != nil {
				return false, "could not write the stack identity of node " + hostname +
					" and could not save the cleanup state: " + releaseErr.Error()
			}
		}
		return false, "could not write the stack identity of node " + hostname
	}

	if opts.group == GroupControlPlane {
		if !ucxsvc.ServiceAddMembers(stack, opts.record.ServiceId, []string{job.Id}) {
			if clusterCreateAuthorityHeld(opts) {
				_ = ucxsvc.JobTerminate(stack, job.Id)
				if releaseErr := clusterReleaseReservation(opts, stack, record, reservation.Id, hostname); releaseErr != nil {
					return false, "could not add the node " + hostname + " to the cluster service" +
						" and could not save the cleanup state: " + releaseErr.Error()
				}
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

	if spec.Session == nil {
		return errors.New("the recovery session is nil")
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

	jobSpec := clusterNodeJobSpec(
		stack,
		node.Hostname,
		GroupControlPlane,
		spec.AllocationId,
		node.Machine,
		node.DiskGb,
		spec.Release,
		spec.CustomUi,
		nil,
		attachments,
	)
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
		ownerProject = job.Owner.Project.Value
	}

	identity := map[string]string{
		"stackId":        stack.InstanceId,
		"provider":       job.Specification.Product.Provider,
		"ownerCreatedBy": job.Owner.CreatedBy,
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

func clusterReleaseReservation(opts clusterNodeOptions, stack *ucxsvc.Stack, record *ClusterRecord, reservationId string, hostname string) error {
	if !clusterCreateAuthorityHeld(opts) {
		return nil
	}

	err := ucxsvc.PrivateNetworkIpDelete(stack, reservationId)
	if err != nil {
		record.PendingCleanup = append(record.PendingCleanup, ClusterPendingCleanup{
			ReservationId: reservationId,
			Hostname:      hostname,
		})
		return ClusterRecordMarkFailed(opts.stateClient, record, "could not release the reservation of node "+hostname+
			". Reservation id "+reservationId+" needs manual cleanup")
	}

	return ClusterRecordWrite(opts.stateClient, record)
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

func NodesInGroup(record ClusterRecord, group string) []ClusterNodeRecord {
	result := []ClusterNodeRecord{}
	for _, node := range record.Nodes {
		if node.Group == group {
			result = append(result, node)
		}
	}
	return result
}

func NewUid() string {
	return fmt.Sprintf("%s-%d-%s", SanitizeForPath(clusterStateHolderLabel()), time.Now().UnixNano(), util.SecureToken())
}

func clusterAntiAffinityGroup(stack *ucxsvc.Stack, group string) string {
	return stack.InstanceId + "-" + group
}
