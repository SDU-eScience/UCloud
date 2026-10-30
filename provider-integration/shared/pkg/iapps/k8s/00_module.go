package k8sapi

import (
	"fmt"
	"regexp"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const (
	AppName = "kubernetes"

	StackType = "Kubernetes"

	GroupControlPlane = "control-plane"

	LabelNodeGroup      = "ucloud.dk/k8s-node-group"
	LabelNodeAllocation = "ucloud.dk/k8s-node-id"
	LabelK8sVersion     = "ucloud.dk/k8s-version"

	ClusterNameMaxLen = 30
	PoolNameMaxLen    = 30
	ClusterMaxNodes   = 256
	MaxPoolNodes      = 250

	MinDiskGb = 10
	MaxDiskGb = 1024

	ControlPlaneMinNodes = 1
	ControlPlaneMaxNodes = 7

	DefaultControlPlaneNodes  = 1
	DefaultControlPlaneDiskGb = 50
	DefaultWorkerNodes        = 1
	DefaultWorkerDiskGb       = 50

	MaintenanceTimeoutDefault = 600
	MaintenanceTimeoutMin     = 30
	MaintenanceTimeoutMax     = 3600

	AppModeCreator   = "creator"
	AppModeDashboard = "dashboard"
	AppModeRecovery  = "recovery"

	NodeStateReady    = "Ready"
	NodeStateNotReady = "NotReady"
	NodeStateUnknown  = "Unknown"

	ProvisioningStatusProvisioning        = "Provisioning"
	ProvisioningStatusWaitingForResources = "Waiting for resources"
	ProvisioningStatusInitializing        = "Initializing"
	ProvisioningStatusPoweredOff          = "Powered off"
	ProvisioningStatusFailed              = "Failed"
	ProvisioningStatusReady               = "ready"

	ClusterPhaseProvisioning = "provisioning"
	ClusterPhaseCleanup      = "cleanup-pending"
	ClusterPhaseError        = "error"
	ClusterPhaseCreated      = "created"
	ClusterPhaseRecovering   = "recovering"

	MaintenancePhasePending   = "Pending"
	MaintenancePhaseRunning   = "Running"
	MaintenancePhaseCompleted = "Completed"
	MaintenancePhaseFailed    = "Failed"
	MaintenancePhaseBlocked   = "Blocked"
	MaintenancePhaseCancelled = "Cancelled"

	MaintenanceTaskK8sUpgrade = "k8s-upgrade"
	MaintenanceTaskOsUpdate   = "os-update"
	MaintenanceTaskCordon     = "cordon"
	MaintenanceTaskUncordon   = "uncordon"
	MaintenanceTaskRemove     = "remove"

	MaintenanceTriggerWindow = "window"
	MaintenanceTriggerManual = "manual"

	MaintenanceRunPending   = "Pending"
	MaintenanceRunPreflight = "Preflight"
	MaintenanceRunBackup    = "Backup"
	MaintenanceRunRunning   = "Running"
	MaintenanceRunBlocked   = "Blocked"
	MaintenanceRunCompleted = "Completed"
	MaintenanceRunFailed    = "Failed"
	MaintenanceRunCancelled = "Cancelled"

	MaintenancePreparationNone   = "none"
	MaintenancePreparationCordon = "cordon"
	MaintenancePreparationDrain  = "drain"

	BackupRequestPending   = "pending"
	BackupRequestRunning   = "running"
	BackupRequestCompleted = "completed"
	BackupRequestFailed    = "failed"

	BackupAttemptSuccess = "success"
	BackupAttemptFailed  = "failed"

	RecoveryPhaseActive = "active"
	RecoveryPhaseClosed = "closed"

	RecoveryStageStop     = "stop"
	RecoveryStageRecreate = "recreate"
	RecoveryStageRestore  = "restore"
	RecoveryStageVerify   = "verify"

	RecoveryCredentialsOk        = "ok"
	RecoveryCredentialsMismatch  = "mismatch"
	RecoveryCredentialsUnchecked = "unchecked"

	RecoveryActionStatus = "status"
	RecoveryActionStart  = "start"
)

func ProvisioningStatusFromJobState(state orcapi.JobState) string {
	switch state {
	case orcapi.JobStateInQueue:
		return ProvisioningStatusWaitingForResources
	case orcapi.JobStateRunning:
		return ProvisioningStatusInitializing
	case orcapi.JobStateSuspended:
		return ProvisioningStatusPoweredOff
	}

	if state.IsFinal() {
		return ProvisioningStatusFailed
	}
	return ""
}

type PingRequest struct {
	Message string
}

type PingResponse struct {
	AppName    string
	AppVersion string
	Mode       string
	Headless   bool
	Message    string
}

var Ping = ucx.Rpc[PingRequest, PingResponse]{
	CallName: "k8s.ping",
}

type DeployRequest struct {
	Name         string           `json:"name"`
	Provider     string           `json:"provider"`
	K8sVersion   string           `json:"k8sVersion"`
	ControlPlane ControlPlaneSpec `json:"controlPlane"`
	WorkerPools  []PoolSpec       `json:"workerPools"`
}

type ControlPlaneSpec struct {
	Machine accapi.ProductReference `json:"machine"`
	Nodes   int                     `json:"nodes"`
	Disk    int                     `json:"disk"`
}

type PoolSpec struct {
	Name    string                  `json:"name"`
	Machine accapi.ProductReference `json:"machine"`
	Nodes   int                     `json:"nodes"`
	Disk    int                     `json:"disk"`
}

type DeployResponse struct {
	Name string `json:"name"`
	Ok   bool   `json:"ok"`
}

var Deploy = ucx.Rpc[DeployRequest, DeployResponse]{
	CallName: "k8s.deploy",
}

var groupNameRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)

func ValidGroupName(name string) bool {
	return groupNameRe.MatchString(name)
}

func NormalizeDeployRequest(request DeployRequest) DeployRequest {
	request.Name = strings.TrimSpace(request.Name)
	request.Provider = strings.TrimSpace(request.Provider)
	request.K8sVersion = strings.TrimSpace(request.K8sVersion)

	for i := range request.WorkerPools {
		request.WorkerPools[i].Name = strings.TrimSpace(request.WorkerPools[i].Name)
	}

	return request
}

func ValidateClusterName(name string) error {
	name = strings.TrimSpace(name)
	if name == "" {
		return fmt.Errorf("the cluster name must not be empty")
	}
	if len(name) > ClusterNameMaxLen {
		return fmt.Errorf("the cluster name must be at most %d characters", ClusterNameMaxLen)
	}
	if !ValidGroupName(name) {
		return fmt.Errorf("the cluster name may only contain a-z, 0-9 and dashes: %s", termio.QuoteName(name))
	}
	if strings.HasPrefix(name, GroupControlPlane) {
		return fmt.Errorf("the cluster name must not start with the reserved name: %s", termio.QuoteName(GroupControlPlane))
	}
	return nil
}

func ValidateDeployRequest(request DeployRequest) error {
	err := ValidateClusterName(request.Name)
	if err != nil {
		return err
	}

	provider := strings.TrimSpace(request.Provider)
	if provider == "" {
		return fmt.Errorf("a service provider is required")
	}

	controlPlane := request.ControlPlane
	if controlPlane.Machine.Id == "" {
		return fmt.Errorf("select a machine type for the control plane")
	}
	if controlPlane.Machine.Provider != provider {
		return fmt.Errorf("the control-plane machine type must come from the selected service provider (%s)", termio.QuoteName(provider))
	}
	switch controlPlane.Nodes {
	case ControlPlaneMinNodes, 3, 5, ControlPlaneMaxNodes:
	default:
		return fmt.Errorf(
			"the control-plane node count must be an odd number from %d to %d",
			ControlPlaneMinNodes,
			ControlPlaneMaxNodes,
		)
	}
	if controlPlane.Disk < MinDiskGb {
		return fmt.Errorf("the control plane needs at least %d GB of disk", MinDiskGb)
	}
	if controlPlane.Disk > MaxDiskGb {
		return fmt.Errorf("the control plane supports at most %d GB of disk", MaxDiskGb)
	}

	totalNodes := controlPlane.Nodes
	seen := make(map[string]bool, len(request.WorkerPools))

	for i, pool := range request.WorkerPools {
		poolName := strings.TrimSpace(pool.Name)
		if poolName == "" {
			return fmt.Errorf("worker pool %d must have a name", i+1)
		}
		if len(poolName) > PoolNameMaxLen {
			return fmt.Errorf("worker pool names must be at most %d characters: %s", PoolNameMaxLen, termio.QuoteName(poolName))
		}
		if !ValidGroupName(poolName) {
			return fmt.Errorf("worker pool names may only contain a-z, 0-9 and dashes: %s", termio.QuoteName(poolName))
		}
		if poolName == GroupControlPlane {
			return fmt.Errorf("the name is reserved for the control plane: %s", termio.QuoteName(poolName))
		}
		if seen[poolName] {
			return fmt.Errorf("worker pool names must be unique: %s", termio.QuoteName(poolName))
		}
		seen[poolName] = true

		if pool.Machine.Id == "" {
			return fmt.Errorf("select a machine type for worker pool %s", termio.QuoteName(poolName))
		}
		if pool.Machine.Provider != provider {
			return fmt.Errorf(
				"the machine for worker pool %s must come from the selected service provider (%s)",
				termio.QuoteName(poolName),
				termio.QuoteName(provider),
			)
		}
		if pool.Nodes < 1 {
			return fmt.Errorf("worker pool %s needs at least one node", termio.QuoteName(poolName))
		}
		if pool.Nodes > MaxPoolNodes {
			return fmt.Errorf("worker pool %s supports at most %d nodes", termio.QuoteName(poolName), MaxPoolNodes)
		}
		if pool.Disk < MinDiskGb {
			return fmt.Errorf("worker pool %s needs at least %d GB of disk", termio.QuoteName(poolName), MinDiskGb)
		}
		if pool.Disk > MaxDiskGb {
			return fmt.Errorf("worker pool %s supports at most %d GB of disk", termio.QuoteName(poolName), MaxDiskGb)
		}

		totalNodes += pool.Nodes
	}

	if totalNodes > ClusterMaxNodes {
		return fmt.Errorf("the cluster supports at most %d nodes, but %d were requested", ClusterMaxNodes, totalNodes)
	}

	return nil
}

type Release struct {
	Version string `json:"version"`
	EolAt   int64  `json:"eolAt,omitempty"`
	Default bool   `json:"default"`
}

type ReleasesResponse struct {
	Releases []Release `json:"releases"`
}

var Releases = ucx.Rpc[util.Empty, ReleasesResponse]{
	CallName: "k8s.releases",
}

type StatusPool struct {
	Name         string                  `json:"name"`
	Machine      accapi.ProductReference `json:"machine"`
	Disk         int                     `json:"disk"`
	Nodes        int                     `json:"nodes"`
	ControlPlane bool                    `json:"controlPlane,omitempty"`
}

type StatusNode struct {
	Hostname       string                  `json:"hostname"`
	Group          string                  `json:"group"`
	AllocationId   int                     `json:"allocationId,omitempty"`
	IpAddress      string                  `json:"ipAddress"`
	JobId          string                  `json:"jobId"`
	Machine        accapi.ProductReference `json:"machine"`
	Disk           int                     `json:"disk"`
	DesiredVersion string                  `json:"desiredVersion,omitempty"`
	Version        string                  `json:"version,omitempty"`
	State          string                  `json:"state,omitempty"`
}

type MaintenanceOperation struct {
	Uid      string `json:"uid,omitempty"`
	NodeName string `json:"nodeName"`
	Kind     string `json:"kind"`
	Phase    string `json:"phase"`
	Error    string `json:"error,omitempty"`
}

type StatusBackup struct {
	StateFound            bool   `json:"stateFound"`
	RequestFound          bool   `json:"requestFound"`
	ClaimUid              string `json:"claimUid,omitempty"`
	ClaimRequestUid       string `json:"claimRequestUid,omitempty"`
	RequestUid            string `json:"requestUid,omitempty"`
	RequestPhase          string `json:"requestPhase,omitempty"`
	RequestError          string `json:"requestError,omitempty"`
	LastAttemptPhase      string `json:"lastAttemptPhase,omitempty"`
	LastAttemptError      string `json:"lastAttemptError,omitempty"`
	LastAttemptFinishedAt int64  `json:"lastAttemptFinishedAt,omitempty"`
	LastSuccessFinishedAt int64  `json:"lastSuccessFinishedAt,omitempty"`
	NextDueAt             int64  `json:"nextDueAt,omitempty"`
	Retained              int    `json:"retained"`
}

type StatusResponse struct {
	Name                string                  `json:"name"`
	Phase               string                  `json:"phase"`
	FailureReason       string                  `json:"failureReason,omitempty"`
	K8sVersion          string                  `json:"k8sVersion"`
	MachineProvider     string                  `json:"machineProvider"`
	Pools               []StatusPool            `json:"pools"`
	Nodes               []StatusNode            `json:"nodes"`
	NodeHealthKnown     bool                    `json:"nodeHealthKnown"`
	NodeTotal           int                     `json:"nodeTotal"`
	NodeReady           int                     `json:"nodeReady"`
	NodeNotReady        int                     `json:"nodeNotReady"`
	NodeUnknown         int                     `json:"nodeUnknown"`
	ControlPlaneVersion string                  `json:"controlPlaneVersion,omitempty"`
	MaintenanceKnown    bool                    `json:"maintenanceKnown"`
	MaintenanceError    string                  `json:"maintenanceError,omitempty"`
	Maintenance         []MaintenanceOperation  `json:"maintenance"`
	MaintenanceRuns     []MaintenanceRunSummary `json:"maintenanceRuns"`
	Backup              *StatusBackup           `json:"backup,omitempty"`
	BackupError         string                  `json:"backupError,omitempty"`
}

var Status = ucx.Rpc[util.Empty, StatusResponse]{
	CallName: "k8s.status",
}

type ProvisioningNodeStatus struct {
	Hostname string `json:"hostname"`
	Group    string `json:"group"`
	JobId    string `json:"jobId,omitempty"`
	Status   string `json:"status"`
	State    string `json:"state,omitempty"`
}

type ProvisioningStatusResponse struct {
	Phase           string                   `json:"phase"`
	FailureReason   string                   `json:"failureReason,omitempty"`
	NodeHealthKnown bool                     `json:"nodeHealthKnown"`
	AllReady        bool                     `json:"allReady"`
	Nodes           []ProvisioningNodeStatus `json:"nodes"`
}

var ProvisioningStatus = ucx.Rpc[util.Empty, ProvisioningStatusResponse]{
	CallName: "k8s.provisioningStatus",
}

type ScaleOutRequest struct {
	Pool  string `json:"pool"`
	Nodes int    `json:"nodes"`
	Disk  int    `json:"disk"`
}

type ScaleOutResponse struct {
	Pool   string `json:"pool"`
	Nodes  int    `json:"nodes"`
	Ok     bool   `json:"ok"`
	RunUid string `json:"runUid,omitempty"`
}

var ScaleOut = ucx.Rpc[ScaleOutRequest, ScaleOutResponse]{
	CallName: "k8s.scaleOut",
}

type AddPoolRequest struct {
	Name    string                  `json:"name"`
	Machine accapi.ProductReference `json:"machine"`
	Nodes   int                     `json:"nodes"`
	Disk    int                     `json:"disk"`
}

type AddPoolResponse struct {
	Name  string `json:"name"`
	Nodes int    `json:"nodes"`
	Ok    bool   `json:"ok"`
}

var AddPool = ucx.Rpc[AddPoolRequest, AddPoolResponse]{
	CallName: "k8s.addPool",
}

type MaintenanceTarget struct {
	All   bool     `json:"all,omitempty"`
	Pools []string `json:"pools,omitempty"`
	Nodes []string `json:"nodes,omitempty"`
}

type MaintenanceTaskRequest struct {
	Kind                    string `json:"kind"`
	TargetRelease           string `json:"targetRelease,omitempty"`
	Preparation             string `json:"preparation,omitempty"`
	TimeoutSeconds          int    `json:"timeoutSeconds,omitempty"`
	DeleteVolatilePods      bool   `json:"deleteVolatilePods,omitempty"`
	BypassDisruptionBudgets bool   `json:"bypassDisruptionBudgets,omitempty"`
	ForceDelete             bool   `json:"forceDelete,omitempty"`
}

type MaintenanceRunRequest struct {
	Tasks               []MaintenanceTaskRequest `json:"tasks"`
	Target              MaintenanceTarget        `json:"target"`
	MaxParallelPerGroup int                      `json:"maxParallelPerGroup,omitempty"`
	RequestedBy         string                   `json:"requestedBy,omitempty"`
}

type MaintenanceRunResponse struct {
	Uid string `json:"uid"`
}

var MaintenanceRun = ucx.Rpc[MaintenanceRunRequest, MaintenanceRunResponse]{
	CallName: "k8s.maintenanceRun",
}

type MaintenanceCancelRequest struct {
	RunUid string `json:"runUid,omitempty"`
	Node   string `json:"node,omitempty"`
}

type MaintenanceRetryRequest struct {
	RunUid string `json:"runUid,omitempty"`
	Node   string `json:"node,omitempty"`
}

var MaintenanceCancel = ucx.Rpc[MaintenanceCancelRequest, MaintenanceRunResponse]{
	CallName: "k8s.maintenanceCancel",
}

var MaintenanceRetry = ucx.Rpc[MaintenanceRetryRequest, MaintenanceRunResponse]{
	CallName: "k8s.maintenanceRetry",
}

type MaintenancePolicyInfo struct {
	K8sEnabled          bool   `json:"k8sEnabled"`
	OsEnabled           bool   `json:"osEnabled"`
	WindowDay           string `json:"windowDay"`
	WindowStartHour     int    `json:"windowStartHour"`
	WindowDurationHours int    `json:"windowDurationHours"`
	MaxBackupAgeSeconds int64  `json:"maxBackupAgeSeconds,omitempty"`
}

type MaintenanceScheduleInfo struct {
	LastWindowStart int64 `json:"lastWindowStart,omitempty"`
	NextWindowAt    int64 `json:"nextWindowAt,omitempty"`
	NextWindowEnd   int64 `json:"nextWindowEnd,omitempty"`
}

type MaintenanceCheckInfo struct {
	Name   string `json:"name"`
	Ok     bool   `json:"ok"`
	Detail string `json:"detail,omitempty"`
}

type MaintenanceItemInfo struct {
	Node         string `json:"node"`
	Group        string `json:"group,omitempty"`
	Phase        string `json:"phase"`
	OperationUid string `json:"operationUid,omitempty"`
	Error        string `json:"error,omitempty"`
	Percent      int    `json:"percent,omitempty"`
	Stage        string `json:"stage,omitempty"`
}

type MaintenanceTaskInfo struct {
	Kind          string                `json:"kind"`
	Phase         string                `json:"phase"`
	TargetRelease string                `json:"targetRelease,omitempty"`
	Items         []MaintenanceItemInfo `json:"items"`
}

type MaintenanceRunSummary struct {
	Uid             string `json:"uid"`
	Kind            string `json:"kind"`
	Trigger         string `json:"trigger"`
	RequestedBy     string `json:"requestedBy,omitempty"`
	RequestedAt     int64  `json:"requestedAt,omitempty"`
	Phase           string `json:"phase"`
	Message         string `json:"message,omitempty"`
	Error           string `json:"error,omitempty"`
	CancelRequested bool   `json:"cancelRequested,omitempty"`
	StartedAt       int64  `json:"startedAt,omitempty"`
	FinishedAt      int64  `json:"finishedAt,omitempty"`
}

type MaintenanceRunInfo struct {
	MaintenanceRunSummary
	Tasks  []MaintenanceTaskInfo  `json:"tasks"`
	Checks []MaintenanceCheckInfo `json:"checks"`
}

type MaintenanceStatusResponse struct {
	PolicyFound     bool                    `json:"policyFound"`
	Policy          MaintenancePolicyInfo   `json:"policy"`
	Schedule        MaintenanceScheduleInfo `json:"schedule"`
	ActiveRuns      []MaintenanceRunInfo    `json:"activeRuns"`
	RecentRuns      []MaintenanceRunInfo    `json:"recentRuns,omitempty"`
	LastRun         *MaintenanceRunInfo     `json:"lastRun,omitempty"`
	Operations      []MaintenanceOperation  `json:"operations"`
	OperationsError string                  `json:"operationsError,omitempty"`
	Backup          *StatusBackup           `json:"backup,omitempty"`
	BackupError     string                  `json:"backupError,omitempty"`
}

var MaintenanceStatus = ucx.Rpc[util.Empty, MaintenanceStatusResponse]{
	CallName: "k8s.maintenanceStatus",
}

type MaintenancePolicyUpdateRequest struct {
	K8sEnabled      util.Option[bool] `json:"k8sEnabled"`
	OsEnabled       util.Option[bool] `json:"osEnabled"`
	WindowDay       string            `json:"windowDay,omitempty"`
	WindowStartHour util.Option[int]  `json:"windowStartHour"`
}

var MaintenancePolicyUpdate = ucx.Rpc[MaintenancePolicyUpdateRequest, MaintenanceStatusResponse]{
	CallName: "k8s.maintenancePolicyUpdate",
}

type BackupResponse struct {
	Uid   string `json:"uid"`
	Phase string `json:"phase"`
}

var MaintenanceBackup = ucx.Rpc[util.Empty, BackupResponse]{
	CallName: "k8s.maintenanceBackup",
}

type RecoveryBackupInfo struct {
	Id            string `json:"id"`
	Release       string `json:"release,omitempty"`
	CreatedAt     int64  `json:"createdAt,omitempty"`
	SizeBytes     int64  `json:"sizeBytes,omitempty"`
	CreatedByNode string `json:"createdByNode,omitempty"`
}

type RecoveryCheckInfo struct {
	Name   string `json:"name"`
	Status string `json:"status"`
	Detail string `json:"detail,omitempty"`
	Ok     bool   `json:"ok"`
}

type RecoveryStatusResponse struct {
	Supported         bool                 `json:"supported"`
	Active            bool                 `json:"active"`
	RecoveryFound     bool                 `json:"recoveryFound"`
	Phase             string               `json:"phase,omitempty"`
	Stage             string               `json:"stage,omitempty"`
	OperationUid      string               `json:"operationUid,omitempty"`
	BackupId          string               `json:"backupId,omitempty"`
	BackupRelease     string               `json:"backupRelease,omitempty"`
	SelectedBackupId  string               `json:"selectedBackupId,omitempty"`
	CredentialsStatus string               `json:"credentialsStatus,omitempty"`
	StartedAt         int64                `json:"startedAt,omitempty"`
	StartedBy         string               `json:"startedBy,omitempty"`
	ClosedAt          int64                `json:"closedAt,omitempty"`
	LastError         string               `json:"lastError,omitempty"`
	ClusterFound      bool                 `json:"clusterFound"`
	ClusterPhase      string               `json:"clusterPhase,omitempty"`
	Backups           []RecoveryBackupInfo `json:"backups"`
	Checks            []RecoveryCheckInfo  `json:"checks"`
	Messages          []string             `json:"messages"`
}

var RecoveryStatus = ucx.Rpc[util.Empty, RecoveryStatusResponse]{
	CallName: "k8s.recoveryStatus",
}

type RecoveryStartRequest struct {
	BackupId        string `json:"backupId,omitempty"`
	AcknowledgeRisk bool   `json:"acknowledgeRisk,omitempty"`
}

type RecoveryStartResponse struct {
	Phase    string              `json:"phase"`
	Stage    string              `json:"stage"`
	BackupId string              `json:"backupId,omitempty"`
	Message  string              `json:"message,omitempty"`
	Checks   []RecoveryCheckInfo `json:"checks"`
}

var RecoveryStart = ucx.Rpc[RecoveryStartRequest, RecoveryStartResponse]{
	CallName: "k8s.startRecovery",
}

type KubeconfigResponse struct {
	Content string `json:"content"`
}

var Kubeconfig = ucx.Rpc[util.Empty, KubeconfigResponse]{
	CallName: "k8s.kubeconfig",
}
