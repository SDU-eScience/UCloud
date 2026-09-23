package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"sort"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func App() ucx.Application {
	return &stackUiApp{}
}

type stackUiApp struct {
	mu        sync.Mutex      `ucx:"-"`
	session   *ucx.Session    `ucx:"-"`
	Stack     *ucxsvc.Stack   `ucx:"-"`
	k8sClient *K8sClient      `ucx:"-"`
	poller    *resourcePoller `ucx:"-"`

	Jobs      []orcapi.Job
	RoutePath string
	Machine   accapi.ProductReference

	TargetGroup     string
	ActiveType      string
	ActiveNamespace string
	ResourceDetail  string
	ResourceYaml    string
	Namespaces      []string

	prevDetail string `ucx:"-"`
}

func (app *stackUiApp) Mutex() *sync.Mutex     { return &app.mu }
func (app *stackUiApp) Session() **ucx.Session { return &app.session }

func (app *stackUiApp) OnInit() {
	app.loadStackJobs()
}

func (app *stackUiApp) OnSysHello(payload string) {
	var request orcapi.AppUcxConnectJobProviderRequest
	if err := json.Unmarshal([]byte(payload), &request); err != nil {
		return
	}

	stack, ok := ucxsvc.StackFromJob(app, request.Job)
	if !ok {
		return
	}

	app.Stack = stack
	ucxsvc.UiSendSuccess(app, "Main control plane is ready!")

	client, err := K8sClientFromKubeconfig(KubeconfigPath())
	if err != nil {
		log.Warn("k8s-app: failed to create k8s client: %s", err)
		return
	}
	app.k8sClient = client
	log.Info("k8s-app: k8s client ready from %s", KubeconfigPath())

	if app.ActiveType == "" {
		app.ActiveType = "nodes"
	}
	app.poller = newResourcePoller(client, *app.Session(), app.ActiveType, func() {
		stateMu := app.Mutex()
		stateMu.Lock()
		ucx.AppUpdateUi(app)
		stateMu.Unlock()
	})
	log.Info("k8s-app: poller started, active type %s", app.ActiveType)

	app.loadNamespaces()
}

func (app *stackUiApp) loadNamespaces() {
	if app.k8sClient == nil {
		return
	}

	names, err := app.k8sClient.ListNamespaces(context.Background())
	if err != nil {
		log.Warn("k8s-app: failed to list namespaces: %s", err)
		return
	}

	app.Namespaces = names
}

func (app *stackUiApp) loadStackJobs() {
	session := *app.Session()
	if session == nil {
		return
	}

	result, err := ucxapi.JobsBrowse.Invoke(
		session,
		orcapi.JobsBrowseRequest{
			ItemsPerPage: 250,
			JobFlags: orcapi.JobFlags{
				IncludeParameters: true,
			},
		},
	)
	if err != nil {
		return
	}

	app.Jobs = result.Items
}

// create a VM and add it to the given group
func (app *stackUiApp) addMachineToGroup(group string) {
	trimmedGroup := strings.TrimSpace(group)
	if trimmedGroup == "" {
		ucxsvc.UiSendFailure(app, "Please provide a node group")
		return
	}

	if app.Machine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine product before adding a node")
		return
	}

	existingJobs := shared.ClusterNodesByGroup(app.Jobs)[group]
	vmId, ok := shared.ClusterAddNode(app, app.Stack, shared.ClusterNodeSpec{
		Group:   group,
		Index:   len(existingJobs) + 1,
		Machine: app.Machine,
	}, existingJobs)
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created new %s VM %s!", group, vmId))
	ucxsvc.RouterPushPage(app, "")
}

func (app *stackUiApp) UserInterface() ucx.UiNode {
	children := []ucx.UiNode{
		ucx.Router("routePath"),
	}

	switch {
	case app.RoutePath == "control":
		children = append(children, app.pageControl()...)
	default:
		children = append(children, app.pageResources()...)
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).
		Sx(ucx.SxP(4)).
		Children(children...)
}

func (app *stackUiApp) pageResources() []ucx.UiNode {
	typeDefs := ResourceTypes()
	if app.poller != nil {
		typeDefs = append(typeDefs, app.poller.customTypesSnapshot()...)
	}

	navItems := make([]ucx.NavItem, 0, 8)
	groups := map[string][]ucx.NavItemChild{}
	for _, def := range typeDefs {
		group := def.Group
		if group == "" {
			group = "Other"
		}
		groups[group] = append(groups[group], ucx.NavItemChild{
			Id:      def.Id,
			Label:   def.Label,
			Aliases: def.Aliases,
		})
	}
	for group, children := range groups {
		navItems = append(navItems, ucx.NavItem{
			Id:       "group:" + group,
			Label:    group,
			Children: children,
		})
	}
	sort.Slice(navItems, func(i, j int) bool {
		return navItems[i].Label < navItems[j].Label
	})

	detail := app.ResourceDetail
	inDetail := detail != ""

	activeDef, _ := app.resolveType(app.ActiveType)
	namespaced := activeDef.Namespaced

	var main []ucx.UiNode
	var bottom []ucx.UiNode
	if inDetail {
		main = []ucx.UiNode{app.resourceDetailNode(detail)}
	} else {
		var tableActions []ucx.ResourceTableAction
		if app.ActiveType == "nodes" {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:    "copyNodeName",
				Label: "Copy node name",
				Icon:  ucx.IconCopy,
				Kind:  ucx.ResourceTableActionCopyText,
			})
		}

		main = []ucx.UiNode{ucx.ResourceTable(ucx.ResourceTableProps{
			Id:               "resourceTable",
			TableId:          app.resourceStreamId(),
			StateKey:         app.ActiveType,
			ViewId:           "k8sResources",
			EmptyMessage:     "No resources found.",
			HideGroupHeaders: namespaced,
			Actions:          tableActions,
		}).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			typeId, namespace, name := rowActivationValue(ev.Value)
			app.handleRowActivated(typeId, namespace, name)
		})}

		bottom = append(bottom, ucx.TableFilter("resourceFilter", app.ActiveType))
		if namespaced {
			bottom = append(bottom, app.namespaceSelectorNode())
		}
		bottom = append(bottom, ucx.Box().Sx(ucx.SxFlexGrow(1)))
		bottom = append(bottom, ucx.TableCount("resourceCount", app.ActiveType).WithTitle(app.activeTypeLabel(typeDefs)))
	}

	props := ucx.BrowserLayoutProps{
		Sidebar: ucx.BrowserSidebar(
			ucx.NavTreeEx("resourceNav", "activeType", navItems).On(ucx.UiEventActivate, func(ev ucx.UiEvent) {
				app.selectResourceType(ucx.ValueAsString(ev.Value))
			}),
		),
		Content: main,
	}

	if inDetail {
		props.EscapePath = ""
	} else {
		props.EscapeDisabled = true
	}

	if len(bottom) > 0 {
		props.Bottom = ucx.BrowserBottom(bottom...)
		props.HasBottom = true
	}

	return []ucx.UiNode{ucx.Surface().
		Sx(
			ucx.SxHeightRaw("calc(100vh - 288px)"),
			ucx.SxMinHeight(480),
		).
		Children(
			ucx.Toolbar().Children(
				ucx.H2("Kubernetes cluster 2"),
				ucx.Button("downloadKubernetesConfig", "Download kubeconfig", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					ucxsvc.StackDownloadFile(app.Stack, shared.KubernetesConfigurationFileName)
				}),
				ucx.Button("copyKubernetesToken", "Copy k8s token", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					ucxsvc.StackCopyFile(app.Stack, shared.KubernetesTokenFileName)
				}),
				ucx.Button("copyHeadlampToken", "Copy Headlamp token", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					ucxsvc.StackCopyFile(app.Stack, shared.HeadlampTokenFileName)
				}),
				ucx.Link("control").Children(ucx.Button("add-machine", "Add machine", ucx.ColorSecondaryMain)),
			),
			ucx.BrowserLayout(props),
		)}
}

func (app *stackUiApp) resourceStreamId() string {
	def, _ := app.resolveType(app.ActiveType)
	return resourceDatasetId(resourceSelection{
		typeId:    app.ActiveType,
		namespace: app.ActiveNamespace,
		def:       def,
	})
}

func (app *stackUiApp) activeTypeLabel(typeDefs []ResourceTypeDef) string {
	for _, def := range typeDefs {
		if def.Id == app.ActiveType {
			return def.Label
		}
	}
	return ""
}

func (app *stackUiApp) selectResourceType(typeId string) {
	if typeId == "" {
		return
	}

	if typeId != app.ActiveType {
		app.ActiveType = typeId
		if app.poller != nil {
			app.poller.SetActiveType(typeId)
		}
		if def, ok := app.resolveType(typeId); ok && def.Namespaced {
			app.loadNamespaces()
		}
	}

	if strings.HasPrefix(app.RoutePath, "detail/") {
		app.ResourceDetail = ""
		app.prevDetail = ""
		app.ResourceYaml = ""
		ucxsvc.RouterPushPage(app, "")
	}

	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) namespaceSelectorNode() ucx.UiNode {
	options := make([]ucx.Option, 0, len(app.Namespaces)+1)
	options = append(options, ucx.Option{Key: "", Value: "All namespaces"})
	for _, namespace := range app.Namespaces {
		options = append(options, ucx.Option{Key: namespace, Value: namespace})
	}

	return ucx.Select("namespaceSelect", "", "activeNamespace", options).Sx(ucx.SxWidth(280))
}

func (app *stackUiApp) resourceDetailNode(detail string) ucx.UiNode {
	parts := strings.Split(detail, "/")
	typeId := parts[0]
	namespace := ""
	name := ""
	if len(parts) > 1 {
		namespace = parts[1]
	}
	if len(parts) > 2 {
		name = parts[2]
	}

	label := typeId
	if def, ok := app.resolveType(typeId); ok {
		label = def.Label
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
		Children(
			ucx.CodeBoundEx("resourceYaml", "resourceYaml").WithLang("yaml").WithStretch(),
			ucx.Toolbar().Children(
				ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					ucxsvc.RouterPushPage(app, "")
				}),
				ucx.Box(),
				ucx.Text(name).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
				ucx.Text(namespace).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
				ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
			),
		)
}

func (app *stackUiApp) handleRowActivated(tableId string, namespace string, name string) {
	if tableId == "" || name == "" {
		return
	}

	if _, ok := app.resolveType(tableId); !ok {
		return
	}

	app.ResourceDetail = tableId + "/" + namespace + "/" + name
	app.prevDetail = app.ResourceDetail
	app.loadResourceYaml(app.ResourceDetail)
	ucxsvc.RouterPushPage(app, "detail/"+url.PathEscape(tableId)+"/"+url.PathEscape(namespace)+"/"+url.PathEscape(name))
	ucx.AppUpdateUi(app)
}

// "Add new VM" page
func (app *stackUiApp) pageControl() []ucx.UiNode {
	children := []ucx.UiNode{ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Add new virtual machine to the stack"),
			ucx.Link("").Children(ucx.Text("Back to overview")),
		),
		ucx.Text("Select a machine and submit to add a new node to the pool."),
	)}

	groups := shared.ClusterNodeGroups(app.Jobs)
	poolGroups := make([]string, 0, len(groups))
	for _, group := range groups {
		if group == shared.GroupControlPlane {
			continue
		}
		poolGroups = append(poolGroups, group)
	}

	if len(poolGroups) == 0 {
		poolGroups = []string{shared.GroupWorker}
	}

	options := make([]ucx.Option, 0, len(poolGroups))
	for _, group := range poolGroups {
		options = append(options, ucx.Option{Key: group, Value: group})
	}

	children = append(children, ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		app.addMachineToGroup(app.TargetGroup)
		ucx.AppUpdateUi(app)
	}).Children(
		ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
			ucx.Select("targetGroup", "Node pool", "targetGroup", options),
			ucx.MachineTypeSelector(
				"machine",
				"Machine product",
				"machine",
				ucx.MachineCapabilityDocker,
				ucx.MachineCapabilityVm,
			),
			ucx.SubmitButton("addToStack", "Add machine to stack", ucx.ColorSecondaryMain),
		),
	))

	return children
}

func (app *stackUiApp) OnMessage(frame ucx.Frame) {
	switch frame.Opcode {
	case ucx.OpModelInput:
		app.RoutePath = strings.TrimSpace(app.RoutePath)

		if app.poller != nil {
			app.poller.SetActiveType(app.ActiveType)
			app.poller.SetActiveNamespace(app.ActiveNamespace)
		}

		routeDetail := ""
		if strings.HasPrefix(app.RoutePath, "detail/") {
			routeDetail = detailFromRoute(app.RoutePath)
		}

		changed := routeDetail != app.ResourceDetail
		app.ResourceDetail = routeDetail
		if app.ResourceDetail != app.prevDetail {
			app.prevDetail = app.ResourceDetail
			app.loadResourceYaml(app.ResourceDetail)
			changed = true
		}

		if frame.ModelInput.Path == "activeNamespace" {
			changed = true
		}

		if changed {
			ucx.AppUpdateUi(app)
		}
	}
}

func detailFromRoute(routePath string) string {
	parts := strings.Split(strings.TrimPrefix(routePath, "detail/"), "/")
	if len(parts) != 3 {
		return ""
	}

	typeId, err := url.PathUnescape(parts[0])
	if err != nil {
		return ""
	}
	namespace, err := url.PathUnescape(parts[1])
	if err != nil {
		return ""
	}
	name, err := url.PathUnescape(parts[2])
	if err != nil {
		return ""
	}

	return typeId + "/" + namespace + "/" + name
}

func rowActivationValue(value ucx.Value) (string, string, string) {
	if value.Kind != ucx.ValueObject {
		return "", "", ""
	}

	tableId := ucx.ValueAsString(value.Object["stateKey"])
	if tableId == "" {
		tableId = ucx.ValueAsString(value.Object["tableId"])
	}
	group := ucx.ValueAsString(value.Object["group"])

	cells := ""
	if rawCells, ok := value.Object["cells"]; ok && rawCells.Kind == ucx.ValueList && len(rawCells.List) > 0 {
		cells = ucx.ValueAsString(rawCells.List[0])
	}

	return tableId, group, cells
}

func (app *stackUiApp) loadResourceYaml(detail string) {
	app.ResourceYaml = ""

	if detail == "" {
		return
	}

	parts := strings.Split(detail, "/")
	if len(parts) != 3 {
		return
	}

	typeId := parts[0]
	namespace := parts[1]
	name := parts[2]

	def, ok := app.resolveType(typeId)
	if !ok {
		app.ResourceYaml = fmt.Sprintf("Unknown resource type: %s", typeId)
		return
	}

	if app.k8sClient == nil {
		app.ResourceYaml = "Kubernetes client is not available"
		return
	}

	yamlText, err := app.k8sClient.YamlForUid(context.Background(), def, namespace, name)
	if err != nil {
		app.ResourceYaml = fmt.Sprintf("Failed to fetch YAML: %s", err)
		return
	}

	app.ResourceYaml = yamlText
}

func (app *stackUiApp) resolveType(typeId string) (ResourceTypeDef, bool) {
	if def, ok := ResourceType(typeId); ok {
		return def, true
	}
	if app.poller != nil {
		def := app.poller.customTypeById(typeId)
		return def, def.Id != ""
	}
	return ResourceTypeDef{}, false
}
