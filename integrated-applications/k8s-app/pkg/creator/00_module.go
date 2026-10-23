package creator

import (
	"context"
	"fmt"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func App() ucx.Application {
	defaultRelease := shared.K3sRelease{}
	if releases := shared.CreationReleases(time.Now().UTC()); len(releases) > 0 {
		defaultRelease = releases[0]
	}
	return &k8sApp{
		ClusterId:          fmt.Sprintf("k8s-%v", util.RandomTokenNoTs(4)),
		K8sVersion:         defaultRelease.Release,
		ControlPlaneNodes:  1,
		ControlPlaneDiskGb: 50,
		Pools: []creatorWorkerPool{
			{
				Name:    "workers-1",
				Machine: accapi.ProductReference{},
				Nodes:   1,
				DiskGb:  50,
			},
		},
	}
}

type creatorWorkerPool struct {
	Name    string
	Machine accapi.ProductReference
	Nodes   int
	DiskGb  int
}

type k8sApp struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`

	ClusterId  string
	K8sVersion string

	ServiceProvider string

	ControlPlaneMachine accapi.ProductReference
	ControlPlaneNodes   int
	ControlPlaneDiskGb  int

	Pools []creatorWorkerPool

	DeployBusy bool

	RecoverStacks  []recoverStackEntry `ucx:"-"`
	RecoverLoaded  bool                `ucx:"-"`
	RecoverError   string
	RecoverStackId string
	RecoverBusy    bool
}

type recoverStackEntry struct {
	Id   string
	Name string
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

	for i := range app.Pools {
		costEntries = append(costEntries, ucx.CostEstimateEntry{
			Title:           app.poolName(i),
			TitleBindPath:   "pools." + strconv.Itoa(i) + ".name",
			MachineBindPath: "pools." + strconv.Itoa(i) + ".machine",
			CountBindPath:   "pools." + strconv.Itoa(i) + ".nodes",
		})
	}

	return ucx.KeyboardNavigationNode("keyboardNavigation").HorizontalSelector("[data-job-info-field]").Submit("stack").SubmitDisabled(app.DeployBusy).Children(
		ucx.SidebarLayout().Sidebar(
			ucx.Surface().Children(
				ucx.CostEstimateNode("costEstimate", costEntries),
				ucx.Button("stack", "Deploy", ucx.ColorSuccessMain).
					ButtonBusy("deployBusy").
					ButtonSubmitShortcut(true).
					Sx(
						ucx.SxMt(24),
						ucx.SxWidthPercent(100),
					).
					On(ucx.UiEventClick, app.deploy),
			),
		).Sx(
			ucx.SxDisplayFlex,
			ucx.SxFlexDirectionColumn,
			ucx.SxGap(24),
		).Children(
			app.cardMetadata(),
			app.cardControlPlane(),
			app.cardWorkerPools(),
			app.cardRecover(),
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
				FieldRowDescription("The exact Kubernetes patch release to deploy.").
				Children(
					ucx.EnumSelectorNode("k8sVersionSelect", "k8sVersion", k8sVersionOptions()),
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
				FieldRowDescription("The number of control plane nodes. Must be an odd number from 1 to 7.").
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

	for i := range app.Pools {
		children = append(children, app.poolCard(i))
	}

	children = append(children,
		ucx.Button("addPool", "Add worker pool", ucx.ColorSecondaryMain).ButtonDisabledWhen("deployBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			app.Pools = append(app.Pools, creatorWorkerPool{
				Name:   fmt.Sprintf("workers-%v", len(app.Pools)+1),
				Nodes:  1,
				DiskGb: 50,
			})
			ucx.AppUpdateUi(app)
		}),
	)
	return ucx.FlexEx("workerPools", ucx.FlexProps{Direction: "column", Gap: 24}).Children(children...)
}

func (app *k8sApp) poolCard(index int) ucx.UiNode {
	indexPath := strconv.Itoa(index)

	removeButton := ucx.UiNode{}
	if len(app.Pools) > 1 {
		poolIndex := index
		removeButton = ucx.Button("removePool"+indexPath, "Remove pool", ucx.ColorErrorMain).ButtonDisabledWhen("deployBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
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
			ucx.FieldRowNodeEx("poolNameRow"+indexPath, "Pool name", "pools."+indexPath+".name").
				FieldRowDescription("The Kubernetes node group name for this pool. Must contain only a-z, 0-9 and dashes.").
				FieldRowRequired(true).
				Children(
					ucx.InputText("poolName"+indexPath, "", "workers", "pools."+indexPath+".name"),
				),
			ucx.FieldRowNodeEx("poolMachineRow"+indexPath, "Machine type", "").
				FieldRowDescription("The machine type used for the nodes in this pool.").
				Children(
					ucx.MachineTypeSelector("poolMachine"+indexPath, "", "pools."+indexPath+".machine", ucx.MachineCapabilityVm).
						MachineSelectorProviderBindPath("serviceProvider").MachineSelectorProviderOnly(true),
				),
			ucx.FieldRowNodeEx("poolNodesRow"+indexPath, "Node count", "pools."+indexPath+".nodes").
				FieldRowDescription("The number of worker nodes in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolNodes"+indexPath, "", "pools."+indexPath+".nodes", 1, 250),
				),
			ucx.FieldRowNodeEx("poolDiskRow"+indexPath, "Disk size (GB)", "pools."+indexPath+".diskGb").
				FieldRowDescription("The disk size for each node in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolDisk"+indexPath, "", "pools."+indexPath+".diskGb", 10, 1024),
				),
		),
	)
}

func (app *k8sApp) poolName(index int) string {
	if index < len(app.Pools) {
		trimmed := strings.TrimSpace(app.Pools[index].Name)
		if trimmed != "" {
			return trimmed
		}
	}
	return "Workers"
}

func (app *k8sApp) removePool(index int) {
	if len(app.Pools) <= 1 || index < 0 || index >= len(app.Pools) {
		return
	}

	app.Pools = append(app.Pools[:index], app.Pools[index+1:]...)
}

func (app *k8sApp) cardRecover() ucx.UiNode {
	card := ucx.SurfaceEx("recoverCard").Children(
		ucx.H3Ex("recoverHeading", "Recover an existing cluster"),
	)

	if !app.RecoverLoaded {
		return card.Children(ucx.TextEx("recoverLoading", "Looking for clusters..."))
	}

	if app.RecoverError != "" {
		return card.Children(ucx.TextEx("recoverError", app.RecoverError))
	}

	if len(app.RecoverStacks) == 0 {
		return card.Children(ucx.TextEx(
			"recoverEmpty",
			"No clusters are available for recovery. You must have EDIT access to a cluster to recover it.",
		))
	}

	options := make([]ucx.Option, 0, len(app.RecoverStacks))
	for _, stack := range app.RecoverStacks {
		options = append(options, ucx.Option{
			Key:   stack.Id,
			Value: fmt.Sprintf("%s (%s)", stack.Name, stack.Id),
		})
	}

	return card.Children(
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("recoverClusterRow", "Cluster", "recoverStackId").
				FieldRowDescription("The cluster to recover. The recovery job runs on the cluster's state drive.").
				FieldRowRequired(true).
				Children(
					ucx.EnumSelectorNode("recoverClusterSelect", "recoverStackId", options),
				),
			ucx.Button("recover", "Start recovery", ucx.ColorErrorMain).
				ButtonBusy("recoverBusy").
				ButtonHoldToConfirm(true).
				Sx(
					ucx.SxMt(12),
					ucx.SxWidthPercent(100),
				).
				On(ucx.UiEventClick, app.recover),
		),
	)
}

func (app *k8sApp) OnSysHello(payload string) {
	go app.loadRecoverStacks()
}

func (app *k8sApp) loadRecoverStacks() {
	session := *app.Session()
	if session == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
	defer cancel()

	entries := make([]recoverStackEntry, 0)
	next := util.OptNone[string]()
	for {
		page, err := ucxapi.StackBrowse.InvokeEx(ctx, session, orcapi.StacksBrowseRequest{
			ItemsPerPage: 100,
			Next:         next,
		})
		if err != nil {
			log.Warn("k8s-app: could not list clusters for recovery: %v", err)
			app.mu.Lock()
			app.RecoverLoaded = true
			app.RecoverError = "Could not load the list of clusters. Reload the page to try again."
			ucx.AppUpdateUi(app)
			app.mu.Unlock()
			return
		}

		for _, stack := range page.Items {
			if strings.TrimSpace(stack.Type) != shared.StackType {
				continue
			}
			if !slices.Contains(stack.Permissions.Myself, orcapi.PermissionEdit) {
				continue
			}

			entries = append(entries, recoverStackEntry{Id: stack.Id, Name: shared.StackType})
		}

		if !page.Next.Present {
			break
		}
		next = page.Next
	}

	slices.SortFunc(entries, func(a recoverStackEntry, b recoverStackEntry) int {
		return strings.Compare(a.Id, b.Id)
	})

	app.mu.Lock()
	app.RecoverLoaded = true
	app.RecoverError = ""
	app.RecoverStacks = entries
	if !slices.ContainsFunc(entries, func(entry recoverStackEntry) bool { return entry.Id == app.RecoverStackId }) {
		app.RecoverStackId = ""
		if len(entries) > 0 {
			app.RecoverStackId = entries[0].Id
		}
	}
	ucx.AppUpdateUi(app)
	app.mu.Unlock()
}

func (app *k8sApp) recover(ev ucx.UiEvent) {
	if app.RecoverBusy {
		return
	}

	stackId := strings.TrimSpace(app.RecoverStackId)
	if stackId == "" {
		ucxsvc.UiSendFailure(app, "Select a cluster to recover")
		return
	}

	session := *app.Session()
	app.RecoverBusy = true
	ucx.AppUpdateModelPatch(session, map[string]ucx.Value{"recoverBusy": ucx.VBool(true)})

	go func() {
		_, err := ucxapi.StackSpawnDeclaredJob.Invoke(session, fndapi.FindByStringId{Id: stackId})

		app.mu.Lock()
		app.RecoverBusy = false
		ucx.AppUpdateUi(app)
		app.mu.Unlock()

		if err != nil {
			ucxsvc.UiSendFailure(app, "Could not start recovery: "+err.Error())
			return
		}

		ucxsvc.UiSendSuccess(app, "Recovery started for "+stackId)
		_, _ = ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stackId})
	}()
}

func (app *k8sApp) deploy(ev ucx.UiEvent) {
	if app.DeployBusy {
		return
	}

	release, ok := shared.ReleaseByExactVersion(app.K8sVersion)
	if !ok {
		ucxsvc.UiSendFailure(app, "Unknown Kubernetes version: "+app.K8sVersion)
		return
	}

	clusterId := strings.TrimSpace(app.ClusterId)
	if clusterId == "" {
		ucxsvc.UiSendFailure(app, "Cluster ID must not be empty")
		return
	}
	if len(clusterId) > clusterIdMaxLen {
		ucxsvc.UiSendFailure(app, fmt.Sprintf("The cluster id must be at most %d characters", clusterIdMaxLen))
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
	switch app.ControlPlaneNodes {
	case 1, 3, 5, 7:
	default:
		ucxsvc.UiSendFailure(app, "The control plane node count must be an odd number from 1 to 7")
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
	if app.ControlPlaneMachine.Provider != app.ServiceProvider {
		ucxsvc.UiSendFailure(app, "The control plane machine must come from the selected service provider")
		return
	}

	if strings.HasPrefix(clusterId, shared.GroupControlPlane) {
		ucxsvc.UiSendFailure(app, "The cluster id must not start with the reserved name: "+shared.GroupControlPlane)
		return
	}

	seenPools := map[string]util.Empty{}
	pools := make([]shared.ClusterPoolSpec, 0, len(app.Pools))
	totalNodes := app.ControlPlaneNodes
	for i, pool := range app.Pools {
		name := strings.TrimSpace(pool.Name)
		machine := pool.Machine
		nodes := pool.Nodes
		diskGb := pool.DiskGb

		if name == "" {
			ucxsvc.UiSendFailure(app, "Worker pool "+strconv.Itoa(i+1)+" must have a name")
			return
		}
		if len(name) > poolNameMaxLen {
			ucxsvc.UiSendFailure(app, fmt.Sprintf("Worker pool names must be at most %d characters: %s", poolNameMaxLen, name))
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
		if name == shared.GroupControlPlane {
			ucxsvc.UiSendFailure(app, "The name is reserved for the control plane: "+name)
			return
		}
		seenPools[name] = util.Empty{}

		if machine.Id == "" {
			ucxsvc.UiSendFailure(app, "Select a machine type for worker pool "+name+"!")
			return
		}
		if machine.Provider != app.ServiceProvider {
			ucxsvc.UiSendFailure(app, "The machine for worker pool "+name+" must come from the selected service provider")
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

		totalNodes += nodes
		pools = append(pools, shared.ClusterPoolSpec{
			Name:    name,
			Machine: machine,
			Nodes:   nodes,
			DiskGb:  diskGb,
		})
	}

	if totalNodes > shared.ClusterMaxNodes {
		ucxsvc.UiSendFailure(app, fmt.Sprintf(
			"The cluster supports at most %d nodes, but %d were requested",
			shared.ClusterMaxNodes,
			totalNodes,
		))
		return
	}

	stackId := clusterId

	spec := shared.ClusterSpec{
		ControlPlaneMachine: app.ControlPlaneMachine,
		ControlPlaneNodes:   app.ControlPlaneNodes,
		ControlPlaneDiskGb:  app.ControlPlaneDiskGb,
		WorkerPools:         pools,
		K8sVersion:          release.Release,
	}

	app.DeployBusy = true
	ucx.AppUpdateModelPatch(*app.Session(), map[string]ucx.Value{
		"deployBusy": ucx.VBool(true),
	})

	go func() {
		app.mu.Lock()
		defer app.mu.Unlock()

		_, _ = shared.ClusterCreate(app, stackId, spec)
		app.DeployBusy = false
		ucx.AppUpdateUi(app)
	}()
}

func (app *k8sApp) OnMessage(msg ucx.Frame) {}

func k8sVersionOptions() []ucx.Option {
	now := time.Now().UTC()
	releases := shared.CreationReleases(now)

	options := make([]ucx.Option, 0, len(releases))
	for _, release := range releases {
		options = append(options, ucx.Option{
			Key:   release.Release,
			Value: shared.ReleaseOptionLabel(release, now),
		})
	}
	return options
}

const clusterIdMaxLen = 30
const poolNameMaxLen = 30

var dnsSafeRe = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$`)
