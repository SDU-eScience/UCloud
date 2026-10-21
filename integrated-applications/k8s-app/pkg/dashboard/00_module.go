package dashboard

import (
	"context"
	"encoding/json"
	"net/url"
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

	"ucloud.dk/iapp/k8s/pkg/shared"
)

const managementMountDir = "/etc/ucloud-k8s/management"

const navHomeId = "home"

const provisioningRowKeyPrefix = "provisioning:"

const dashboardUiMessageSendTimeout = 10 * time.Second

func dashboardSendUiMessage(session *ucx.Session, message string, success bool) {
	if session == nil || session.Context().Err() != nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), dashboardUiMessageSendTimeout)
	defer cancel()

	_, _ = ucxapi.UiSendMessage.InvokeEx(ctx, session, ucxapi.UiSendMessageRequest{
		Message: message,
		Success: success,
	})
}

func (app *stackUiApp) runAsyncAction(busyBinding string, busy *bool, action func() error, onDone func(error)) {
	session := *app.Session()
	if session == nil {
		return
	}

	if busy != nil {
		*busy = true
		ucx.AppUpdateModelPatch(session, map[string]ucx.Value{
			busyBinding: ucx.VBool(true),
		})
	}

	go func() {
		err := action()

		if busy != nil {
			app.mu.Lock()
			*busy = false
			updatedUi := app.UserInterface()
			updatedModel := ucx.AppSnapshot(app)
			app.mu.Unlock()

			if session.Context().Err() != nil {
				return
			}
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
		}

		if onDone != nil && session.Context().Err() == nil {
			onDone(err)
		}
	}()
}

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

	ActiveType           string
	ActiveNamespace      string
	ActiveFilter         resourceFilter             `ucx:"-"`
	filterOrigin         string                     `ucx:"-"`
	resourceNavigation   []dashboardNavigationEntry `ucx:"-"`
	ResourceDetail       string
	ResourceYaml         string
	ResourceDraft        string
	resourceEditor       dashboardResourceEditorState `ucx:"-"`
	resourceCreateDrafts map[string]string            `ucx:"-"`
	Namespaces           []string
	LogJobId             string
	ContainerLogs        string

	MaintenanceTimeoutSeconds          int
	MaintenanceDrain                   bool
	MaintenanceCordon                  bool
	MaintenanceDeleteVolatilePods      bool
	MaintenanceBypassDisruptionBudgets bool
	MaintenanceForceDelete             bool
	MaintenanceBusy                    bool
	MaintenanceTargetRelease           string
	RollingUpgradeTargetRelease        string
	RollingUpgradeGroups               []rollingUpgradeGroup
	RollingUpgradeBusy                 bool

	BackupsBusy      bool
	BackupsBlocked   bool
	backupsRequester string `ucx:"-"`

	AutoMaintenanceK8sEnabled bool
	AutoMaintenanceOsEnabled  bool
	AutoWindowDay             string
	AutoWindowStartHour       string
	AutoMaintenanceBusy       bool
	autoMaintenanceLoaded     bool              `ucx:"-"`
	autoSettingsSaving        bool              `ucx:"-"`
	autoSettingsPending       *autoSettingsForm `ucx:"-"`
	autoSettingsSaveError     string            `ucx:"-"`
	autoRunNowConfirm         bool              `ucx:"-"`

	maintenanceNodeName        string `ucx:"-"`
	maintenanceNodeUid         string `ucx:"-"`
	maintenanceRetryOptionsFor string `ucx:"-"`
	maintenanceMode            string `ucx:"-"`

	stackInfo *ucxapi.StackInfoResponse `ucx:"-"`

	ShowRolloutRestartDialog bool                          `ucx:"-"`
	rolloutRestartTarget     rolloutRestartTarget          `ucx:"-"`
	ShowResourceDeleteDialog bool                          `ucx:"-"`
	resourceDeleteTarget     dashboardResourceDeleteTarget `ucx:"-"`

	clusterHealth *NodeHealth `ucx:"-"`

	provisioningCache []provisioningEntry `ucx:"-"`
	provisioningKick  chan util.Empty     `ucx:"-"`

	prevDetail string `ucx:"-"`

	containerLogTarget     string             `ucx:"-"`
	containerLogCancel     context.CancelFunc `ucx:"-"`
	containerLogFlushTimer *time.Timer        `ucx:"-"`
	containerLogGeneration uint64             `ucx:"-"`
	containerLogPending    string             `ucx:"-"`
	containerLogTruncated  bool               `ucx:"-"`
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
	if request.Job.Owner.CreatedBy != "" {
		app.backupsRequester = request.Job.Owner.CreatedBy
	}

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

	app.mu.Lock()
	changed := app.stackInfo == nil || *app.stackInfo != info
	if changed {
		app.stackInfo = &info
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()
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
							app.OnMessage(ucx.Frame{
								Opcode: ucx.OpModelInput,
								ModelInput: ucx.ModelInput{
									Path: "routePath",
								},
							})
							app.mu.Unlock()
						})
						app.syncContainerLogStream()
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
	if app.RoutePath != "automatic-maintenance" {
		app.autoMaintenanceLoaded = false
	}

	children := []ucx.UiNode{
		ucx.Router("routePath"),
	}

	switch {
	case app.RoutePath == "rolling-upgrade":
		children = append(children, rollingUpgradePage(app)...)
	case app.RoutePath == "automatic-maintenance":
		children = append(children, autoMaintenancePage(app)...)
	case app.RoutePath == "backups":
		children = append(children, backupPage(app)...)
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

	if app.ShowRolloutRestartDialog {
		children = append(children, app.rolloutRestartDialogNode())
	}
	if app.ShowResourceDeleteDialog {
		children = append(children, dashboardResourceDeleteDialog(app))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).
		Sx(
			ucx.SxP(4),
			ucx.SxHeightRaw("100%"),
			ucx.SxBoxSizing("border-box"),
		).
		Children(children...)
}

func (app *stackUiApp) allTypeDefs() []ResourceTypeDef {
	if app.poller != nil {
		return app.poller.resourceTypesSnapshot()
	}
	return ResourceTypes()
}

func (app *stackUiApp) browseRoute(typeId string, filter resourceFilter) string {
	route := "browse/" + typeId
	if filter.key() != "" {
		route = route + "?" + url.QueryEscape(filter.key())
	}
	return route
}

func filterFromRoute(routePath string) resourceFilter {
	if !strings.HasPrefix(routePath, "browse/") {
		return resourceFilter{}
	}
	rest := strings.TrimPrefix(routePath, "browse/")
	_, filter, found := strings.Cut(rest, "?")
	if !found {
		return resourceFilter{}
	}
	decoded, err := url.QueryUnescape(filter)
	if err != nil {
		return resourceFilter{}
	}
	return parseResourceFilterKey(decoded)
}

func (app *stackUiApp) resourceNavItems() []ucx.NavItem {
	typeDefs := app.allTypeDefs()

	navItems := make([]ucx.NavItem, 0, 8)
	groups := map[string][]ucx.NavItemChild{}
	var customTypes []ResourceTypeDef
	for _, def := range typeDefs {
		if strings.HasPrefix(def.Id, "crd:") {
			customTypes = append(customTypes, def)
			continue
		}
		path := dashboardBuiltinNavPath(def)
		group := path[0]
		children := groups[group]
		dashboardBuiltinNavAdd(&children, path[1:], "group:"+group, ucx.NavItemChild{
			Id:      def.Id,
			Label:   def.Label,
			Aliases: def.Aliases,
		})
		groups[group] = children
	}
	for group, children := range groups {
		dashboardNavSort(children)
		navItems = append(navItems, ucx.NavItem{
			Id:       "group:" + group,
			Label:    group,
			Children: children,
		})
	}
	navItems = append(navItems, dashboardCustomNavItems(customTypes)...)
	sort.Slice(navItems, func(i, j int) bool {
		leftCluster := navItems[i].Id == "group:Cluster"
		rightCluster := navItems[j].Id == "group:Cluster"
		if leftCluster != rightCluster {
			return leftCluster
		}
		leftDirectory := len(navItems[i].Children) > 0
		rightDirectory := len(navItems[j].Children) > 0
		if leftDirectory != rightDirectory {
			return leftDirectory
		}
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

func (app *stackUiApp) resourceStreamId() string {
	def, _ := app.resolveType(app.ActiveType)
	return resourceDatasetId(resourceSelection{
		typeId:    app.ActiveType,
		namespace: app.ActiveNamespace,
		filter:    app.pollerFilter(),
		def:       def,
	})
}

func (app *stackUiApp) pollerFilter() resourceFilter {
	return app.ActiveFilter
}

func (app *stackUiApp) activeTypeLabel(typeDefs []ResourceTypeDef) string {
	if def, ok := app.resolveType(app.ActiveType); ok && def.Id == app.ActiveType {
		return def.Label
	}
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
	app.resourceNavigation = nil
	app.ActiveFilter = resourceFilter{}
	app.filterOrigin = ""
	if app.poller != nil {
		app.poller.SetActiveFilter(resourceFilter{})
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

	if app.ResourceDetail != "" {
		app.ResourceDetail = ""
		app.prevDetail = ""
		app.ResourceYaml = ""
	}

	targetRoute := ""
	if typeId != navHomeId {
		targetRoute = dashboardNavigationSelectType(app)
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

func (app *stackUiApp) onMaintenanceRoute() bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	return app.RoutePath == "rolling-upgrade" || app.RoutePath == "backups" || app.RoutePath == "automatic-maintenance" || app.RoutePath == "maintenance" || strings.HasPrefix(app.RoutePath, "maintenance/")
}

func (app *stackUiApp) maintenanceUiTick() {
	if !app.onMaintenanceRoute() {
		return
	}
	app.mu.Lock()
	ucx.AppUpdateUi(app)
	app.mu.Unlock()
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
	changed := app.clusterHealth == nil || !nodeHealthEqual(*app.clusterHealth, health)
	if changed {
		app.clusterHealth = &health
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()
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

func (app *stackUiApp) OnMessage(frame ucx.Frame) {
	switch frame.Opcode {
	case ucx.OpModelInput:
		switch frame.ModelInput.Path {
		case "autoMaintenanceK8sEnabled", "autoMaintenanceOsEnabled", "autoWindowDay", "autoWindowStartHour":
			if strings.HasPrefix(frame.ModelInput.NodeId, "rehydrate:") {
				app.autoMaintenanceLoaded = false
			} else {
				autoSettingsSaveAsync(app)
			}
			ucx.AppUpdateUi(app)
			return
		}
		if frame.ModelInput.Path == "resourceDraft" {
			dashboardResourceCreateDraftKeep(app)
			return
		}
		app.RoutePath = strings.TrimSpace(app.RoutePath)
		if frame.ModelInput.Path == "routePath" {
			dashboardNavigationRestore(app)
		} else if frame.ModelInput.Path == "activeNamespace" {
			dashboardNavigationUpdateNamespace(app)
		}
		routePath := dashboardNavigationCurrentRoute(app)

		if app.poller != nil {
			app.poller.SetActiveType(app.ActiveType)
			app.poller.SetActiveNamespace(app.ActiveNamespace)
		}

		routeDetail := ""
		if strings.HasPrefix(routePath, "detail/") || strings.HasPrefix(routePath, "create/") {
			routeDetail = detailFromRoute(routePath)
		}

		routeType := app.ActiveType
		if routePath == "" {
			routeType = navHomeId
		} else if strings.HasPrefix(routePath, "browse/") {
			routeType = strings.TrimPrefix(routePath, "browse/")
			if idx := strings.Index(routeType, "?"); idx >= 0 {
				routeType = routeType[:idx]
			}
			if _, ok := app.resolveType(routeType); !ok && routeType != navHomeId {
				routeType = app.ActiveType
			}
		}

		routeFilter := resourceFilter{}
		if strings.HasPrefix(routePath, "browse/") {
			routeFilter = filterFromRoute(routePath)
		} else if strings.HasPrefix(routePath, "detail/") || strings.HasPrefix(routePath, "create/") {
			routeFilter = app.ActiveFilter
		}

		changed := routeDetail != app.ResourceDetail

		if routeType != app.ActiveType {
			app.ActiveType = routeType
			app.ActiveFilter = routeFilter
			if app.poller != nil {
				app.poller.SetActiveFilter(routeFilter)
				app.poller.SetActiveType(routeType)
			}
			if def, ok := app.resolveType(routeType); ok && def.Namespaced {
				app.loadNamespaces()
			}
			changed = true
		} else if routeFilter != app.ActiveFilter {
			app.ActiveFilter = routeFilter
			if routeFilter.isEmpty() {
				app.filterOrigin = ""
			}
			if app.poller != nil {
				app.poller.SetActiveFilter(routeFilter)
			}
			changed = true
		}
		app.ResourceDetail = routeDetail
		if app.ResourceDetail != app.prevDetail {
			app.prevDetail = app.ResourceDetail
			ref, _ := parseResourceDetail(app.ResourceDetail)
			if jobId := app.provisioningJobId(ref.Name); jobId != "" {
				app.LogJobId = jobId
			}
			if ref.Type != containersTypeId {
				dashboardResourceEditorLoad(app, app.ResourceDetail, false)
			}
			app.syncContainerLogStream()
			changed = true
		} else if routeDetail == "" && app.containerLogTarget != "" {
			app.stopContainerLogsLocked()
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

type resourceDetailRef struct {
	Type         string
	Namespace    string
	Name         string
	Container    string
	hasContainer bool
}

func parseResourceDetail(detail string) (resourceDetailRef, bool) {
	parts := strings.Split(detail, "/")
	if len(parts) != 3 && len(parts) != 4 {
		return resourceDetailRef{}, false
	}

	result := resourceDetailRef{Type: parts[0], Namespace: parts[1], Name: parts[2], hasContainer: len(parts) == 4}
	if len(parts) == 4 {
		result.Container = parts[3]
	}
	return result, true
}

func (ref resourceDetailRef) String() string {
	if ref.hasContainer || ref.Container != "" {
		return ref.Type + "/" + ref.Namespace + "/" + ref.Name + "/" + ref.Container
	}
	return ref.Type + "/" + ref.Namespace + "/" + ref.Name
}

func (ref resourceDetailRef) creating() bool {
	return ref.Name == "@create"
}

func detailFromRoute(routePath string) string {
	if strings.HasPrefix(routePath, "create/") {
		return detailFromRoute("detail/" + strings.TrimPrefix(routePath, "create/") + "/@create")
	}
	parts := strings.Split(strings.TrimPrefix(routePath, "detail/"), "/")
	if len(parts) != 3 && len(parts) != 4 {
		return ""
	}

	decoded := make([]string, 0, len(parts))
	for _, part := range parts {
		value, err := url.PathUnescape(part)
		if err != nil {
			return ""
		}
		decoded = append(decoded, value)
	}

	return strings.Join(decoded, "/")
}

func (app *stackUiApp) resolveType(typeId string) (ResourceTypeDef, bool) {
	if app.poller != nil {
		return app.poller.resourceTypeById(typeId)
	}
	return ResourceType(typeId)
}
