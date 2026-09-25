package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

const managementMountDir = "/etc/ucloud-k8s/management"

const clusterFunctionalProbeInterval = 500 * time.Millisecond
const clusterFunctionalProbeTimeout = 10 * time.Second

func LocalKubeconfigPath() string {
	return filepath.Join(managementMountDir, "kubeconfig-internal")
}

func clusterFunctional() bool {
	client, err := K8sClientFromKubeconfig(LocalKubeconfigPath())
	if err != nil {
		return false
	}

	probeCtx, cancel := context.WithTimeout(context.Background(), clusterFunctionalProbeTimeout)
	defer cancel()
	_, err = client.ListNamespaces(probeCtx)
	return err == nil
}

func WaitForFunctionalCluster() {
	for !clusterFunctional() {
		time.Sleep(clusterFunctionalProbeInterval)
	}
}

func App() ucx.Application {
	return &stackUiApp{TargetDiskGb: 50}
}

type stackUiApp struct {
	mu        sync.Mutex      `ucx:"-"`
	session   *ucx.Session    `ucx:"-"`
	Stack     *ucxsvc.Stack   `ucx:"-"`
	k8sClient *K8sClient      `ucx:"-"`
	poller    *resourcePoller `ucx:"-"`

	clientWaiterStarted bool `ucx:"-"`

	RoutePath string
	Machine   accapi.ProductReference

	ClusterProvider string

	TargetGroup     string
	TargetDiskGb    int
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

	if app.ActiveType == "" {
		app.ActiveType = "nodes"
	}

	if !app.clientWaiterStarted && app.session != nil {
		app.clientWaiterStarted = true
		app.startK8sClientWhenReady(app.session, app.ActiveType)
	}
}

func (app *stackUiApp) startK8sClientWhenReady(session *ucx.Session, activeType string) {
	sessionCtx := session.Context()

	go func() {
		for {
			if sessionCtx.Err() != nil {
				return
			}

			client, err := K8sClientFromKubeconfig(LocalKubeconfigPath())
			if err == nil {
				probeCtx, cancel := context.WithTimeout(sessionCtx, 10*time.Second)
				namespaces, probeErr := client.ListNamespaces(probeCtx)
				cancel()
				if probeErr == nil {
					app.mu.Lock()
					if app.k8sClient == nil {
						app.k8sClient = client
						app.Namespaces = namespaces
						log.Info("k8s-app: k8s client ready from %s", LocalKubeconfigPath())

						app.poller = newResourcePoller(client, session, activeType, app.nodeJobIds, func() {
							app.mu.Lock()
							ucx.AppUpdateUi(app)
							app.mu.Unlock()
						})
					}
					ucx.AppUpdateUi(app)
					app.mu.Unlock()
					return
				}

				err = probeErr
			}

			if sessionCtx.Err() != nil {
				return
			}

			log.Info("k8s-app: k8s API not ready yet (%s), retrying", err)

			app.mu.Lock()
			ucx.AppUpdateUi(app)
			app.mu.Unlock()

			select {
			case <-sessionCtx.Done():
				return
			case <-time.After(15 * time.Second):
			}
		}
	}()
}

func (app *stackUiApp) clusterNodeMessage(record *shared.ClusterRecord) string {
	starting := 0
	running := 0
	for _, node := range record.Nodes {
		if node.JobId == "" {
			running++
			continue
		}

		job, err := ucxsvc.JobRetrieve(app.Stack, node.JobId)
		if err != nil {
			starting++
			continue
		}

		if job.Status.State.IsFinal() {
			running++
		} else {
			starting++
		}
	}

	if starting > 0 {
		return fmt.Sprintf(
			"The cluster is starting. %d of %d nodes are not running yet. See the node logs for details.",
			starting,
			len(record.Nodes),
		)
	}
	return ""
}

func (app *stackUiApp) loadNamespaces() {
	client := app.k8sClient
	if client == nil {
		return
	}

	session := app.session
	if session == nil {
		return
	}

	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
		defer cancel()

		names, err := client.ListNamespaces(ctx)
		if err != nil {
			log.Warn("k8s-app: failed to list namespaces: %s", err)
			return
		}

		app.mu.Lock()
		app.Namespaces = names
		ucx.AppUpdateUi(app)
		app.mu.Unlock()
	}()
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

	if app.ClusterProvider != "" && app.Machine.Provider != app.ClusterProvider {
		ucxsvc.UiSendFailure(app, "The machine must come from the provider that runs the cluster: "+app.ClusterProvider)
		return
	}

	diskGb := app.TargetDiskGb
	if diskGb < 10 {
		ucxsvc.UiSendFailure(app, "The node needs a disk size of at least 10 GB")
		return
	}

	vmId, ok := shared.ClusterAddNode(app, app.Stack, trimmedGroup, app.Machine, diskGb, nil)
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created new %s VM %s!", trimmedGroup, vmId))
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
	if app.k8sClient == nil {
		return append([]ucx.UiNode{}, app.pageProvisioning()...)
	}

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
		bottom = append(bottom, app.resourceDetailBottomNode(detail))
	} else {
		var tableActions []ucx.ResourceTableAction
		if app.ActiveType == "nodes" {
			tableActions = append(tableActions,
				ucx.ResourceTableAction{
					Id:    "copyNodeName",
					Label: "Copy node name",
					Icon:  ucx.IconCopy,
					Kind:  ucx.ResourceTableActionCopyText,
				},
				ucx.ResourceTableAction{
					Id:    "goToJob",
					Label: "Go to job",
					Icon:  ucx.IconHeroArrowTopRightOnSquare,
				},
			)
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
		}).On(ucx.UiEventAction, func(ev ucx.UiEvent) {
			app.handleRowAction(ev)
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

	pageChildren := []ucx.UiNode{
		ucx.Toolbar().Children(
			ucx.H2("Kubernetes cluster"),
			ucx.Button("downloadKubernetesConfig", "Download kubeconfig", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackDownloadFile(app.Stack, shared.KubernetesConfigurationFileName)
			}),
			ucx.Button("copyKubernetesToken", "Copy k8s token", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackCopyFile(app.Stack, shared.KubernetesTokenFileName)
			}),
			ucx.Button("copyHeadlampToken", "Copy Headlamp token", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackCopyFile(app.Stack, shared.KubernetesTokenFileName)
			}),
			ucx.Link("control").Children(ucx.Button("add-machine", "Add machine", ucx.ColorSecondaryMain)),
		),
	}

	pageChildren = append(pageChildren, ucx.BrowserLayout(props))

	return []ucx.UiNode{ucx.Surface().
		Sx(
			ucx.SxHeightRaw("calc(100vh - 288px)"),
			ucx.SxMinHeight(480),
		).
		Children(pageChildren...)}
}

func (app *stackUiApp) pageProvisioning() []ucx.UiNode {
	messages := []ucx.UiNode{}

	if record, ok := app.readClusterRecord(); ok && record.FailureReason != "" {
		messages = append(messages, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		for _, pending := range record.PendingCleanup {
			parts := []string{pending.Hostname}
			if pending.JobId != "" {
				parts = append(parts, "job "+pending.JobId)
			}
			if pending.ReservationId != "" {
				parts = append(parts, "reservation "+pending.ReservationId)
			}
			messages = append(messages, ucx.Text(
				"These resources need manual cleanup: "+strings.Join(parts, ", "),
			))
		}
	}

	message := ""
	if record, ok := app.readClusterRecord(); ok {
		message = app.clusterNodeMessage(&record)
	}

	if message == "" {
		message = "The cluster control plane is starting. The dashboard loads when the Kubernetes API is ready."
	}

	messages = append(messages, ucx.Text(message))

	return []ucx.UiNode{ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Kubernetes cluster"),
		),
		ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(messages...),
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
	return ucx.CodeBoundEx("resourceYaml", "resourceYaml").WithLang("yaml").WithStretch()
}

func (app *stackUiApp) resourceDetailBottomNode(detail string) ucx.UiNode {
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

	return ucx.Toolbar().Children(
		ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.RouterPushPage(app, "")
		}),
		ucx.Box(),
		ucx.Text(name).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		ucx.Text(namespace).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
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

func (app *stackUiApp) handleRowAction(ev ucx.UiEvent) {
	if ev.Event != string(ucx.UiEventAction) || ev.Value.Kind != ucx.ValueObject {
		return
	}

	actionId := ucx.ValueAsString(ev.Value.Object["actionId"])
	rowKey := ucx.ValueAsString(ev.Value.Object["rowKey"])
	if actionId != "goToJob" || rowKey == "" {
		return
	}

	nodeName, ok := app.poller.nodeNameForRowKey(rowKey)
	if !ok {
		return
	}

	jobId := app.nodeJobIds()[nodeName]
	if jobId == "" {
		return
	}

	ucxsvc.OpenUrl(app, "/jobs/properties/"+jobId)
}

func (app *stackUiApp) nodeJobIds() map[string]string {
	record, ok := app.readClusterRecord()
	if !ok {
		return map[string]string{}
	}

	result := make(map[string]string, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.Hostname != "" && node.JobId != "" {
			result[node.Hostname] = node.JobId
		}
	}
	return result
}

func (app *stackUiApp) readClusterRecord() (shared.ClusterRecord, bool) {
	data, err := os.ReadFile(filepath.Join(managementMountDir, "cluster.json"))
	if err != nil {
		return shared.ClusterRecord{}, false
	}

	var record shared.ClusterRecord
	if err := json.Unmarshal(data, &record); err != nil {
		return shared.ClusterRecord{}, false
	}

	return record, true
}

func (app *stackUiApp) nodeGroupsFromRecord() []string {
	record, ok := app.readClusterRecord()
	if !ok {
		return nil
	}

	groups := make([]string, 0, len(record.Pools))
	for _, pool := range record.Pools {
		name := strings.TrimSpace(pool.Name)
		if name != "" {
			groups = append(groups, name)
		}
	}

	sort.Strings(groups)
	return groups
}

// "Add new VM" page
func (app *stackUiApp) pageControl() []ucx.UiNode {
	poolGroups := app.nodeGroupsFromRecord()

	record, recordOk := app.readClusterRecord()
	if recordOk && record.MachineProvider != "" {
		app.ClusterProvider = record.MachineProvider
	}

	options := make([]ucx.Option, 0, len(poolGroups))
	for _, group := range poolGroups {
		options = append(options, ucx.Option{Key: group, Value: group})
	}

	surface := ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Add new virtual machine to the stack"),
			ucx.Link("").Children(ucx.Text("Back to overview")),
		),
	)

	if recordOk && record.Phase != "created" {
		content := []ucx.UiNode{ucx.Text(
			"New nodes cannot be added while the cluster is in state " + record.Phase +
				". The cluster must be in the created state.",
		)}
		if record.FailureReason != "" {
			content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		}
		return []ucx.UiNode{surface.Children(content...)}
	}

	if len(options) == 0 {
		return []ucx.UiNode{surface.Children(ucx.Text("No node pools are available."))}
	}

	if app.TargetGroup == "" {
		app.TargetGroup = options[0].Key
	}

	form := ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		app.addMachineToGroup(app.TargetGroup)
		ucx.AppUpdateUi(app)
	}).Children(
		ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
			ucx.Select("targetGroup", "Node pool", "targetGroup", options),
			ucx.MachineTypeSelector(
				"machine",
				"Machine product",
				"machine",
				ucx.MachineCapabilityVm,
			).MachineSelectorProviderBindPath("clusterProvider").MachineSelectorProviderOnly(true),
			ucx.InputNumber("targetDiskGb", "Disk size (GB)", "targetDiskGb", 10, 1024),
			ucx.SubmitButton("addToStack", "Add machine to stack", ucx.ColorSecondaryMain),
		),
	)

	return []ucx.UiNode{surface.Children(
		ucx.Text("Select a machine and submit to add a new node to the pool."),
		form,
	)}
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

	client := app.k8sClient
	if client == nil {
		app.ResourceYaml = "Kubernetes client is not available"
		return
	}

	session := app.session
	if session == nil {
		app.ResourceYaml = "Kubernetes client is not available"
		return
	}

	target := app.ResourceDetail

	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()

		yamlText, err := client.YamlForUid(ctx, def, namespace, name)
		if err != nil {
			yamlText = fmt.Sprintf("Failed to fetch YAML: %s", err)
		}

		app.mu.Lock()
		if app.ResourceDetail == target {
			app.ResourceYaml = yamlText
			ucx.AppUpdateUi(app)
		}
		app.mu.Unlock()
	}()
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
