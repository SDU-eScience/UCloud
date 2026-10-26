package k8sapi

import (
	"fmt"
	"regexp"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const (
	AppName = "kubernetes"

	StackType = "Kubernetes"

	GroupControlPlane = "control-plane"

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

	AppModeCreator   = "creator"
	AppModeDashboard = "dashboard"
	AppModeRecovery  = "recovery"

	NodeStateReady    = "Ready"
	NodeStateNotReady = "NotReady"
	NodeStateUnknown  = "Unknown"

	ClusterPhaseProvisioning = "provisioning"
	ClusterPhaseCleanup      = "cleanup-pending"
	ClusterPhaseError        = "error"
	ClusterPhaseCreated      = "created"
	ClusterPhaseRecovering   = "recovering"
)

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

func ValidateDeployRequest(request DeployRequest) error {
	name := strings.TrimSpace(request.Name)
	if name == "" {
		return fmt.Errorf("the cluster name must not be empty")
	}
	if len(name) > ClusterNameMaxLen {
		return fmt.Errorf("the cluster name must be at most %d characters", ClusterNameMaxLen)
	}
	if !ValidGroupName(name) {
		return fmt.Errorf("the cluster name may only contain a-z, 0-9 and dashes: %s", name)
	}
	if strings.HasPrefix(name, GroupControlPlane) {
		return fmt.Errorf("the cluster name must not start with the reserved name: %s", GroupControlPlane)
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
		return fmt.Errorf("the control plane machine must come from the selected service provider (%s)", provider)
	}
	switch controlPlane.Nodes {
	case ControlPlaneMinNodes, 3, 5, ControlPlaneMaxNodes:
	default:
		return fmt.Errorf(
			"the control plane node count must be an odd number from %d to %d",
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
			return fmt.Errorf("worker pool names must be at most %d characters: %s", PoolNameMaxLen, poolName)
		}
		if !ValidGroupName(poolName) {
			return fmt.Errorf("worker pool names may only contain a-z, 0-9 and dashes: %s", poolName)
		}
		if poolName == GroupControlPlane {
			return fmt.Errorf("the name is reserved for the control plane: %s", poolName)
		}
		if seen[poolName] {
			return fmt.Errorf("worker pool names must be unique: %s", poolName)
		}
		seen[poolName] = true

		if pool.Machine.Id == "" {
			return fmt.Errorf("select a machine type for worker pool %s", poolName)
		}
		if pool.Machine.Provider != provider {
			return fmt.Errorf(
				"the machine for worker pool %s must come from the selected service provider (%s)",
				poolName,
				provider,
			)
		}
		if pool.Nodes < 1 {
			return fmt.Errorf("worker pool %s needs at least one node", poolName)
		}
		if pool.Nodes > MaxPoolNodes {
			return fmt.Errorf("worker pool %s supports at most %d nodes", poolName, MaxPoolNodes)
		}
		if pool.Disk < MinDiskGb {
			return fmt.Errorf("worker pool %s needs at least %d GB of disk", poolName, MinDiskGb)
		}
		if pool.Disk > MaxDiskGb {
			return fmt.Errorf("worker pool %s supports at most %d GB of disk", poolName, MaxDiskGb)
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
	IpAddress      string                  `json:"ipAddress"`
	JobId          string                  `json:"jobId"`
	Machine        accapi.ProductReference `json:"machine"`
	Disk           int                     `json:"disk"`
	DesiredVersion string                  `json:"desiredVersion,omitempty"`
	Version        string                  `json:"version,omitempty"`
	State          string                  `json:"state,omitempty"`
}

type MaintenanceOperation struct {
	NodeName string `json:"nodeName"`
	Kind     string `json:"kind"`
	Phase    string `json:"phase"`
	Error    string `json:"error,omitempty"`
}

type StatusResponse struct {
	Name                string                 `json:"name"`
	Phase               string                 `json:"phase"`
	FailureReason       string                 `json:"failureReason,omitempty"`
	K8sVersion          string                 `json:"k8sVersion"`
	MachineProvider     string                 `json:"machineProvider"`
	Pools               []StatusPool           `json:"pools"`
	Nodes               []StatusNode           `json:"nodes"`
	NodeHealthKnown     bool                   `json:"nodeHealthKnown"`
	NodeTotal           int                    `json:"nodeTotal"`
	NodeReady           int                    `json:"nodeReady"`
	NodeNotReady        int                    `json:"nodeNotReady"`
	NodeUnknown         int                    `json:"nodeUnknown"`
	ControlPlaneVersion string                 `json:"controlPlaneVersion,omitempty"`
	MaintenanceKnown    bool                   `json:"maintenanceKnown"`
	MaintenanceError    string                 `json:"maintenanceError,omitempty"`
	Maintenance         []MaintenanceOperation `json:"maintenance"`
}

var Status = ucx.Rpc[util.Empty, StatusResponse]{
	CallName: "k8s.status",
}

type KubeconfigResponse struct {
	Content string `json:"content"`
}

var Kubeconfig = ucx.Rpc[util.Empty, KubeconfigResponse]{
	CallName: "k8s.kubeconfig",
}
