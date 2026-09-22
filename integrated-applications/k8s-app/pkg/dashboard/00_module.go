package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

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

	session := *app.Session()
	ucx.RpcHandle(session, ResourceYamlRpc, app.handleResourceYaml)
	ucx.RpcHandle(session, ListNamespacesRpc, app.handleListNamespaces)
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
		children = util.Combined(children, app.pageControl())
	default:
		children = util.Combined(children, app.pageResources())
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

	typeOptions := make([]ucx.ResourceTypeOption, 0, len(typeDefs))
	for _, def := range typeDefs {
		typeOptions = append(typeOptions, ucx.ResourceTypeOption{
			Id:         def.Id,
			Label:      def.Label,
			Aliases:    def.Aliases,
			Group:      def.Group,
			HasYaml:    def.HasYaml,
			Namespaced: def.Namespaced,
		})
	}

	return []ucx.UiNode{ucx.Surface().
		Sx(
			ucx.SxHeightRaw("calc(100vh - 288px)"),
			ucx.SxMinHeight(480),
		).
		Children(
			ucx.Toolbar().Children(
				ucx.H2("Kubernetes cluster"),
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
			ucx.ResourceTable("resourceTable", "activeType", "activeNamespace", "resourceDetail", typeOptions),
		)}
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

		changed := false
		if routeDetail != app.ResourceDetail {
			app.ResourceDetail = routeDetail
			changed = true
		}
		if app.ResourceDetail != app.prevDetail {
			app.prevDetail = app.ResourceDetail
			app.loadResourceYaml(app.ResourceDetail)
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

func (app *stackUiApp) handleResourceYaml(ctx context.Context, request resourceYamlRequest) (resourceYamlResponse, error) {
	if app.k8sClient == nil {
		return resourceYamlResponse{}, fmt.Errorf("kubernetes client is not available")
	}

	def, ok := app.resolveType(request.Type)
	if !ok {
		return resourceYamlResponse{}, fmt.Errorf("unknown resource type %q", request.Type)
	}

	yamlText, err := app.k8sClient.YamlForUid(ctx, def, request.Namespace, request.Name)
	if err != nil {
		return resourceYamlResponse{}, err
	}

	return resourceYamlResponse{Yaml: yamlText}, nil
}

func (app *stackUiApp) handleListNamespaces(ctx context.Context, request util.Empty) (namespaceListResponse, error) {
	if app.k8sClient == nil {
		return namespaceListResponse{}, fmt.Errorf("kubernetes client is not available")
	}

	names, err := app.k8sClient.ListNamespaces(ctx)
	if err != nil {
		return namespaceListResponse{}, err
	}

	return namespaceListResponse{Namespaces: names}, nil
}

type resourceYamlRequest struct {
	Type      string
	Namespace string
	Name      string
}

type resourceYamlResponse struct {
	Yaml string
}

type namespaceListResponse struct {
	Namespaces []string
}

var ResourceYamlRpc = ucx.Rpc[resourceYamlRequest, resourceYamlResponse]{
	CallName: "k8s.resourceYaml",
}

var ListNamespacesRpc = ucx.Rpc[util.Empty, namespaceListResponse]{
	CallName: "k8s.listNamespaces",
}
