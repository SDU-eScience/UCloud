package creator

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func App() ucx.Application {
	return &k8sApp{
		ClusterId:          fmt.Sprintf("k8s-%v", util.RandomTokenNoTs(4)),
		K8sVersion:         "1.36",
		Ports:              "8080",
		ControlPlaneNodes:  1,
		ControlPlaneDiskGb: 50,
		PoolCount:          1,
		PoolNames:          []string{"workers-1"},
		PoolNodes:          []int{1},
		PoolDisksGb:        []int{50},
	}
}

type k8sApp struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`

	ClusterId  string
	K8sVersion string
	Ports      string

	ServiceProvider string

	ControlPlaneMachine accapi.ProductReference
	ControlPlaneNodes   int
	ControlPlaneDiskGb  int

	PoolCount int `ucx:"-"`

	PoolNames    []string
	PoolMachines []accapi.ProductReference
	PoolNodes    []int
	PoolDisksGb  []int
}

func (app *k8sApp) Mutex() *sync.Mutex {
	return &app.mu
}

func (app *k8sApp) Session() **ucx.Session {
	return &app.session
}

func (app *k8sApp) OnInit() {
	// Nothing to do
}

func (app *k8sApp) UserInterface() ucx.UiNode {
	costEntries := []ucx.CostEstimateEntry{
		{Title: "Control plane", MachineBindPath: "controlPlaneMachine", CountBindPath: "controlPlaneNodes"},
	}

	for i := 0; i < app.PoolCount; i++ {
		costEntries = append(costEntries, ucx.CostEstimateEntry{
			Title:           app.poolName(i),
			TitleBindPath:   "poolNames." + strconv.Itoa(i),
			MachineBindPath: "poolMachines." + strconv.Itoa(i),
			CountBindPath:   "poolNodes." + strconv.Itoa(i),
		})
	}

	return ucx.KeyboardNavigationNode("keyboardNavigation").HorizontalSelector("[data-job-info-field]").Submit("stack").Children(
		ucx.SidebarLayout().Sidebar(
			ucx.Surface().Children(
				ucx.CostEstimateNode("costEstimate", costEntries),
				ucx.Button("stack", "Deploy", ucx.ColorSuccessMain).ButtonSubmitShortcut(true).Sx(
					ucx.SxMt(24),
					ucx.SxWidthPercent(100),
				).On(ucx.UiEventClick, app.deploy),
			),
		).Sx(
			ucx.SxDisplayFlex,
			ucx.SxFlexDirectionColumn,
			ucx.SxGap(24),
		).Children(
			app.cardMetadata(),
			app.cardControlPlane(),
			app.cardWorkerPools(),
		),
	)
}

func (app *k8sApp) cardMetadata() ucx.UiNode {
	return ucx.SurfaceEx("metadataCard").Children(
		ucx.H3Ex("metadataHeading", "Metadata"),
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("clusterIdRow", "Cluster ID", "clusterId").FieldRowRequired(true).
				FieldRowDescription("A unique name for your cluster. It is used in hostnames and must contain only a-z, 0-9 and dashes.").
				Children(
					ucx.InputText("clusterId", "", "", "clusterId"),
				),
			ucx.FieldRowNodeEx("serviceProviderRow", "Service provider", "").
				FieldRowDescription("The provider that runs your cluster. All machines must come from this provider.").
				Children(
					ucx.ServiceProviderSelectorNode("serviceProvider", "serviceProvider").ServiceProviderRealProvidersOnly(true),
				),
			ucx.FieldRowNodeEx("k8sVersionRow", "K8s version", "k8sVersion").
				FieldRowDescription("The Kubernetes version to deploy.").
				Children(
					ucx.EnumSelectorNode("k8sVersionSelect", "k8sVersion", []ucx.Option{
						{Key: "1.36", Value: "1.36"},
					}),
				),
			ucx.FieldRowNodeEx("portsRow", "Exposed ports", "").
				FieldRowDescription("Comma-separated list of ports to expose. Reserved ports: API 6443, Headlamp 30500.").
				Children(
					ucx.InputText("ports", "", "8080", "ports"),
				),
		),
	)
}

func (app *k8sApp) cardControlPlane() ucx.UiNode {
	return ucx.SurfaceEx("controlPlaneCard").Children(
		ucx.H3Ex("controlPlaneHeading", "Control plane"),
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("controlPlaneMachineRow", "Machine type", "").
				FieldRowDescription("The machine type used for the control plane nodes.").
				Children(
					ucx.MachineTypeSelector("controlPlaneMachine", "", "controlPlaneMachine", ucx.MachineCapabilityVm).
						MachineSelectorProviderBindPath("serviceProvider").MachineSelectorProviderOnly(true),
				),
			ucx.FieldRowNodeEx("controlPlaneNodesRow", "Node count", "controlPlaneNodes").
				FieldRowDescription("The number of control plane nodes. An odd number is recommended for high availability.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("controlPlaneNodes", "", "controlPlaneNodes", 1, 7),
				),
			ucx.FieldRowNodeEx("controlPlaneDiskRow", "Disk size (GB)", "controlPlaneDiskGb").
				FieldRowDescription("The disk size for each control plane node.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("controlPlaneDisk", "", "controlPlaneDiskGb", 10, 1024),
				),
		),
	)
}

func (app *k8sApp) cardWorkerPools() ucx.UiNode {
	children := []ucx.UiNode{}

	for i := 0; i < app.PoolCount; i++ {
		children = append(children, app.poolCard(i))
	}

	children = append(children,
		ucx.Button("addPool", "Add worker pool", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			app.PoolCount++
			app.PoolNames = append(app.PoolNames, fmt.Sprintf("workers-%v", app.PoolCount))
			app.PoolMachines = append(app.PoolMachines, accapi.ProductReference{})
			app.PoolNodes = append(app.PoolNodes, 1)
			app.PoolDisksGb = append(app.PoolDisksGb, 50)
			ucx.AppUpdateUi(app)
		}),
	)
	return ucx.FlexEx("workerPools", ucx.FlexProps{Direction: "column", Gap: 24}).Children(children...)
}

func (app *k8sApp) poolCard(index int) ucx.UiNode {
	indexPath := strconv.Itoa(index)

	removeButton := ucx.UiNode{}
	if app.PoolCount > 1 {
		poolIndex := index
		removeButton = ucx.Button("removePool"+indexPath, "Remove pool", ucx.ColorErrorMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			app.removePool(poolIndex)
			ucx.AppUpdateUi(app)
		})
	}

	return ucx.SurfaceEx("poolCard"+indexPath).Children(
		ucx.Toolbar().Children(
			ucx.H3Ex("poolHeading"+indexPath, fmt.Sprintf("Worker pool #%v", index+1)),
			removeButton,
		),
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("poolNameRow"+indexPath, "Pool name", "poolNames."+indexPath).
				FieldRowDescription("The Kubernetes node group name for this pool. Must contain only a-z, 0-9 and dashes.").
				FieldRowRequired(true).
				Children(
					ucx.InputText("poolName"+indexPath, "", "workers", "poolNames."+indexPath),
				),
			ucx.FieldRowNodeEx("poolMachineRow"+indexPath, "Machine type", "").
				FieldRowDescription("The machine type used for the nodes in this pool.").
				Children(
					ucx.MachineTypeSelector("poolMachine"+indexPath, "", "poolMachines."+indexPath, ucx.MachineCapabilityVm).
						MachineSelectorProviderBindPath("serviceProvider").MachineSelectorProviderOnly(true),
				),
			ucx.FieldRowNodeEx("poolNodesRow"+indexPath, "Node count", "poolNodes."+indexPath).
				FieldRowDescription("The number of worker nodes in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolNodes"+indexPath, "", "poolNodes."+indexPath, 1, 250),
				),
			ucx.FieldRowNodeEx("poolDiskRow"+indexPath, "Disk size (GB)", "poolDisksGb."+indexPath).
				FieldRowDescription("The disk size for each node in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolDisk"+indexPath, "", "poolDisksGb."+indexPath, 10, 1024),
				),
		),
	)
}

func (app *k8sApp) poolName(index int) string {
	if index < len(app.PoolNames) {
		trimmed := strings.TrimSpace(app.PoolNames[index])
		if trimmed != "" {
			return trimmed
		}
	}
	return "Workers"
}

func (app *k8sApp) removePool(index int) {
	if app.PoolCount <= 1 || index < 0 || index >= app.PoolCount {
		return
	}

	app.PoolNames = append(app.PoolNames[:index], app.PoolNames[index+1:]...)
	app.PoolMachines = append(app.PoolMachines[:index], app.PoolMachines[index+1:]...)
	app.PoolNodes = append(app.PoolNodes[:index], app.PoolNodes[index+1:]...)
	app.PoolDisksGb = append(app.PoolDisksGb[:index], app.PoolDisksGb[index+1:]...)
	app.PoolCount--
}

func (app *k8sApp) deploy(ev ucx.UiEvent) {
	ports, ok := parsePorts(app.Ports)
	if !ok {
		ucxsvc.UiSendFailure(app, "Could not decode ports or a reserved port was used! Found list: "+app.Ports)
		return
	}

	clusterId := strings.TrimSpace(app.ClusterId)
	if clusterId == "" {
		ucxsvc.UiSendFailure(app, "Cluster ID must not be empty")
		return
	}
	if !dnsSafeRe.MatchString(clusterId) {
		ucxsvc.UiSendFailure(app, "Cluster ID may only contain a-z, 0-9 and dashes: "+clusterId)
		return
	}

	if app.ControlPlaneMachine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine type for the control plane!")
		return
	}
	if app.ControlPlaneNodes < 1 {
		ucxsvc.UiSendFailure(app, "The control plane needs at least one node")
		return
	}
	if app.ControlPlaneDiskGb < 10 {
		ucxsvc.UiSendFailure(app, "The control plane needs at least 10 GB of disk")
		return
	}

	if app.ServiceProvider == "" {
		ucxsvc.UiSendFailure(app, "Select a service provider!")
		return
	}

	seenPools := map[string]util.Empty{}
	pools := make([]shared.ClusterPoolSpec, 0, app.PoolCount)
	for i := 0; i < app.PoolCount; i++ {
		var machine accapi.ProductReference
		if i < len(app.PoolMachines) {
			machine = app.PoolMachines[i]
		}
		var nodes int
		if i < len(app.PoolNodes) {
			nodes = app.PoolNodes[i]
		}
		var diskGb int
		if i < len(app.PoolDisksGb) {
			diskGb = app.PoolDisksGb[i]
		}
		var name string
		if i < len(app.PoolNames) {
			name = strings.TrimSpace(app.PoolNames[i])
		}

		if name == "" {
			ucxsvc.UiSendFailure(app, "Worker pool "+strconv.Itoa(i+1)+" must have a name")
			return
		}
		if !dnsSafeRe.MatchString(name) {
			ucxsvc.UiSendFailure(app, "Worker pool names may only contain a-z, 0-9 and dashes: "+name)
			return
		}
		if _, exists := seenPools[name]; exists {
			ucxsvc.UiSendFailure(app, "Worker pool names must be unique: "+name)
			return
		}
		seenPools[name] = util.Empty{}

		if machine.Id == "" {
			ucxsvc.UiSendFailure(app, "Select a machine type for worker pool "+name+"!")
			return
		}
		if nodes < 1 {
			ucxsvc.UiSendFailure(app, "Worker pool "+name+" needs at least one node")
			return
		}
		if diskGb < 10 {
			ucxsvc.UiSendFailure(app, "Worker pool "+name+" needs at least 10 GB of disk")
			return
		}

		pools = append(pools, shared.ClusterPoolSpec{
			Name:    name,
			Machine: machine,
			Nodes:   nodes,
			DiskGb:  diskGb,
		})
	}

	stackId := "K8s-" + clusterId

	_, ok = shared.ClusterCreate(app, stackId, shared.ClusterSpec{
		ControlPlaneMachine: app.ControlPlaneMachine,
		ControlPlaneNodes:   app.ControlPlaneNodes,
		ControlPlaneDiskGb:  app.ControlPlaneDiskGb,
		WorkerPools:         pools,
		K8sVersion:          app.K8sVersion,
		Ports:               ports,
	})
	if !ok {
		return
	}
}

func (app *k8sApp) OnMessage(msg ucx.Frame) {}

var dnsSafeRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)

func parsePorts(ports string) ([]int, bool) {
	result := []int{}
	for _, p := range strings.Split(ports, ",") {
		if p == "" {
			continue
		}

		port, err := strconv.Atoi(p)
		if err != nil {
			return nil, false
		}

		if port == shared.ApiPort || port == shared.HeadlampPort {
			return nil, false
		}

		result = append(result, port)
	}
	return result, true
}
