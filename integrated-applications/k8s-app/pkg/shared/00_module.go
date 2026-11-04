package shared

import (
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed scripts
var scriptFiles embed.FS

const (
	KubeconfigTemplatePath          = "management/kubeconfig.tpl"
	KubernetesConfigurationFileName = "management/kubeconfig"

	controllerRegistrationTokenFile = "controller-token"

	nodeDecommissionedMarker = "decommissioned"

	managementDir       = "management"
	ManagementMountPath = "/etc/ucloud-k8s/management"
	nodesDir            = "nodes"
	nodesMountPath      = "/etc/ucloud-k8s/nodes"
	backupsDir          = "backups"
	BackupsMountPath    = "/etc/ucloud-k8s/backups"
	storageDir          = "k3s-storage"
	storageMountPath    = "/etc/ucloud-stack/k3s/storage"
	vmDisksDir          = "vm-disks"
	bundleMountPath     = "/etc/ucloud-k8s/bundle"
	inputMountPath      = "/etc/ucloud-k8s/input"
	recoveryMountPath   = "/etc/ucloud-k8s/recovery"
	launcherPath        = bundleMountPath + "/launcher.sh"

	ApiPort         = 6443
	IngressNodePort = 30080
	customUiPort    = 43102

	clusterRecordSchemaRevision = 3

	clusterRecordWriteAttempts = 8
	clusterRecordWriteDelay    = 2 * time.Second

	ScriptBundleRevision = 25
)

func Script(name string) string {
	data, err := scriptFiles.ReadFile("scripts/" + name)
	if err != nil {
		log.Fatal("k8s-app: could not read the embedded script %s: %s", name, err)
	}
	return string(data)
}

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
	InternalId          int                     `json:"allocationId"`
	Group               string                  `json:"group"`
	Hostname            string                  `json:"hostname"`
	JobId               string                  `json:"jobId"`
	IpAddress           string                  `json:"ipAddress"`
	PrivateIpIdentifier string                  `json:"reservationId"`
	Machine             accapi.ProductReference `json:"machine,omitempty"`
	DiskGb              int                     `json:"diskGb,omitempty"`
	DesiredVersion      string                  `json:"desiredVersion,omitempty"`
}

type ClusterPoolRecord struct {
	Name    string                  `json:"name"`
	Machine accapi.ProductReference `json:"machine"`
	DiskGb  int                     `json:"diskGb"`
}

type ClusterRecord struct {
	SchemaRevision  int                     `json:"schemaRevision"`
	StackId         string                  `json:"stackId"`
	K8sVersion      string                  `json:"k8sVersion"`
	NetworkId       string                  `json:"networkId"`
	ServiceId       string                  `json:"serviceId"`
	ServiceDnsName  string                  `json:"serviceDnsName"`
	MachineProvider string                  `json:"machineProvider"`
	Subnets         []string                `json:"subnets"`
	BundlePath      string                  `json:"bundlePath"`
	NextInternalId  int                     `json:"nextAllocationId"`
	Phase           string                  `json:"phase"`
	FailureReason   string                  `json:"failureReason,omitempty"`
	PendingCleanup  []ClusterPendingCleanup `json:"pendingCleanup,omitempty"`
	Pools           []ClusterPoolRecord     `json:"pools,omitempty"`
	Nodes           []ClusterNodeRecord     `json:"nodes"`

	stateRevision int64
}

type ClusterPendingCleanup struct {
	PrivateIpIdentifier string `json:"reservationId"`
	JobId               string `json:"jobId"`
	Hostname            string `json:"hostname"`
	IpAddress           string `json:"ipAddress"`
}

func ClusterRecordReplace(client ClusterStateClient, record *ClusterRecord) error {
	_, err := clusterRecordReplaceAt(client, record, record.stateRevision)
	return err
}

func ClusterRecordMarkFailed(client ClusterStateClient, reason string, mutate func(*ClusterRecord)) error {
	return ClusterRecordUpdate(client, func(record *ClusterRecord) bool {
		if mutate != nil {
			mutate(record)
		}
		record.Phase = k8sapi.ClusterPhaseError
		if reason != "" {
			record.FailureReason = reason
		}
		return true
	})
}

func ClusterRecordUpdate(client ClusterStateClient, mutate func(*ClusterRecord) bool) error {
	for attempt := 0; attempt < clusterRecordWriteAttempts; attempt++ {
		snapshot, err := ClusterStateRead(client)
		if err != nil {
			return err
		}
		if !snapshot.Found {
			return errors.New("the cluster record does not exist")
		}

		record := snapshot.Record
		if !mutate(&record) {
			return nil
		}

		_, err = clusterRecordReplaceAt(client, &record, snapshot.Revision)
		if err == nil {
			return nil
		}
		if !IsConflict(err) {
			return err
		}

		time.Sleep(clusterRecordWriteDelay)
	}

	return errors.New("the cluster record changed too often")
}

func ClusterRecordPhaseOperational(phase string) bool {
	return phase == k8sapi.ClusterPhaseCreated || phase == k8sapi.ClusterPhaseCleanup
}

func ClusterRecordPhaseAllowsRecovery(phase string) bool {
	return phase == k8sapi.ClusterPhaseCreated || phase == k8sapi.ClusterPhaseCleanup || phase == k8sapi.ClusterPhaseRecovering
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
		Name:    k8sapi.GroupControlPlane,
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

	stack, failure := ucxsvc.StackCreateWithDriveEx(app, request.Name, k8sapi.StackType)
	if failure != "" {
		return stack, false, failure
	}

	stateClient := ClusterStateClientNewSession(session, stack.InstanceId)

	existingState, readErr := ClusterStateRead(stateClient)
	if readErr != nil {
		return stack, false, fmt.Sprintf("could not check the existing cluster state: %s", readErr)
	}
	if existingState.Found {
		return stack, false, "a cluster record already exists for this cluster. Resource creation was not restarted"
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
		SchemaRevision:  clusterRecordSchemaRevision,
		StackId:         request.Name,
		K8sVersion:      release.Release,
		NetworkId:       network.Id,
		ServiceId:       clusterService.Id,
		ServiceDnsName:  serviceDnsName,
		MachineProvider: request.Provider,
		Subnets:         []string{ClusterVmCidr, ClusterPodCidr, ClusterServiceCidr},
		BundlePath:      BundlePathForRelease(release),
		NextInternalId:  1,
		Phase:           k8sapi.ClusterPhaseProvisioning,
	}

	for _, pool := range plans {
		record.Pools = append(record.Pools, ClusterPoolRecord{
			Name:    pool.Name,
			Machine: pool.Machine,
			DiskGb:  pool.Disk,
		})
	}

	if err := ClusterRecordReplace(stateClient, record); err != nil {
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
				return stack, false, "cluster creation could not continue. Cleanup of the cluster resources has been scheduled. The state drive is kept"
			}

			internalId := record.NextInternalId
			hostname := ClusterNodeHostname(pool.Name, internalId)

			if pool.Name == k8sapi.GroupControlPlane {
				ucxsvc.UiSendProgress(app, fmt.Sprintf("Creating control-plane node %d/%d", i, pool.Nodes))
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
				internalId:      internalId,
				customUi:        customUi,
				session:         session,
				stateClient:     stateClient,
				heartbeatFailed: heartbeatFailed,
			})
			if !created {
				cleanupErr := ClusterRecordMarkFailed(stateClient, failReason, nil)
				if cleanupErr != nil {
					return stack, false, fmt.Sprintf(
						"could not create node %s. Cleanup of the cluster resources has been scheduled. The state drive is kept. Could not save the cleanup details: %s",
						hostname,
						cleanupErr,
					)
				}

				return stack, false, "could not create node " + hostname + ". Cleanup of the cluster resources has been scheduled. The state drive is kept"
			}
		}
	}

	if err := stopHeartbeat(); err != nil {
		stack.Ok = false
		return stack, false, "cluster creation could not continue. Cleanup of the cluster resources has been scheduled. The state drive is kept"
	}

	if err := ucxsvc.StackConfirm(stack); err != nil {
		stack.Ok = false
		return stack, false, fmt.Sprintf("the cluster resources were created, but the cluster could not be confirmed: %s", err)
	}

	record.Phase = k8sapi.ClusterPhaseCreated
	if err := ClusterRecordReplace(stateClient, record); err != nil {
		stack.Ok = false
		return stack, false, fmt.Sprintf("the cluster resources were created, but the cluster record could not be finalized: %s", err)
	}

	ucxsvc.UiSendSuccess(app, "Cluster resources created")

	if _, err := ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId}); err != nil {
		return stack, true, ""
	}

	return stack, true, ""
}

type clusterNodeProvisionStage int

const (
	clusterNodeStagePrepare clusterNodeProvisionStage = iota
	clusterNodeStageInternalId
	clusterNodeStageReserve
	clusterNodeStageRecord
	clusterNodeStageJob
	clusterNodeStageIdentity
	clusterNodeStagePersist
	clusterNodeStageService
	clusterNodeStageCredential
)

type clusterNodeOptions struct {
	record          *ClusterRecord
	release         K3sRelease
	tokens          ClusterTokens
	writeTokens     bool
	group           string
	machine         accapi.ProductReference
	diskGb          int
	internalId      int
	customUi        ucxsvc.UcxCustomUiServiceInit
	session         *ucx.Session
	stateClient     ClusterStateClient
	heartbeatFailed func() bool
	onFailure       func(stage clusterNodeProvisionStage, node ClusterNodeRecord, jobId string, cause string) string
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
	internalId int,
	customUi ucxsvc.UcxCustomUiServiceInit,
	attachments []orcapi.AppParameterValue,
	labels map[string]string,
) ([]orcapi.AppParameterValue, map[string]string) {
	if group != k8sapi.GroupControlPlane {
		labels = util.MapMerge(labels, ucxsvc.UcxPortLabel(0))
		labels[orcapi.ResourceLabelInitScript] = launcherPath
		return attachments, labels
	}

	attachments = append(attachments,
		ucxsvc.StackSubtreeMount(stack, managementDir, ManagementMountPath, false),
		ucxsvc.StackSubtreeMount(stack, nodesDir, nodesMountPath, false),
		ucxsvc.StackSubtreeMount(stack, backupsDir, BackupsMountPath, false),
	)

	labels = util.MapMerge(labels, customUi.Labels)
	labels[orcapi.ResourceLabelStackController] = "true"

	initScript := Script("launcher.sh") + "\n" + customUi.InitScript
	initLabels := ucxsvc.StackWriteInitScriptAt(stack, initScript, inputDirFor(internalId), inputMountPath)
	labels = util.MapMerge(labels, initLabels)

	return attachments, labels
}

func clusterNodeJobSpec(
	stack *ucxsvc.Stack,
	hostname string,
	group string,
	internalId int,
	machine accapi.ProductReference,
	diskGb int,
	release K3sRelease,
	customUi ucxsvc.UcxCustomUiServiceInit,
	resources []orcapi.AppParameterValue,
) orcapi.JobSpecification {
	labels := map[string]string{
		k8sapi.LabelNodeGroup:                 group,
		k8sapi.LabelNodeAllocation:            strconv.Itoa(internalId),
		k8sapi.LabelK8sVersion:                release.Release,
		orcapi.ResourceLabelAntiAffinityGroup: clusterAntiAffinityGroup(stack, group),
	}

	resources, labels = clusterControlPlaneWiring(stack, group, internalId, customUi, resources, labels)

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

func clusterNodeAttachments(stack *ucxsvc.Stack, record *ClusterRecord, internalId int, ipAddress string) []orcapi.AppParameterValue {
	return []orcapi.AppParameterValue{
		orcapi.AppParameterValuePrivateNetwork(record.NetworkId, ipAddress),
		ucxsvc.StackSubtreeMount(stack, record.BundlePath, bundleMountPath, true),
		ucxsvc.StackSubtreeMount(stack, inputDirFor(internalId), inputMountPath, true),
		ucxsvc.StackSubtreeMount(stack, storageDir, storageMountPath, false),
	}
}

func clusterRecordNodeJob(record *ClusterRecord, internalId int, jobId string) {
	for i := range record.Nodes {
		if record.Nodes[i].InternalId == internalId {
			record.Nodes[i].JobId = jobId
			break
		}
	}
}

func clusterCreateNode(stack *ucxsvc.Stack, opts clusterNodeOptions) (bool, string) {
	opts.onFailure = func(stage clusterNodeProvisionStage, node ClusterNodeRecord, jobId string, cause string) string {
		switch stage {
		case clusterNodeStageIdentity:
			if clusterCreateAuthorityHeld(opts) {
				_ = ucxsvc.JobTerminate(stack, jobId)
				err := clusterReleaseReservation(opts, stack, opts.record, node.PrivateIpIdentifier, node.Hostname)
				if err != nil {
					return cause + " and could not save the cleanup state: " + err.Error()
				}
			}
			return cause
		case clusterNodeStagePersist:
			reason := cause + ". Job id " + jobId + " and private IP identifier " +
				node.PrivateIpIdentifier + " may need manual cleanup"
			_ = ClusterRecordMarkFailed(opts.stateClient, reason, func(record *ClusterRecord) {
				clusterRecordNodeJob(record, node.InternalId, jobId)
			})
			return ""
		case clusterNodeStageService:
			if clusterCreateAuthorityHeld(opts) {
				_ = ucxsvc.JobTerminate(stack, jobId)
				err := clusterReleaseReservation(opts, stack, opts.record, node.PrivateIpIdentifier, node.Hostname)
				if err != nil {
					return cause + " and could not save the cleanup state: " + err.Error()
				}
			}
			return cause
		case clusterNodeStageReserve:
			if node.PrivateIpIdentifier != "" {
				err := clusterReleaseReservation(opts, stack, opts.record, node.PrivateIpIdentifier, node.Hostname)
				if err != nil {
					return "could not reserve the node address and could not save the cleanup state: " + err.Error()
				}
			}
			return ""
		case clusterNodeStageRecord:
			if node.PrivateIpIdentifier != "" {
				err := clusterReleaseReservation(opts, stack, opts.record, node.PrivateIpIdentifier, node.Hostname)
				if err != nil {
					return "could not persist the node record and could not save the cleanup state: " + err.Error()
				}
			}
			return ""
		case clusterNodeStageJob:
			if node.PrivateIpIdentifier != "" {
				err := clusterReleaseReservation(opts, stack, opts.record, node.PrivateIpIdentifier, node.Hostname)
				if err != nil {
					return "could not create the node job and could not save the cleanup state: " + err.Error()
				}
			}
			return ""
		default:
			return cause
		}
	}

	return clusterProvisionNode(stack, opts)
}

func clusterProvisionNode(stack *ucxsvc.Stack, opts clusterNodeOptions) (bool, string) {
	ipAddress := NodeIpForInternalId(opts.internalId)
	hostname := ClusterNodeHostname(opts.group, opts.internalId)

	clusterEnsureDir(stack, storageDir)
	if opts.group == k8sapi.GroupControlPlane {
		clusterEnsureDir(stack, nodesDir)
		clusterEnsureDir(stack, backupsDir)
	}
	if !stack.Ok {
		return false, opts.onFailure(clusterNodeStagePrepare, ClusterNodeRecord{}, "", "could not prepare the node directories")
	}

	if !clusterWriteNodeInput(stack, opts.record, opts.release, clusterNodeInputSpec{
		group:       opts.group,
		internalId:  opts.internalId,
		firstServer: opts.group == k8sapi.GroupControlPlane && opts.internalId == 1,
		tokens:      opts.tokens,
	}) {
		return false, opts.onFailure(clusterNodeStagePrepare, ClusterNodeRecord{}, "", "could not prepare the node configuration")
	}

	if opts.writeTokens {
		inputDir := inputDirFor(opts.internalId)
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "agent-token.ca"), opts.tokens.AgentToken, 0600)
		if opts.group == k8sapi.GroupControlPlane {
			ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "server-token.ca"), opts.tokens.ServerToken, 0600)
		}
		if !stack.Ok {
			return false, opts.onFailure(clusterNodeStagePrepare, ClusterNodeRecord{}, "", "could not prepare the node configuration")
		}
	}

	node := ClusterNodeRecord{
		InternalId:     opts.internalId,
		Group:          opts.group,
		Hostname:       hostname,
		IpAddress:      ipAddress,
		Machine:        opts.machine,
		DiskGb:         opts.diskGb,
		DesiredVersion: opts.release.Release,
	}

	opts.record.NextInternalId = opts.internalId + 1
	if err := ClusterRecordReplace(opts.stateClient, opts.record); err != nil {
		return false, opts.onFailure(clusterNodeStageInternalId, node, "", "could not persist the node internal id: "+err.Error())
	}

	reservation, err := ucxsvc.PrivateNetworkIpReserveRetry(stack, opts.record.NetworkId, ipAddress, 30*time.Second)
	if err != nil {
		node.PrivateIpIdentifier = reservation.Id
		return false, opts.onFailure(clusterNodeStageReserve, node, "", "could not reserve the node address: "+err.Error())
	}
	node.PrivateIpIdentifier = reservation.Id

	opts.record.Nodes = append(opts.record.Nodes, node)
	if err := ClusterRecordReplace(opts.stateClient, opts.record); err != nil {
		return false, opts.onFailure(clusterNodeStageRecord, node, "", "could not persist the node record: "+err.Error())
	}

	attachments := clusterNodeAttachments(stack, opts.record, opts.internalId, ipAddress)

	job, err := clusterCreateNodeJob(stack, clusterNodeJobSpec(
		stack,
		hostname,
		opts.group,
		opts.internalId,
		opts.machine,
		opts.diskGb,
		opts.release,
		opts.customUi,
		attachments,
	))
	if err != nil {
		return false, opts.onFailure(clusterNodeStageJob, node, "", "could not create the node: "+err.Error())
	}

	node.JobId = job.Id
	clusterRecordNodeJob(opts.record, opts.internalId, job.Id)

	if !clusterWriteStackIdentity(stack, opts.internalId) {
		return false, opts.onFailure(
			clusterNodeStageIdentity,
			node,
			job.Id,
			"could not write the stack identity of node "+hostname,
		)
	}

	if err := ClusterRecordReplace(opts.stateClient, opts.record); err != nil {
		return false, opts.onFailure(
			clusterNodeStagePersist,
			node,
			job.Id,
			"could not persist the job id of node "+hostname,
		)
	}

	if opts.group == k8sapi.GroupControlPlane && opts.record.ServiceId != "" {
		if !ucxsvc.ServiceAddMembers(stack, opts.record.ServiceId, []string{job.Id}) {
			return false, opts.onFailure(
				clusterNodeStageService,
				node,
				job.Id,
				"could not add the node to the cluster service",
			)
		}
	}

	if opts.group == k8sapi.GroupControlPlane {
		if !clusterWriteControllerToken(stack, opts.session, job.Id, opts.internalId) {
			return false, opts.onFailure(
				clusterNodeStageCredential,
				node,
				job.Id,
				"could not prepare the credentials for cluster management",
			)
		}
	}

	return true, ""
}

type ClusterRecoveryNodeSpec struct {
	InternalId     int
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
	return ucxsvc.UcxInitCustomUiServiceAt(stack, customUiPort, "", managementDir, ManagementMountPath)
}

func ClusterRecoveryFirstServerInternalId(record *ClusterRecord) int {
	lowest := 0
	if record == nil {
		return lowest
	}

	for _, node := range record.Nodes {
		if node.Group != k8sapi.GroupControlPlane {
			continue
		}
		if lowest == 0 || node.InternalId < lowest {
			lowest = node.InternalId
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
		log.Fatal("k8s-app: the cluster record is nil")
	}
	if spec.Release.Release == "" {
		log.Fatal("k8s-app: the recovery Kubernetes version is empty")
	}
	if !InternalIdIsValid(spec.InternalId) {
		return fmt.Errorf("the internal id %d is not valid", spec.InternalId)
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
		if record.Nodes[i].InternalId == spec.InternalId {
			nodeIndex = i
			break
		}
	}
	if nodeIndex < 0 {
		log.Fatal("k8s-app: the cluster record has no node with internal id %d", spec.InternalId)
	}

	node := record.Nodes[nodeIndex]
	if node.Group != k8sapi.GroupControlPlane {
		log.Fatal("k8s-app: node %s is not a control-plane node", node.Hostname)
	}
	if node.PrivateIpIdentifier == "" {
		return fmt.Errorf("node %s has no address reservation in the cluster record", node.Hostname)
	}

	bundleTarget := BundlePathForRelease(spec.Release)
	if record.BundlePath != bundleTarget || record.K8sVersion != spec.Release.Release {
		writeBundle(stack, bundleTarget)
		if !stack.Ok {
			return errors.New("could not prepare the setup files for Kubernetes version " + spec.Release.Release)
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
		group:       k8sapi.GroupControlPlane,
		internalId:  spec.InternalId,
		firstServer: spec.FirstServer,
		tokens:      ClusterTokens{ServerToken: spec.ServerToken, AgentToken: spec.AgentToken},
		recovery:    recovery,
	}) {
		return errors.New("could not prepare the configuration for node " + node.Hostname)
	}

	ucxsvc.StackWriteFileEx(
		stack,
		filepath.Join(inputDirFor(spec.InternalId), nodeDecommissionedMarker),
		"",
		0600,
	)
	if !stack.Ok {
		return errors.New("could not clear the decommissioned marker of node " + node.Hostname)
	}

	attachments := clusterNodeAttachments(stack, record, spec.InternalId, node.IpAddress)
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
		k8sapi.GroupControlPlane,
		spec.InternalId,
		node.Machine,
		node.DiskGb,
		spec.Release,
		spec.CustomUi,
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

	if !clusterWriteStackIdentity(stack, spec.InternalId) {
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
	if err := ClusterRecordReplace(client, record); err != nil {
		_ = ucxsvc.JobTerminate(stack, job.Id)
		return fmt.Errorf("could not record the replacement job of node %s: %s", node.Hostname, err)
	}

	if !clusterWriteControllerToken(stack, spec.Session, job.Id, spec.InternalId) {
		return errors.New("could not prepare the cluster management credentials for node " + node.Hostname)
	}

	return nil
}

func clusterWriteControllerToken(stack *ucxsvc.Stack, session *ucx.Session, jobId string, internalId int) bool {
	if session == nil {
		return false
	}

	response, err := ucxapi.StackControlToken.Invoke(session, ucxapi.StackControlTokenRequest{JobId: jobId})
	if err != nil {
		return false
	}

	path := filepath.Join(inputDirFor(internalId), controllerRegistrationTokenFile)
	ucxsvc.StackWriteFileAtomicEx(stack, path, response.Token, 0600)

	return stack.Ok
}

func clusterWriteStackIdentity(stack *ucxsvc.Stack, internalId int) bool {
	ucxsvc.StackWriteFileAtomicEx(
		stack,
		filepath.Join(inputDirFor(internalId), "stack-identity.txt"),
		stack.InstanceId,
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

func clusterReleaseReservation(opts clusterNodeOptions, stack *ucxsvc.Stack, record *ClusterRecord, privateIpIdentifier string, hostname string) error {
	if !clusterCreateAuthorityHeld(opts) {
		return nil
	}

	err := ucxsvc.PrivateNetworkIpDelete(stack, privateIpIdentifier)
	if err != nil {
		reason := "could not release the reservation of node " + hostname +
			". Private IP identifier " + privateIpIdentifier + " is pending cluster cleanup"
		return ClusterRecordMarkFailed(opts.stateClient, reason, func(record *ClusterRecord) {
			record.PendingCleanup = append(record.PendingCleanup, ClusterPendingCleanup{
				PrivateIpIdentifier: privateIpIdentifier,
				Hostname:            hostname,
			})
		})
	}

	return ClusterRecordReplace(opts.stateClient, record)
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
	group       string
	internalId  int
	firstServer bool
	tokens      ClusterTokens
	recovery    *clusterRecoveryInput
}

type clusterRecoveryInput struct {
	backupId       string
	snapshotSha256 string
}

func clusterWriteNodeInput(stack *ucxsvc.Stack, record *ClusterRecord, release K3sRelease, spec clusterNodeInputSpec) bool {
	ipAddress := NodeIpForInternalId(spec.internalId)

	serverUrl := fmt.Sprintf("https://%s:%d", record.ServiceDnsName, ApiPort)

	nodeJson := map[string]any{
		"nodeGroup":   spec.group,
		"firstServer": spec.firstServer,
		"hostname":    ClusterNodeHostname(spec.group, spec.internalId),
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

	nodeData := util.MustJsonMarshalIndent(nodeJson, "", "  ")

	inputDir := inputDirFor(spec.internalId)
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

func inputDirFor(internalId int) string {
	return filepath.Join(nodesDir, strconv.Itoa(internalId), "input")
}

func NodeDirForInternalId(internalId int) string {
	return filepath.Join(nodesMountPath, strconv.Itoa(internalId))
}

func ClusterNodeHostname(group string, internalId int) string {
	return fmt.Sprintf("%s-%v", group, internalId)
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

func ClusterPoolRecordByName(record ClusterRecord, name string) (ClusterPoolRecord, bool) {
	for _, pool := range record.Pools {
		if pool.Name == name {
			return pool, true
		}
	}
	return ClusterPoolRecord{}, false
}

func NewUid() string {
	return util.SecureToken()
}

func clusterAntiAffinityGroup(stack *ucxsvc.Stack, group string) string {
	return stack.InstanceId + "-" + group
}
