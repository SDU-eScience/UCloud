package shared

import (
	"embed"
	"encoding/json"
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

const (
	StackGroupingLabel  = "ucloud.dk/k8s-node-group"
	NodeAllocationLabel = "ucloud.dk/k8s-node-id"
	K8sVersionLabel     = "ucloud.dk/k8s-version"
)

const (
	ClusterRecordPath               = "management/cluster.json"
	KubeconfigTemplatePath          = "management/kubeconfig.tpl"
	KubernetesConfigurationFileName = "management/kubeconfig"
	KubernetesTokenFileName         = "management/kube-api-token"
)

const (
	managementDir       = "management"
	managementMountPath = "/etc/ucloud-k8s/management"
	nodesDir            = "nodes"
	nodesMountPath      = "/etc/ucloud-k8s/nodes"
	storageDir          = "k3s-storage"
	storageMountPath    = "/etc/ucloud-stack/k3s/storage"
	bundleMountPath     = "/etc/ucloud-k8s/bundle"
	inputMountPath      = "/etc/ucloud-k8s/input"
	launcherPath        = bundleMountPath + "/launcher.sh"

	ManagementMountPath = managementMountPath
)

const (
	ApiPort      = 6443
	HeadlampPort = 30500
	customUiPort = 43102
)

const clusterRecordSchemaRevision = 2

func Script(name string) string {
	data, err := scriptFiles.ReadFile("scripts/" + name)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}

const (
	clusterRecordPhaseProvisioning = "provisioning"
	clusterRecordPhaseCreated      = "created"
	clusterRecordPhaseCleanup      = "cleanup-pending"
	clusterRecordPhaseError        = "error"
)

const ScriptBundleRevision = 7

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
	Ports               []int
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
	MachineProvider  string                  `json:"machineProvider"`
	Subnets          []string                `json:"subnets"`
	Ports            []int                   `json:"ports"`
	BundlePath       string                  `json:"bundlePath"`
	NextAllocationId int                     `json:"nextAllocationId"`
	Phase            string                  `json:"phase"`
	FailureReason    string                  `json:"failureReason,omitempty"`
	PendingCleanup   []ClusterPendingCleanup `json:"pendingCleanup,omitempty"`
	Pools            []ClusterPoolRecord     `json:"pools,omitempty"`
	Nodes            []ClusterNodeRecord     `json:"nodes"`
}

type ClusterPendingCleanup struct {
	ReservationId string `json:"reservationId"`
	JobId         string `json:"jobId"`
	Hostname      string `json:"hostname"`
	IpAddress     string `json:"ipAddress"`
}

func ClusterRecordWrite(stack *ucxsvc.Stack, record *ClusterRecord) {
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		log.Fatal(err)
	}
	ucxsvc.StackWriteFileAtomic(stack, ClusterRecordPath, string(data))
}

func ClusterRecordMarkFailed(stack *ucxsvc.Stack, record *ClusterRecord, reason string) {
	record.Phase = clusterRecordPhaseError
	record.FailureReason = reason
	ClusterRecordWrite(stack, record)
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

	totalNodes := 0
	for _, pool := range planPools(spec) {
		totalNodes += pool.nodes
	}
	if totalNodes > ClusterMaxNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster supports at most %d nodes, but %d were requested", ClusterMaxNodes, totalNodes))
		return &ucxsvc.Stack{}, false
	}

	stack, ok := ucxsvc.StackCreate(app, stackId, "Kubernetes")
	if !ok {
		return stack, false
	}

	stopHeartbeat, heartbeatFailed := ucxsvc.StackStartHeartbeat(stack)
	defer func() {
		_ = stopHeartbeat()
	}()

	session := *app.Session()
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
	apiLinkDomain := fmt.Sprintf("%s%s-k8s%s", linkDomain.Prefix, stackIdLower, linkDomain.Suffix)

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

	record := &ClusterRecord{
		SchemaRevision:   clusterRecordSchemaRevision,
		StackId:          stackId,
		K8sVersion:       release.Release,
		NetworkId:        network.Id,
		MachineProvider:  spec.ControlPlaneMachine.Provider,
		Subnets:          []string{ClusterVmCidr, ClusterPodCidr, ClusterServiceCidr},
		Ports:            spec.Ports,
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

	ClusterRecordWrite(stack, record)
	if !stack.Ok {
		return stack, false
	}

	if heartbeatFailed() {
		ucxsvc.UiSendFailure(app, "The stack lease was lost during creation, the failed resources are cleaned up automatically.")
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

	links := []orcapi.AppParameterValue{
		ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-api", stackId), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(ApiPort),
			TLS:  true,
		}),
		ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-dashboard", stackId), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(HeadlampPort),
		}),
	}
	for _, port := range spec.Ports {
		links = append(links, ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-%d", stackId, port), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(port),
		}))
	}
	if !stack.Ok {
		return stack, false
	}

	customUi := ucxsvc.UcxInitCustomUiServiceAt(stack, customUiPort, "", managementDir, managementMountPath)
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
				record:       record,
				release:      release,
				tokens:       tokens,
				group:        pool.name,
				machine:      pool.machine,
				diskGb:       pool.diskGb,
				allocationId: allocationId,
				links:        links,
				customUi:     customUi,
			})
			if !created {
				clusterCreateCleanupFailed(stack, record, failReason)
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

	record.Phase = clusterRecordPhaseCreated
	ClusterRecordWrite(stack, record)
	if !stack.Ok {
		return stack, false
	}

	if _, err := ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId}); err != nil {
		return stack, true
	}

	return stack, true
}

func clusterCreateCleanupFailed(stack *ucxsvc.Stack, record *ClusterRecord, reason string) {
	if reason != "" {
		record.FailureReason = reason
	}
	record.Phase = clusterRecordPhaseError
	ClusterRecordWrite(stack, record)
}

type clusterNodeOptions struct {
	record       *ClusterRecord
	release      K3sRelease
	tokens       ClusterTokens
	group        string
	machine      accapi.ProductReference
	diskGb       int
	allocationId int
	links        []orcapi.AppParameterValue
	customUi     ucxsvc.UcxCustomUiServiceInit
}

func clusterCreateNode(stack *ucxsvc.Stack, opts clusterNodeOptions) (bool, string) {
	ipAddress := NodeIpForAllocation(opts.allocationId)
	hostname := ClusterNodeHostname(opts.group, opts.allocationId)
	firstServer := opts.group == GroupControlPlane && opts.allocationId == 1

	clusterEnsureDir(stack, storageDir)
	if firstServer {
		clusterEnsureDir(stack, nodesDir)
	}
	if !stack.Ok {
		return false, "could not prepare the node directories"
	}

	if !clusterWriteNodeInput(stack, opts.record, opts.release, opts.tokens, opts) {
		return false, "could not write the input files of the node"
	}

	record := opts.record
	record.NextAllocationId = opts.allocationId + 1
	ClusterRecordWrite(stack, record)
	if !stack.Ok {
		return false, "could not persist the node allocation"
	}

	reservation, err := ucxsvc.PrivateNetworkIpReserveRetry(stack, record.NetworkId, ipAddress, 30*time.Second)
	if err != nil {
		if reservation.Id != "" {
			clusterReleaseReservation(stack, record, reservation.Id, hostname)
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
	ClusterRecordWrite(stack, record)
	if !stack.Ok {
		clusterReleaseReservation(stack, record, reservation.Id, hostname)
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

	initScript := ""
	if firstServer {
		attachments = append(attachments, opts.links...)
		attachments = append(attachments,
			ucxsvc.StackSubtreeMount(stack, managementDir, managementMountPath, false),
			ucxsvc.StackSubtreeMount(stack, nodesDir, nodesMountPath, false),
		)

		labels[orcapi.ResourceLabelServiceForwardTcp] = marshalPorts(append([]int{ApiPort, HeadlampPort}, opts.record.Ports...))
		labels = util.MapMerge(labels, opts.customUi.Labels)

		initScript = Script("launcher.sh") + "\n" + opts.customUi.InitScript
		initLabels := ucxsvc.StackWriteInitScriptAt(stack, initScript, inputDirFor(opts.allocationId), inputMountPath)
		labels = util.MapMerge(labels, initLabels)
	} else {
		labels[orcapi.ResourceLabelInitScript] = launcherPath
	}

	job, err := clusterCreateNodeJob(stack, orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: opts.machine,
			Labels:  labels,
		},
		Application: ucxsvc.VmImageUbuntu26_04,
		Name:        hostname,
		Hostname:    util.OptValue[string](hostname),
		Parameters: map[string]orcapi.AppParameterValue{
			"diskSize": orcapi.AppParameterValueInteger(int64(opts.diskGb)),
		},
		Replicas:  1,
		Resources: attachments,
	})
	if err != nil {
		clusterReleaseReservation(stack, record, reservation.Id, hostname)
		return false, ""
	}

	for i := range opts.record.Nodes {
		if opts.record.Nodes[i].AllocationId == opts.allocationId {
			opts.record.Nodes[i].JobId = job.Id
			break
		}
	}

	ClusterRecordWrite(stack, record)
	if !stack.Ok {
		ClusterRecordMarkFailed(stack, record, "could not persist the job id of node "+hostname+
			"; job id "+job.Id+" and reservation id "+reservation.Id+" may need manual cleanup")
		return false, ""
	}

	return true, ""
}

func clusterCreateNodeJob(stack *ucxsvc.Stack, spec orcapi.JobSpecification) (orcapi.Job, error) {
	deadline := time.Now().Add(30 * time.Second)

	for {
		job, err := ucxsvc.JobCreateJob(stack, spec)
		if err == nil {
			return job, nil
		}

		if !strings.Contains(err.Error(), "is not ready yet") || time.Now().After(deadline) {
			return orcapi.Job{}, err
		}

		time.Sleep(2 * time.Second)
	}
}

func clusterReleaseReservation(stack *ucxsvc.Stack, record *ClusterRecord, reservationId string, hostname string) {
	err := ucxsvc.PrivateNetworkIpDelete(stack, reservationId)
	if err != nil {
		record.PendingCleanup = append(record.PendingCleanup, ClusterPendingCleanup{
			ReservationId: reservationId,
			Hostname:      hostname,
		})
		ClusterRecordMarkFailed(stack, record, "could not release the reservation of node "+hostname+
			"; reservation id "+reservationId+" needs manual cleanup")
	} else {
		ClusterRecordWrite(stack, record)
	}
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

func clusterWriteNodeInput(stack *ucxsvc.Stack, record *ClusterRecord, release K3sRelease, tokens ClusterTokens, opts clusterNodeOptions) bool {
	firstServer := opts.group == GroupControlPlane && opts.allocationId == 1
	ipAddress := NodeIpForAllocation(opts.allocationId)

	nodeJson := map[string]any{
		"role":        opts.group,
		"firstServer": firstServer,
		"hostname":    ClusterNodeHostname(opts.group, opts.allocationId),
		"ipAddress":   ipAddress,
		"serverUrl":   fmt.Sprintf("https://%s:%d", NodeIpForAllocation(1), ApiPort),
		"k8sVersion":  release.Release,
		"sha256Amd64": release.Sha256Amd64,
		"sha256Arm64": release.Sha256Arm64,
		"clusterCidr": ClusterPodCidr,
		"serviceCidr": ClusterServiceCidr,
	}

	nodeData, err := json.MarshalIndent(nodeJson, "", "  ")
	if err != nil {
		log.Fatal(err)
	}

	inputDir := inputDirFor(opts.allocationId)
	ucxsvc.StackWriteFile(stack, filepath.Join(inputDir, "node.json"), string(nodeData))

	if firstServer {
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "server-token"), tokens.ServerToken, 0600)
		ucxsvc.StackWriteFileEx(stack, filepath.Join(inputDir, "agent-token"), tokens.AgentToken, 0600)
	}

	return stack.Ok
}

func inputDirFor(allocationId int) string {
	return filepath.Join(nodesDir, strconv.Itoa(allocationId), "input")
}

func ClusterNodeHostname(group string, allocationId int) string {
	return fmt.Sprintf("%s-%v", group, allocationId)
}

func marshalPorts(ports []int) string {
	data, err := json.Marshal(ports)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}
