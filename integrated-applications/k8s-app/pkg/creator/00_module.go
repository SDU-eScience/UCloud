package creator

import (
	"context"
	"encoding/json"
	"fmt"
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

	HostProvider string `ucx:"-"`
	SwitchBusy   bool   `ucx:"-"`

	ControlPlaneMachine accapi.ProductReference
	ControlPlaneNodes   int
	ControlPlaneDiskGb  int

	Pools []creatorWorkerPool

	DeployBusy bool

	RecoverStacks        []recoverStackEntry `ucx:"-"`
	RecoverLoaded        bool                `ucx:"-"`
	RecoverLoading       bool                `ucx:"-"`
	RecoverError         string
	RecoverStackId       string
	RecoverBusy          bool
	RecoverStartDisabled bool
	ShowRecoverDialog    bool `ucx:"-"`
}

type recoverStackEntry struct {
	Id   string
	Name string
}

type creatorHandoff struct {
	ClusterId          string               `json:"clusterId" ucx:"clusterId"`
	K8sVersion         string               `json:"k8sVersion" ucx:"k8sVersion"`
	ServiceProvider    string               `json:"serviceProvider" ucx:"serviceProvider"`
	ControlPlaneNodes  int                  `json:"controlPlaneNodes" ucx:"controlPlaneNodes"`
	ControlPlaneDiskGb int                  `json:"controlPlaneDiskGb" ucx:"controlPlaneDiskGb"`
	Pools              []creatorPoolHandoff `json:"pools" ucx:"pools"`
	RecoverStackId     string               `json:"recoverStackId" ucx:"recoverStackId"`
}

type creatorPoolHandoff struct {
	Name   string `json:"name" ucx:"name"`
	Nodes  int    `json:"nodes" ucx:"nodes"`
	DiskGb int    `json:"diskGb" ucx:"diskGb"`
}

func (app *k8sApp) Mutex() *sync.Mutex {
	return &app.mu
}

func (app *k8sApp) Session() **ucx.Session {
	return &app.session
}

func (app *k8sApp) OnInit() {
	shared.RegisterRpcHandlers(app, shared.ModeCreator)
	app.registerRpcHandlers()
}

func (app *k8sApp) OnSysHello(payload string) {
	var request orcapi.AppUcxConnectProviderRequest
	if err := json.Unmarshal([]byte(payload), &request); err != nil {
		return
	}

	app.HostProvider = strings.TrimSpace(request.Provider)
	app.SwitchBusy = false

	handoffApplied := false
	if len(request.Handoff) > 0 {
		var handoff creatorHandoff
		if err := json.Unmarshal(request.Handoff, &handoff); err == nil {
			app.applyProviderHandoff(handoff)
			handoffApplied = true
		}
	}

	if handoffApplied && app.HostProvider != "" && app.ServiceProvider != app.HostProvider {
		app.ServiceProvider = ""
	}
}

func (app *k8sApp) applyProviderHandoff(handoff creatorHandoff) {
	if handoff.ClusterId != "" {
		app.ClusterId = handoff.ClusterId
	}
	if _, ok := shared.ReleaseByExactVersion(handoff.K8sVersion); ok {
		app.K8sVersion = handoff.K8sVersion
	}
	if handoff.ServiceProvider != "" {
		app.ServiceProvider = handoff.ServiceProvider
	}
	switch handoff.ControlPlaneNodes {
	case 1, 3, 5, 7:
		app.ControlPlaneNodes = handoff.ControlPlaneNodes
	}
	if handoff.ControlPlaneDiskGb >= 10 && handoff.ControlPlaneDiskGb <= 1024 {
		app.ControlPlaneDiskGb = handoff.ControlPlaneDiskGb
	}

	if len(handoff.Pools) > 0 {
		pools := make([]creatorWorkerPool, 0, len(handoff.Pools))
		for i := range handoff.Pools {
			pool := handoff.Pools[i]
			nodes := pool.Nodes
			if nodes < 1 {
				nodes = 1
			} else if nodes > 250 {
				nodes = 250
			}

			diskGb := pool.DiskGb
			if diskGb < 10 {
				diskGb = 10
			} else if diskGb > 1024 {
				diskGb = 1024
			}

			pools = append(pools, creatorWorkerPool{
				Name:   pool.Name,
				Nodes:  nodes,
				DiskGb: diskGb,
			})
		}
		app.Pools = pools
	}

	app.ControlPlaneMachine = accapi.ProductReference{}
	app.RecoverStackId = handoff.RecoverStackId
}

func (app *k8sApp) providerHandoff() creatorHandoff {
	pools := make([]creatorPoolHandoff, 0, len(app.Pools))
	for _, pool := range app.Pools {
		pools = append(pools, creatorPoolHandoff{
			Name:   pool.Name,
			Nodes:  pool.Nodes,
			DiskGb: pool.DiskGb,
		})
	}

	return creatorHandoff{
		ClusterId:          app.ClusterId,
		K8sVersion:         app.K8sVersion,
		ServiceProvider:    app.ServiceProvider,
		ControlPlaneNodes:  app.ControlPlaneNodes,
		ControlPlaneDiskGb: app.ControlPlaneDiskGb,
		Pools:              pools,
		RecoverStackId:     app.RecoverStackId,
	}
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

	return ucx.KeyboardNavigationNode("keyboardNavigation").HorizontalSelector("[data-job-info-field]").Submit("stack").SubmitDisabled(app.DeployBusy || app.SwitchBusy).Children(
		ucx.SidebarLayout().Sidebar(
			ucx.Surface().Children(
				ucx.CostEstimateNode("costEstimate", costEntries),
				ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(
					ucx.Button("stack", "Deploy", ucx.ColorSuccessMain).
						ButtonBusy("deployBusy").
						ButtonSubmitShortcut(true).
						Sx(
							ucx.SxMt(24),
							ucx.SxWidthPercent(100),
						).
						On(ucx.UiEventClick, app.deploy),
					ucx.LinkButton("recoverLink", "Recover an existing cluster", ucx.ColorLinkColor).
						On(ucx.UiEventClick, app.openRecoverDialog),
				),
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
		app.recoverDialog(),
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
					ucx.ServiceProviderSelectorNode("serviceProvider", "serviceProvider").
						ServiceProviderRealProvidersOnly(true).
						ServiceProviderPreferred(app.HostProvider),
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

func (app *k8sApp) openRecoverDialog(ev ucx.UiEvent) {
	app.ShowRecoverDialog = true
	if !app.RecoverLoaded && !app.RecoverLoading {
		app.RecoverLoading = true
		app.RecoverStartDisabled = true
		go app.loadRecoverStacks()
	}
	ucx.AppUpdateUi(app)
}

func (app *k8sApp) closeRecoverDialog(ev ucx.UiEvent) {
	app.ShowRecoverDialog = false
	ucx.AppUpdateUi(app)
}

func (app *k8sApp) recoverDialog() ucx.UiNode {
	return ucx.DialogEx("recoverDialog", "Recover an existing cluster", app.ShowRecoverDialog).Children(
		app.recoverDialogBody(),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
			Sx(
				ucx.SxWidthAuto(),
				ucx.SxJustifyEnd,
				ucx.SxMx(-20),
				ucx.SxMb(-20),
				ucx.SxPx(20),
				ucx.SxPy(12),
				ucx.SxBackground("var(--dialogToolbar)"),
			).
			Children(
				ucx.ButtonEx("recoverCancel", "Cancel", ucx.ColorSecondaryMain, "", "", "").
					WithShortcutKey("n").
					On(ucx.UiEventClick, app.closeRecoverDialog),
				ucx.ButtonEx("recover", "Start recovery", ucx.ColorErrorMain, "", "", "").
					ButtonBusy("recoverBusy").
					ButtonDisabledWhen("recoverStartDisabled").
					WithShortcutKey("y").
					On(ucx.UiEventClick, app.recover),
			),
	).On(ucx.UiEventClose, app.closeRecoverDialog)
}

func (app *k8sApp) recoverDialogBody() ucx.UiNode {
	body := ucx.Box().Sx(ucx.SxMinHeight(200))

	if !app.RecoverLoaded {
		return body.Children(ucx.TextEx("recoverLoading", "Looking for clusters..."))
	}

	if app.RecoverError != "" {
		return body.Children(ucx.TextEx("recoverError", app.RecoverError))
	}

	if len(app.RecoverStacks) == 0 {
		return body.Children(ucx.TextEx("recoverEmpty", "No clusters are available for recovery."))
	}

	options := make([]ucx.Option, 0, len(app.RecoverStacks))
	for _, stack := range app.RecoverStacks {
		options = append(options, ucx.Option{
			Key:   stack.Id,
			Value: fmt.Sprintf("%s (%s)", stack.Name, stack.Id),
		})
	}

	return body.Children(
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("recoverClusterRow", "Cluster", "recoverStackId").
				FieldRowDescription("The cluster to recover. The recovery job runs on the cluster's state drive.").
				FieldRowRequired(true).
				Children(
					ucx.EnumSelectorNode("recoverClusterSelect", "recoverStackId", options),
				),
		),
	)
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
			app.RecoverLoading = false
			app.RecoverLoaded = true
			app.RecoverError = "Could not load the list of clusters. Reload the page to try again."
			app.RecoverStartDisabled = true
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
	app.RecoverLoading = false
	app.RecoverLoaded = true
	app.RecoverError = ""
	app.RecoverStacks = entries
	app.RecoverStartDisabled = len(entries) == 0
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
	if app.RecoverBusy || app.SwitchBusy || app.DeployBusy {
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
		if err == nil {
			app.ShowRecoverDialog = false
		}
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
	if app.DeployBusy || app.SwitchBusy {
		return
	}

	request := creatorDeployRequest(app)
	provider := app.ServiceProvider
	app.DeployBusy = true

	ucx.AppUpdateModelPatch(*app.Session(), map[string]ucx.Value{
		"deployBusy": ucx.VBool(true),
	})

	go func() {
		_, ok, failure := creatorDeploy(app, request, provider)

		if !ok && failure != "" {
			ucxsvc.UiSendFailure(app, failure)
		}

		app.mu.Lock()
		app.DeployBusy = false
		ucx.AppUpdateUi(app)
		app.mu.Unlock()
	}()
}

func (app *k8sApp) OnMessage(msg ucx.Frame) {
	if msg.Opcode != ucx.OpModelInput || msg.ModelInput.Path != "serviceProvider" {
		return
	}

	target := strings.TrimSpace(app.ServiceProvider)
	host := strings.TrimSpace(app.HostProvider)
	if target == "" || (host != "" && target == host) {
		return
	}

	if app.DeployBusy || app.RecoverBusy || app.SwitchBusy {
		if host != "" {
			app.ServiceProvider = host
		}
		ucx.AppUpdateUi(app)
		return
	}

	handoff := app.providerHandoff()
	app.SwitchBusy = true

	go func() {
		err := ucxsvc.RequestProviderSwitch(app, target, handoff)

		app.mu.Lock()
		if err == nil {
			app.SwitchBusy = false
			app.mu.Unlock()
			return
		}

		session := *app.Session()
		if session == nil || session.Context().Err() != nil {
			app.mu.Unlock()
			return
		}

		app.SwitchBusy = false
		if host != "" {
			app.ServiceProvider = host
		}
		ui := app.UserInterface()
		model := ucx.AppSnapshot(app)
		app.mu.Unlock()

		ucx.AppUpdateUiLocked(session, ui, model)
		ucxsvc.UiSendFailure(app, "Could not switch to the selected service provider: "+err.Error())
	}()
}

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
