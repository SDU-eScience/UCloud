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
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

const managementMountDir = "/etc/ucloud-k8s/management"

const navHomeId = "home"

const provisioningRowKeyPrefix = "provisioning:"

func LocalKubeconfigPath() string {
	return filepath.Join(managementMountDir, "kubeconfig-internal")
}

func App() ucx.Application {
	app := &stackUiApp{
		TargetDiskGb:     50,
		ActiveType:       navHomeId,
		provisioningKick: make(chan util.Empty, 1),
	}
	return app
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

	TargetGroup  string
	TargetDiskGb int
	TargetCount  int
	NewPoolName  string
	AddBusy      bool

	ActiveType      string
	ActiveNamespace string
	ResourceDetail  string
	ResourceYaml    string
	Namespaces      []string
	LogJobId        string

	MaintenanceTimeoutSeconds          int
	MaintenanceDrain                   bool
	MaintenanceCordon                  bool
	MaintenanceDeleteVolatilePods      bool
	MaintenanceBypassDisruptionBudgets bool
	MaintenanceForceDelete             bool
	MaintenanceBusy                    bool
	MaintenanceTargetRelease           string

	maintenanceNodeName        string `ucx:"-"`
	maintenanceNodeUid         string `ucx:"-"`
	maintenanceRetryOptionsFor string `ucx:"-"`
	maintenanceMode            string `ucx:"-"`

	stackInfo   *ucxapi.StackInfoResponse `ucx:"-"`
	headlampUrl string                    `ucx:"-"`

	ShowHeadlampDialog bool   `ucx:"-"`
	headlampToken      string `ucx:"-"`

	clusterHealth *NodeHealth `ucx:"-"`

	provisioningCache []provisioningEntry `ucx:"-"`
	provisioningKick  chan util.Empty     `ucx:"-"`

	prevDetail string `ucx:"-"`
}

type provisioningEntry struct {
	Hostname string
	Group    string
	Status   string
	JobId    string
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
		app.ActiveType = navHomeId
	}

	if !app.clientWaiterStarted && app.session != nil {
		app.clientWaiterStarted = true
		go app.provisioningWatcher(app.session)
		app.startK8sClientWhenReady(app.session, app.ActiveType)
	}

	go app.refreshStackInfo()
}

func (app *stackUiApp) refreshStackInfo() {
	session := app.session
	if session == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	info, err := ucxapi.StackInfo.InvokeEx(ctx, session, util.Empty{})
	if err != nil {
		log.Warn("k8s-app: failed to fetch stack info: %s", err)
		return
	}

	headlampUrl := app.lookupHeadlampUrl(ctx, session)

	app.mu.Lock()
	changed := app.stackInfo == nil || *app.stackInfo != info || app.headlampUrl != headlampUrl
	if changed {
		app.stackInfo = &info
		app.headlampUrl = headlampUrl
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()
}

func (app *stackUiApp) lookupHeadlampUrl(ctx context.Context, session *ucx.Session) string {
	links, err := ucxapi.PublicLinksBrowse.InvokeEx(ctx, session, orcapi.IngressesBrowseRequest{
		ItemsPerPage: 250,
	})
	if err != nil {
		return ""
	}

	for _, link := range links.Items {
		if link.Specification.Target.Present &&
			link.Specification.Target.Value.Port == "headlamp" {
			return "https://" + link.Specification.Domain
		}
	}

	return ""
}

func (app *stackUiApp) provisioningSnapshot() []provisioningEntry {
	return app.provisioningCache
}

func (app *stackUiApp) activeTypeIsHome() bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	return app.ActiveType == navHomeId
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

						app.poller = newResourcePoller(client, session, activeType, app.nodeJobIds, app.provisioningResourceRows, func() {
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

func (app *stackUiApp) UserInterface() ucx.UiNode {
	children := []ucx.UiNode{
		ucx.Router("routePath"),
	}

	switch {
	case app.RoutePath == "control/new-pool":
		children = append(children, app.pageAddPool()...)
	case strings.HasPrefix(app.RoutePath, "maintenance"):
		children = append(children, maintenancePage(app)...)
	case app.RoutePath == "control" || strings.HasPrefix(app.RoutePath, "control/"):
		app.TargetGroup = groupFromRoute(app.RoutePath)
		children = append(children, app.pageAddMachine()...)
	default:
		children = append(children, app.pageResources()...)
	}

	if app.ShowHeadlampDialog {
		children = append(children, ucx.DialogEx("headlampDialog", "Open Headlamp", true).Children(
			ucx.TextEx("", "Use the following token to sign in to Headlamp.").Sx(
				ucx.SxColor(ucx.ColorTextSecondary),
			),
			ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
				Sx(ucx.SxAlignItemsCenter).
				Children(
					ucx.InputSecretEx("headlampToken", "", app.headlampToken, "").Sx(ucx.SxFlexGrow(1)),
					ucx.CopyButtonEx("headlampCopyToken", app.headlampToken).WithTooltip("Copy token"),
				),
			ucx.ButtonEx("headlampContinue", "Continue", ucx.ColorPrimaryMain, "", "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				app.closeHeadlampDialog()
				ucxsvc.OpenUrl(app, app.headlampUrl)
			}),
		).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
			app.closeHeadlampDialog()
		}))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).
		Sx(
			ucx.SxP(4),
			ucx.SxHeightRaw("100%"),
			ucx.SxBoxSizing("border-box"),
		).
		Children(children...)
}

func groupFromRoute(routePath string) string {
	rest := strings.TrimPrefix(routePath, "control")
	rest = strings.TrimPrefix(rest, "/")
	return strings.TrimSpace(rest)
}

func (app *stackUiApp) allTypeDefs() []ResourceTypeDef {
	typeDefs := ResourceTypes()
	if app.poller != nil {
		typeDefs = append(typeDefs, app.poller.customTypesSnapshot()...)
	}
	return typeDefs
}

func (app *stackUiApp) resourceNavItems() []ucx.NavItem {
	typeDefs := app.allTypeDefs()

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

	navItems = append([]ucx.NavItem{{
		Id:      navHomeId,
		Label:   "Home",
		Aliases: []string{"home", "overview"},
	}}, navItems...)
	if len(navItems) > 1 {
		navItems[1].SeparatorBefore = true
	}

	return navItems
}

func (app *stackUiApp) homeMetric(title string, valueNodes ...ucx.UiNode) []ucx.UiNode {
	return app.homeMetricEx(title, nil, valueNodes...)
}

func (app *stackUiApp) homeMetricEx(title string, titleNodes []ucx.UiNode, valueNodes ...ucx.UiNode) []ucx.UiNode {
	headerChildren := []ucx.UiNode{
		ucx.TextEx("", title).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
			ucx.SxFontSize(12),
		),
	}
	headerChildren = append(headerChildren, titleNodes...)

	return []ucx.UiNode{
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).
			Sx(ucx.SxAlignItemsCenter).
			Children(headerChildren...),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).
			Sx(ucx.SxAlignItemsCenter).
			Children(valueNodes...),
	}
}

func homeMetricText(value string, color ucx.Color) ucx.UiNode {
	return ucx.TextEx("", value).Sx(
		ucx.SxFontWeight("600"),
		ucx.SxColor(color),
		ucx.SxFontSize(16),
	)
}

func (app *stackUiApp) homeMetricsRow(metrics ...[]ucx.UiNode) ucx.UiNode {
	cells := make([]ucx.UiNode, 0, len(metrics))
	for _, metric := range metrics {
		cells = append(cells, ucx.Box().Sx(
			ucx.SxFlexBasis("200px"),
			ucx.SxFlexGrow(1),
		).Children(metric...))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 24}).
		Sx(ucx.SxFlexWrapWrap).
		Children(cells...)
}

func (app *stackUiApp) pageHomeContent() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()

	clusterState := "Unknown"
	clusterStateColor := ucx.ColorTextSecondary
	if recordOk {
		clusterState = record.Phase
		clusterStateColor = ucx.ColorSuccessMain
		if record.Phase != "created" {
			clusterStateColor = ucx.ColorWarningMain
		}
		if record.FailureReason != "" {
			clusterState = record.Phase + ": " + record.FailureReason
			clusterStateColor = ucx.ColorErrorMain
		}
	}

	if health := app.clusterHealth; health != nil && recordOk && record.Phase == "created" {
		if health.NotReady == 0 && health.Unknown == 0 && health.Total > 0 {
			clusterState = "Healthy"
		} else {
			clusterState = "Unhealthy"
		}
		clusterStateColor = ucx.ColorSuccessMain
		if clusterState == "Unhealthy" {
			clusterStateColor = ucx.ColorErrorMain
		}
	}

	nodesTotal := len(record.Nodes)
	nodesReady := nodesTotal - len(app.provisioningSnapshot())
	if nodesReady < 0 {
		nodesReady = 0
	}
	if health := app.clusterHealth; health != nil {
		nodesTotal = health.Total
		nodesReady = health.Ready
	}
	nodesLabel := fmt.Sprintf("%d / %d", nodesReady, nodesTotal)

	k8sVersion := "-"
	if health := app.clusterHealth; health != nil && health.ControlPlaneVersion != "" {
		k8sVersion = health.ControlPlaneVersion
	} else if recordOk && record.K8sVersion != "" {
		k8sVersion = record.K8sVersion
	}

	manageNodes := ucx.LinkButton("homeManageNodes", "(Manage)", ucx.ColorTextSecondary).Sx(
		ucx.SxFontSize(12),
	).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		app.selectResourceType("nodes")
	})

	children := []ucx.UiNode{}

	stackInfo := app.stackInfo
	if stackInfo != nil {
		createdAtLabel := "-"
		if stackInfo.CreatedAt > 0 {
			createdAtLabel = time.UnixMilli(stackInfo.CreatedAt).UTC().Format("Jan 2, 2006 15:04 UTC")
		}

		stackIdMetric := app.homeMetric("Cluster ID",
			homeMetricText(stackInfo.Id, ucx.ColorTextPrimary),
			ucx.Box().Sx(ucx.SxMl(-4)).Children(
				ucx.CopyButtonEx("homeCopyStackId", stackInfo.Id).WithTooltip("Copy cluster ID"),
			),
		)

		providerMetric := app.homeMetric("Provider", homeMetricText("-", ucx.ColorTextPrimary))
		if stackInfo.Provider != "" {
			providerMetric = app.homeMetric("Provider",
				ucx.ProviderTitleEx("homeProviderTitle", stackInfo.Provider, true).Sx(
					ucx.SxFontWeight("600"),
					ucx.SxFontSize(16),
				),
			)
		}

		children = append(children,
			app.homeMetricsRow(
				stackIdMetric,
				providerMetric,
				app.homeMetric("Created", homeMetricText(createdAtLabel, ucx.ColorTextPrimary)),
			),
		)
	}

	children = append(children,
		app.homeMetricsRow(
			app.homeMetric("Cluster state", homeMetricText(clusterState, clusterStateColor)),
			app.homeMetricEx("Nodes ready", []ucx.UiNode{manageNodes}, homeMetricText(nodesLabel, ucx.ColorTextPrimary)),
			app.homeMetric("Kubernetes version", homeMetricText(k8sVersion, ucx.ColorTextPrimary)),
		),
	)

	if stackInfo != nil {
		resourcesMetric := app.homeMetric("Resources",
			ucx.LinkButton("homeShowResources", fmt.Sprintf("%d", stackInfo.ResourceCount), ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackShowResources(app)
			}),
		)
		children = append(children,
			app.homeMetricsRow(
				resourcesMetric,
			),
		)
	}

	return children
}

func kubectlExample(title string, command string) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(
		ucx.TextEx("", title).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
		),
		ucx.Code(command),
	)
}

func (app *stackUiApp) openHeadlampDialog() {
	token, err := os.ReadFile(filepath.Join(managementMountDir, "kube-api-token"))
	if err != nil {
		log.Warn("k8s-app: failed to read headlamp token: %s", err)
		return
	}

	app.headlampToken = strings.TrimSpace(string(token))
	app.ShowHeadlampDialog = true
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) closeHeadlampDialog() {
	app.ShowHeadlampDialog = false
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) pageHomeActions() []ucx.UiNode {
	connectControls := []ucx.UiNode{
		ucx.ButtonEx("homeDownloadKubernetesConfig", "Kubeconfig", ucx.ColorPrimaryMain, ucx.IconHeroArrowDownTray, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.StackDownloadFile(app.Stack, shared.KubernetesConfigurationFileName)
		}),
	}
	if app.headlampUrl != "" {
		connectControls = append(connectControls, ucx.ButtonEx("homeOpenHeadlamp", "Open Headlamp", ucx.ColorPrimaryMain, ucx.IconHeroArrowTopRightOnSquare, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			app.openHeadlampDialog()
		}))
	}

	actions := []ucx.UiNode{
		ucx.SettingsAction("homeConnectAction",
			"Connect to this cluster",
			"Use the cluster from your terminal with kubectl, or from your browser with the Headlamp dashboard.",
		).Children(
			ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).
				Sx(ucx.SxAlignItemsCenter).
				Children(connectControls...),
		),
		ucx.AccordionNode("Use kubectl from your terminal", false).WithNoHeaderBorder().Sx(ucx.SxMt(8), ucx.SxMb(12)).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
				ucx.Markdown("kubectl is the standard Kubernetes command line tool. If you do not have it, see [how to install kubectl](https://kubernetes.io/docs/tasks/tools/)."),
				ucx.TextEx("", "1. Download the configuration file with the Kubeconfig button above.").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.TextEx("", "2. Move the file to where kubectl looks for it by default (adjust the source path to where your browser saved it):").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.Code("mkdir -p ~/.kube && mv ~/Downloads/kubeconfig ~/.kube/config"),
				ucx.TextEx("", "3. Check that it works by listing the nodes of the cluster:").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.Code("kubectl get nodes"),
			),
			ucx.Box().Sx(ucx.SxMt(16)).Children(
				ucx.TextEx("", "Some commands you will likely need:").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
					ucx.SxFontWeight("600"),
					ucx.SxMb(8),
				),
				ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(
					kubectlExample("List all pods across every namespace", "kubectl get pods -A"),
					kubectlExample("List all services across every namespace", "kubectl get services -A"),
					kubectlExample("Show the details of a pod, for example when it will not start", "kubectl describe pod <name>"),
					kubectlExample("Read the logs of a pod", "kubectl logs <pod>"),
					kubectlExample("Create or update resources from a YAML file", "kubectl apply -f <file>"),
				),
			),
			ucx.TextEx("", "If kubectl reports connection errors, verify that the file lives at ~/.kube/config.").Sx(
				ucx.SxColor(ucx.ColorTextSecondary),
			),
		),
	}

	if app.headlampUrl != "" {
		actions = append(actions, ucx.AccordionNode("Use Headlamp in your browser", false).WithNoHeaderBorder().Sx(ucx.SxMt(8), ucx.SxMb(12)).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(
				ucx.Markdown("Headlamp is a Kubernetes dashboard that runs in your browser. It shows workloads, nodes and other resources of the cluster. Nothing needs to be installed."),
				ucx.Markdown(
					"- Open it with the **Open Headlamp** button. It first lets you copy the login token.\n"+
						"- The token is the cluster's admin token and must be pasted into Headlamp's login screen."),
				ucx.ButtonEx("homeOpenHeadlampAccordion", "Open Headlamp", ucx.ColorPrimaryMain, ucx.IconHeroArrowTopRightOnSquare, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					app.openHeadlampDialog()
				}),
			),
		))
	}

	if app.Stack != nil && app.Stack.Ok {
		actions = append(actions, ucx.SettingsAction("homeDeleteAction",
			"Delete cluster",
			"Permanently deletes the cluster and all of its resources. This cannot be undone.",
		).Children(
			ucx.ButtonEx("homeDeleteCluster", "Delete cluster", ucx.ColorErrorMain, ucx.IconTrash, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackDelete(app)
			}),
		))
	}

	return actions
}

func (app *stackUiApp) pageResources() []ucx.UiNode {
	if app.k8sClient == nil {
		return []ucx.UiNode{app.appShell(appShellProps{
			Content:        []ucx.UiNode{shellContentBox(1100, app.pageProvisioning()...)},
			EscapeDisabled: true,
		})}
	}

	detail := app.ResourceDetail
	inDetail := detail != ""

	activeDef, _ := app.resolveType(app.ActiveType)
	namespaced := activeDef.Namespaced

	var main []ucx.UiNode
	var bottom []ucx.UiNode
	isHome := app.ActiveType == navHomeId
	if isHome && !inDetail {
		homeChildren := append([]ucx.UiNode{}, app.pageHomeContent()...)
		homeChildren = append(homeChildren, ucx.Box().Sx(ucx.SxMt(8)).Children(app.pageHomeActions()...))

		main = []ucx.UiNode{shellContentBox(1100, homeChildren...)}
	} else if inDetail {
		main = []ucx.UiNode{app.resourceDetailNode(detail)}
		bottom = append(bottom, app.resourceDetailBottomNode(detail))
	} else {
		var tableActions []ucx.ResourceTableAction
		var groupAction *ucx.ResourceTableAction
		var trailingAction *ucx.ResourceTableAction
		if app.ActiveType == "nodes" && app.clusterReadyForNodes() {
			tableActions = append(tableActions,
				ucx.ResourceTableAction{
					Id:       "copyNodeName",
					Label:    "Copy node name",
					Icon:     ucx.IconCopy,
					Kind:     ucx.ResourceTableActionCopyText,
					Shortcut: "c",
				},
				ucx.ResourceTableAction{
					Id:       "goToJob",
					Label:    "Go to job",
					Icon:     ucx.IconHeroArrowTopRightOnSquare,
					Shortcut: "g",
				},
			)
			groupAction = &ucx.ResourceTableAction{
				Id:       "addMachine",
				Label:    "Add machine",
				Icon:     ucx.IconHeroPlusSmall,
				Shortcut: "a",
			}
			tableActions = append(tableActions,
				ucx.ResourceTableAction{
					Id:    "cordonDrain",
					Label: "Cordon and drain",
					Icon:  ucx.IconBroom,
				},
				ucx.ResourceTableAction{
					Id:    "uncordon",
					Label: "Uncordon",
					Icon:  ucx.IconRefresh,
				},
				ucx.ResourceTableAction{
					Id:    "upgradeNode",
					Label: "Upgrade Kubernetes",
					Icon:  ucx.IconHeroArrowUp,
				},
				ucx.ResourceTableAction{
					Id:    "removeNode",
					Label: "Remove node",
					Icon:  ucx.IconTrash,
				},
			)
			trailingAction = &ucx.ResourceTableAction{
				Id:       "addWorkerPool",
				Label:    "Add worker pool",
				Icon:     ucx.IconHeroPlusSmall,
				Color:    ucx.ColorPrimaryMain,
				Shortcut: "n",
			}
		}

		main = []ucx.UiNode{ucx.ResourceTable(ucx.ResourceTableProps{
			Id:               "resourceTable",
			TableId:          app.resourceStreamId(),
			StateKey:         app.ActiveType,
			ViewId:           "k8sResources",
			EmptyMessage:     "No resources found.",
			HideGroupHeaders: namespaced,
			Actions:          tableActions,
			GroupAction:      groupAction,
			TrailingAction:   trailingAction,
		}).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			typeId, namespace, name := rowActivationValue(ev.Value)
			app.handleRowActivated(typeId, namespace, name)
		}).On(ucx.UiEventAction, func(ev ucx.UiEvent) {
			app.handleTableAction(ev)
		})}

		bottom = append(bottom, ucx.TableFilter("resourceFilter", app.ActiveType))
		if namespaced {
			bottom = append(bottom, app.namespaceSelectorNode())
		}
		bottom = append(bottom, ucx.Box().Sx(ucx.SxFlexGrow(1)))
		bottom = append(bottom, ucx.TableCount("resourceCount", app.ActiveType).WithTitle(app.activeTypeLabel(app.allTypeDefs())))
	}

	escapePath := ""
	if inDetail {
		escapeType := detailType(app.ResourceDetail)
		if escapeType == "provisioning" {
			escapeType = app.ActiveType
			if escapeType == navHomeId {
				escapeType = "nodes"
			}
		}
		escapePath = "browse/" + escapeType
	}

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:        main,
		Bottom:         bottom,
		EscapePath:     escapePath,
		EscapeDisabled: !inDetail,
	})}
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

	maintenanceLinks := app.pageProvisioningMaintenanceLinks()
	if len(maintenanceLinks) > 0 {
		messages = append(messages, ucx.Text("Node maintenance (including recovery of finished operations):"))
		messages = append(messages, maintenanceLinks...)
	}

	children := []ucx.UiNode{ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(messages...)}

	if record, ok := app.readClusterRecord(); ok {
		for _, node := range record.Nodes {
			if node.JobId == "" {
				continue
			}

			children = append(children,
				ucx.H3Ex("initLogsHeading-"+node.JobId, node.Hostname),
				ucx.JobLogs("initLogs-"+node.JobId, node.JobId),
			)
		}
	}

	return children
}

func (app *stackUiApp) pageProvisioningMaintenanceLinks() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return nil
	}

	known := maintenanceKnownNodeNames(record)

	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return nil
	}

	names := make([]string, 0, len(snapshot))
	for nodeName := range snapshot {
		if known[nodeName] {
			names = append(names, nodeName)
		}
	}
	sort.Strings(names)

	links := make([]ucx.UiNode, 0, len(names))
	for _, nodeName := range names {
		links = append(links, ucx.Link(fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName))).Children(ucx.Text(
			fmt.Sprintf("%s (%s)", nodeName, snapshot[nodeName].Phase),
		)))
	}
	return links
}

func maintenanceKnownNodeNames(record shared.ClusterRecord) map[string]bool {
	known := make(map[string]bool, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.Hostname != "" {
			known[node.Hostname] = true
		}
	}
	return known
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
	}

	targetRoute := "browse/" + typeId
	if typeId == navHomeId {
		targetRoute = ""
	}
	if app.RoutePath != targetRoute {
		ucxsvc.RouterPushPage(app, targetRoute)
	}

	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) namespaceSelectorNode() ucx.UiNode {
	options := make([]ucx.Option, 0, len(app.Namespaces)+1)
	options = append(options, ucx.Option{Key: "", Value: "All namespaces"})
	for _, namespace := range app.Namespaces {
		options = append(options, ucx.Option{Key: namespace, Value: namespace})
	}

	return ucx.Select("namespaceSelect", "", "activeNamespace", options).Sx(ucx.SxWidth(280)).WithShortcutKey("s")
}

func (app *stackUiApp) resourceDetailNode(detail string) ucx.UiNode {
	if strings.HasPrefix(detail, "provisioning/") {
		return ucx.JobLogsBound("detailInitLogs", "logJobId").
			Sx(ucx.SxP(16), ucx.SxBoxSizing("border-box"))
	}
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
	if typeId == "provisioning" {
		label = "Provisioning"
	} else if def, ok := app.resolveType(typeId); ok {
		label = def.Label
	}

	backTarget := typeId
	if typeId == "provisioning" {
		backTarget = app.ActiveType
		if backTarget == navHomeId {
			backTarget = "nodes"
		}
	}

	return ucx.Toolbar().Children(
		ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.RouterPushPage(app, "browse/"+backTarget)
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

	if jobId := app.provisioningJobId(name); jobId != "" {
		app.ResourceDetail = "provisioning/" + namespace + "/" + name
		app.prevDetail = app.ResourceDetail
		app.LogJobId = jobId
		app.ResourceYaml = ""
		ucxsvc.RouterPushPage(app, "detail/"+url.PathEscape("provisioning")+"/"+url.PathEscape(namespace)+"/"+url.PathEscape(name))
		ucx.AppUpdateUi(app)
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
	if rowKey == "" {
		return
	}

	if actionId != "goToJob" && actionId != "cordonDrain" && actionId != "uncordon" && actionId != "upgradeNode" && actionId != "removeNode" {
		return
	}

	if app.ActiveType != "nodes" || app.poller == nil {
		return
	}

	nodeName, ok := app.poller.nodeNameForRowKey(rowKey)
	if !ok || nodeName == "" {
		return
	}

	switch actionId {
	case "cordonDrain", "upgradeNode", "removeNode":
		if strings.HasPrefix(rowKey, provisioningRowKeyPrefix) {
			if actionId == "removeNode" {
				maintenanceOpenWithMode(app, nodeName, "", maintenanceModeRemove)
			}
			return
		}
		nodeUid, known := app.nodeUidForName(nodeName)
		if !known {
			return
		}
		if actionId == "upgradeNode" {
			maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeUpgrade)
		} else if actionId == "removeNode" {
			maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeRemove)
		} else {
			maintenanceOpen(app, nodeName, nodeUid)
		}
	case "uncordon":
		if strings.HasPrefix(rowKey, provisioningRowKeyPrefix) {
			return
		}
		nodeUid, known := app.nodeUidForName(nodeName)
		if !known {
			return
		}
		if app.MaintenanceBusy {
			return
		}
		maintenanceSubmitAsync(app, "uncordon", nodeName, nodeUid, maintenance.Options{})
	case "goToJob":
		jobId := app.nodeJobIds()[nodeName]
		if jobId == "" {
			return
		}
		ucxsvc.OpenUrl(app, fmt.Sprintf("/jobs/properties/%s", jobId))
	}
}

func (app *stackUiApp) nodeUidForName(nodeName string) (string, bool) {
	if app.poller == nil {
		return "", false
	}
	return app.poller.nodeUidForRowName(nodeName)
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

func (app *stackUiApp) provisioningKickNow() {
	if app.provisioningKick == nil {
		return
	}
	select {
	case app.provisioningKick <- util.Empty{}:
	default:
	}
}

func (app *stackUiApp) onMaintenanceRoute() bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	return app.RoutePath == "maintenance" || strings.HasPrefix(app.RoutePath, "maintenance/")
}

func (app *stackUiApp) provisioningWatcher(session *ucx.Session) {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		app.refreshProvisioningRows(session)
		if app.activeTypeIsHome() {
			go app.refreshStackInfo()
		}
		if app.activeTypeIsHome() || app.onMaintenanceRoute() {
			go app.refreshClusterHealth()
		}
		if app.onMaintenanceRoute() {
			app.mu.Lock()
			ucx.AppUpdateUi(app)
			app.mu.Unlock()
		}

		select {
		case <-session.Context().Done():
			return
		case <-app.provisioningKick:
		case <-ticker.C:
		}
	}
}

func (app *stackUiApp) refreshClusterHealth() {
	client := app.k8sClient
	session := app.session
	if client == nil || session == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	health, err := client.NodeHealth(ctx)
	if err != nil {
		return
	}

	app.mu.Lock()
	changed := app.clusterHealth == nil || *app.clusterHealth != health
	if changed {
		app.clusterHealth = &health
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()
}

func (app *stackUiApp) refreshProvisioningRows(session *ucx.Session) {
	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return
	}

	app.mu.Lock()
	known := make(map[string]string, len(app.provisioningCache))
	for _, entry := range app.provisioningCache {
		known[entry.Hostname] = entry.Status
	}
	app.mu.Unlock()

	entries := make([]provisioningEntry, 0, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.JobId == "" || node.Hostname == "" {
			continue
		}

		status := known[node.Hostname]
		if status == "" {
			status = "Provisioning"
		}
		entries = append(entries, provisioningEntry{
			Hostname: node.Hostname,
			Group:    node.Group,
			Status:   status,
			JobId:    node.JobId,
		})
	}

	app.mu.Lock()
	changed := !provisioningEntriesEqual(app.provisioningCache, entries)
	app.provisioningCache = entries
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && app.poller != nil {
		app.poller.signalPollNow()
	}

	client := app.k8sClient
	if client == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	existing, err := client.NodeNames(ctx)
	if err != nil {
		return
	}

	changed = false
	for i := range entries {
		if existing[entries[i].Hostname] {
			entries[i].Status = "ready"
			continue
		}
		if job, err := ucxsvc.JobRetrieve(app.Stack, entries[i].JobId); err == nil {
			switch {
			case job.Status.State == orcapi.JobStateInQueue:
				entries[i].Status = "Waiting for resources"
			case job.Status.State == orcapi.JobStateRunning:
				entries[i].Status = "Initializing"
			case job.Status.State == orcapi.JobStateSuspended:
				entries[i].Status = "Powered off"
			case job.Status.State.IsFinal():
				entries[i].Status = "Failed"
			}
		}
	}

	survivors := make([]provisioningEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.Status == "ready" {
			continue
		}
		survivors = append(survivors, entry)
	}

	app.mu.Lock()
	changed = !provisioningEntriesEqual(app.provisioningCache, survivors)
	app.provisioningCache = survivors
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && app.poller != nil {
		app.poller.signalPollNow()
	}
}

func provisioningEntriesEqual(a, b []provisioningEntry) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func (app *stackUiApp) provisioningResourceRows(def ResourceTypeDef) []ResourceRow {
	if def.Id != "nodes" {
		return nil
	}

	app.mu.Lock()
	entries := app.provisioningCache
	app.mu.Unlock()

	rows := make([]ResourceRow, 0, len(entries))
	for _, entry := range entries {
		role := ""
		if entry.Group == shared.GroupControlPlane {
			role = "control-plane"
		}
		rows = append(rows, ResourceRow{
			Key:   "provisioning:" + entry.Hostname,
			Group: entry.Group,
			Cells: []string{entry.Hostname, entry.Status, "Schedulable", "", role, "", "", ""},
		})
	}
	return rows
}

func (app *stackUiApp) provisioningJobId(hostname string) string {
	for _, entry := range app.provisioningCache {
		if entry.Hostname == hostname {
			return entry.JobId
		}
	}
	return ""
}

func (app *stackUiApp) readClusterRecord() (shared.ClusterRecord, bool) {
	session := app.session
	stack := app.Stack
	if session != nil && stack != nil && stack.Ok {
		client, clientErr := shared.ClusterStateClientNewSession(session, stack.InstanceId)
		if clientErr == nil {
			snapshot, readErr := shared.ClusterStateRead(client)
			if readErr == nil && snapshot.Found {
				record := snapshot.Record
				shared.ClusterStateRecordRevisionSet(&record, snapshot.Revision)
				return record, true
			}
		}
	}

	record, found, err := shared.ClusterStateRecordRead()
	if err != nil || !found {
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

func (app *stackUiApp) clusterReadyForNodes() bool {
	record, ok := app.readClusterRecord()
	return ok && record.Phase == "created"
}

// "Add new VM" page for an existing pool
func (app *stackUiApp) pageAddMachine() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if recordOk && record.MachineProvider != "" {
		app.ClusterProvider = record.MachineProvider
	}

	group := strings.TrimSpace(app.TargetGroup)

	shellEscape := appShellProps{EscapePath: "browse/nodes"}

	if group == "" {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("No node pool was selected."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	shellEscape.Bottom = []ucx.UiNode{shellBottomNode(app, "nodes", group, "Add machine")}

	if !recordOk {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Could not read the cluster record."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if record.Phase != "created" {
		content := []ucx.UiNode{ucx.Text(
			"New nodes cannot be added while the cluster is in state " + record.Phase +
				". The cluster must be in the created state.",
		)}
		if record.FailureReason != "" {
			content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		}
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, content...)}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	var pool *shared.ClusterPoolRecord
	for i := range record.Pools {
		if record.Pools[i].Name == group {
			pool = &record.Pools[i]
			break
		}
	}

	if pool == nil {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Unknown node pool: "+group))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if app.TargetDiskGb <= 0 {
		app.TargetDiskGb = pool.DiskGb
	}
	if app.TargetCount <= 0 {
		app.TargetCount = 1
	}

	children := []ucx.UiNode{
		ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
			if app.AddBusy {
				return
			}

			groupValue := group
			machine := pool.Machine
			diskGb := app.TargetDiskGb
			count := app.TargetCount

			app.AddBusy = true
			ucx.AppUpdateModelPatch(*app.Session(), map[string]ucx.Value{
				"addBusy": ucx.VBool(true),
			})

			go func() {
				app.mu.Lock()
				defer app.mu.Unlock()

				app.addMachinesToGroup(groupValue, machine, diskGb, count)
				app.AddBusy = false
				app.provisioningKickNow()
				ucx.AppUpdateUi(app)
			}()
		}).Children(
			ucx.FieldGroupNode().Children(
				ucx.FieldRowNodeEx("poolNameRow", "Pool name", "").
					Children(ucx.Text(group)),
				ucx.FieldRowNodeEx("machineRow", "Machine type", "").
					FieldRowDescription("All machines in a pool use the same machine type.").
					Children(ucx.Text(pool.Machine.Id)),
				ucx.FieldRowNodeEx("diskRow", "Disk size (GB)", "targetDiskGb").
					FieldRowDescription(fmt.Sprintf("The disk size for each new node. This pool uses %d GB by default.", pool.DiskGb)).
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetDisk", "", "targetDiskGb", 10, 1024).WithAutoFocus(),
					),
				ucx.FieldRowNodeEx("countRow", "Node count", "targetCount").
					FieldRowDescription("The number of nodes to add to this pool.").
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetCount", "", "targetCount", 1, 250),
					),
			),
			ucx.SubmitButton("addToPool", "Add machine to pool", ucx.ColorSuccessMain).
				ButtonBusy("addBusy").
				ButtonSubmitShortcut(true).
				Sx(ucx.SxJustifyEnd),
		),
	}

	shellEscape.Content = []ucx.UiNode{shellContentBox(800,
		ucx.KeyboardNavigationNode("keyboardNavigation").
			HorizontalSelector("[data-job-info-field]").
			SubmitForm("addNodeForm").
			SubmitDisabled(app.AddBusy).
			Children(children...),
	)}

	return []ucx.UiNode{app.appShell(shellEscape)}
}

// "Add worker pool" page, modeled after the creator's pool card
func (app *stackUiApp) pageAddPool() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if recordOk && record.MachineProvider != "" {
		app.ClusterProvider = record.MachineProvider
	}

	shellEscape := appShellProps{EscapePath: "browse/nodes"}

	shellEscape.Bottom = []ucx.UiNode{shellBottomNode(app, "nodes", "Add worker pool")}

	if !recordOk {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Could not read the cluster record."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if record.Phase != "created" {
		content := []ucx.UiNode{ucx.Text(
			"New pools cannot be added while the cluster is in state " + record.Phase +
				". The cluster must be in the created state.",
		)}
		if record.FailureReason != "" {
			content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		}
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, content...)}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if app.TargetDiskGb <= 0 {
		app.TargetDiskGb = 50
	}
	if app.TargetCount <= 0 {
		app.TargetCount = 1
	}

	form := ucx.Form("addPoolForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.AddBusy {
			return
		}

		name := app.NewPoolName
		machine := app.Machine
		diskGb := app.TargetDiskGb
		count := app.TargetCount

		app.AddBusy = true
		ucx.AppUpdateModelPatch(*app.Session(), map[string]ucx.Value{
			"addBusy": ucx.VBool(true),
		})

		go func() {
			app.mu.Lock()
			defer app.mu.Unlock()

			app.addPool(name, machine, diskGb, count)
			app.AddBusy = false
			app.provisioningKickNow()
			ucx.AppUpdateUi(app)
		}()
	}).Children(
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("poolNameRow", "Pool name", "newPoolName").
				FieldRowDescription("The Kubernetes node group name for this pool. Must contain only a-z, 0-9 and dashes.").
				FieldRowRequired(true).
				Children(
					ucx.InputText("poolName", "", "workers", "newPoolName").WithAutoFocus(),
				),
			ucx.FieldRowNodeEx("machineRow", "Machine type", "").
				FieldRowDescription("The machine type used for the nodes in this pool.").
				Children(
					ucx.MachineTypeSelector("poolMachine", "", "machine", ucx.MachineCapabilityVm).
						MachineSelectorProviderBindPath("clusterProvider").MachineSelectorProviderOnly(true),
				),
			ucx.FieldRowNodeEx("diskRow", "Disk size (GB)", "targetDiskGb").
				FieldRowDescription("The disk size for each node in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolDisk", "", "targetDiskGb", 10, 1024),
				),
			ucx.FieldRowNodeEx("countRow", "Node count", "targetCount").
				FieldRowDescription("The number of worker nodes in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolCount", "", "targetCount", 1, 250),
				),
		),
		ucx.SubmitButton("addPool", "Add worker pool", ucx.ColorSuccessMain).
			ButtonBusy("addBusy").
			ButtonSubmitShortcut(true).
			Sx(ucx.SxJustifyEnd),
	)

	shellEscape.Content = []ucx.UiNode{shellContentBox(800,
		ucx.KeyboardNavigationNode("keyboardNavigation").
			HorizontalSelector("[data-job-info-field]").
			SubmitForm("addPoolForm").
			SubmitDisabled(app.AddBusy).
			Children(form),
	)}

	return []ucx.UiNode{app.appShell(shellEscape)}
}

func (app *stackUiApp) addMachinesToGroup(group string, machine accapi.ProductReference, diskGb int, count int) {
	if count < 1 {
		count = 1
	}

	_, ok := shared.ClusterAddNodesBatch(app, app.Stack, group, machine, diskGb, count)
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created %d new %s VM(s).", count, group))
	ucxsvc.RouterPushPage(app, "browse/nodes")
}

func (app *stackUiApp) addPool(name string, machine accapi.ProductReference, diskGb int, count int) {
	trimmedName := strings.TrimSpace(name)
	if trimmedName == "" {
		ucxsvc.UiSendFailure(app, "Please provide a pool name")
		return
	}

	if app.ClusterProvider != "" && machine.Provider != app.ClusterProvider {
		ucxsvc.UiSendFailure(app, "The machine must come from the provider that runs the cluster: "+app.ClusterProvider)
		return
	}

	ok := shared.ClusterAddPool(app, app.Stack, shared.ClusterPoolSpec{
		Name:    trimmedName,
		Machine: machine,
		Nodes:   count,
		DiskGb:  diskGb,
	})
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created pool %s with %d node(s)!", trimmedName, count))
	ucxsvc.RouterPushPage(app, "browse/nodes")
}

func (app *stackUiApp) handleTableAction(ev ucx.UiEvent) {
	if ev.Event != string(ucx.UiEventAction) || ev.Value.Kind != ucx.ValueObject {
		return
	}

	actionId := ucx.ValueAsString(ev.Value.Object["actionId"])
	rowKey := ucx.ValueAsString(ev.Value.Object["rowKey"])
	group := ucx.ValueAsString(ev.Value.Object["group"])

	if rowKey != "" {
		app.handleRowAction(ev)
		return
	}

	switch actionId {
	case "cordonDrain", "uncordon", "upgradeNode", "removeNode":
		ucxsvc.UiSendFailure(app, "Select a single node before using this action")
	case "addMachine":
		if group == "" {
			return
		}
		app.TargetDiskGb = 0
		app.TargetCount = 0
		ucxsvc.RouterPushPage(app, "control/"+group)
		ucx.AppUpdateUi(app)
	case "addWorkerPool":
		app.NewPoolName = ""
		app.Machine = accapi.ProductReference{}
		app.TargetDiskGb = 0
		app.TargetCount = 0
		ucxsvc.RouterPushPage(app, "control/new-pool")
		ucx.AppUpdateUi(app)
	}
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

		routeType := app.ActiveType
		if app.RoutePath == "" {
			routeType = navHomeId
		} else if strings.HasPrefix(app.RoutePath, "browse/") {
			routeType = strings.TrimPrefix(app.RoutePath, "browse/")
			if _, ok := app.resolveType(routeType); !ok && routeType != navHomeId {
				routeType = app.ActiveType
			}
		}

		changed := routeDetail != app.ResourceDetail

		if routeType != app.ActiveType {
			app.ActiveType = routeType
			if app.poller != nil {
				app.poller.SetActiveType(routeType)
			}
			if def, ok := app.resolveType(routeType); ok && def.Namespaced {
				app.loadNamespaces()
			}
			changed = true
		}
		app.ResourceDetail = routeDetail
		if app.ResourceDetail != app.prevDetail {
			app.prevDetail = app.ResourceDetail
			if jobId := app.provisioningJobId(detailHostname(app.ResourceDetail)); jobId != "" {
				app.LogJobId = jobId
			}
			app.loadResourceYaml(app.ResourceDetail)
			changed = true
		}

		switch frame.ModelInput.Path {
		case "activeNamespace", "routePath", "maintenanceDrain":
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

func detailHostname(detail string) string {
	parts := strings.Split(detail, "/")
	if len(parts) != 3 {
		return ""
	}
	hostname, err := url.PathUnescape(parts[2])
	if err != nil {
		return ""
	}
	return hostname
}

func detailType(detail string) string {
	parts := strings.Split(detail, "/")
	if len(parts) != 3 {
		return ""
	}
	typeId, err := url.PathUnescape(parts[0])
	if err != nil {
		return ""
	}
	return typeId
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

	if typeId == "provisioning" {
		return
	}

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
