// Introduction
// =====================================================================================================================
// This package implements the dashboard of the UCloud Kubernetes application. The dashboard runs inside the UCloud
// user interface. It shows the cluster and lets the user change it.
//
// The dashboard talks to two systems. The first is the UCloud stack. The stack owns the cluster record, the VMs and
// the jobs. The second is the Kubernetes API of the cluster. The dashboard reads the kubeconfig from the management
// mount and talks to the API server directly.
//
// The pages
// ---------------------------------------------------------------------------------------------------------------------
// - page_home.go: 				is the first page. It shows the cluster state, the versions and the shortcuts to the
//								other pages.
// - page_browse.go: 			is the resource browser. It lists every resource type in a table with actions on each row.
// - page_provisioning.go: 		is shown while the control plane starts. It reports the nodes and the job logs.
// - page_scaleout.go: 			adds machines to a node pool and creates new worker pools.
// - page_maintenance.go: 		maintains a single node. It cordons, drains, upgrades and removes the node.
// - page_rolling_upgrade.go: 	upgrades the whole cluster one node at a time.
// - page_auto_maintenance.go: 	configures the weekly window, the automatic upgrades and the automatic operating-system
//                            	updates.
// - page_backup.go: 			shows the control plane backups and starts a manual backup.
//
// The resource model
// ---------------------------------------------------------------------------------------------------------------------
// resources.go defines the resource types and the Kubernetes client. It also turns Kubernetes objects into table
// rows. poller.go keeps a table current with a watch. It sends only the rows that changed to the user interface.
//
// navigation.go keeps the navigation stack behind the breadcrumbs and the back links. navigation_tree.go builds the
// sidebar from the resource types. shell.go is the frame around every page. It holds the sidebar, the content box and
// the bottom toolbar.
//
// Editing resources
// ---------------------------------------------------------------------------------------------------------------------
// resource_create.go opens the create flow. It fetches the OpenAPI schema from the cluster and builds a template from
// it. resource_editor.go loads, edits and saves the YAML of one resource. resource_actions.go deletes a resource or
// restarts a workload. container_logs.go streams the logs of one container.
//
// Cluster state
// ---------------------------------------------------------------------------------------------------------------------
// provisioning.go watches the nodes while they start. It turns jobs into rows for the node table. Every read of the
// cluster record goes through readClusterRecord. The revision of the record protects every write, so two dashboard
// sessions cannot overwrite each other.
//
// The application lifecycle
// ---------------------------------------------------------------------------------------------------------------------
// App creates the application. OnSysHello connects it to the stack. The Kubernetes client starts in the background
// because the API server needs time to come up. The poller and the provisioning watcher start when the client is
// ready. UserInterface picks the page for the current route. OnMessage applies a change from the user interface.

package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
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

// Application model
// =====================================================================================================================
// The application keeps one struct. The struct is the model of the user interface and the state of the current page.
// The mutex guards the struct because the watchers and the message handlers run on different goroutines.

const (
	navHomeId                     = "home"
	provisioningRowKeyPrefix      = "provisioning:"
	dashboardUiMessageSendTimeout = 10 * time.Second
	dashboardClusterStateTimeout  = 5 * time.Second
)

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

	clusterHealth NodeHealth `ucx:"-"`

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

func (app *stackUiApp) Mutex() *sync.Mutex { return &app.mu }

func (app *stackUiApp) Session() **ucx.Session { return &app.session }

func (app *stackUiApp) OnInit() {
	shared.RegisterRpcHandlers(app, shared.ModeDashboard)
	app.registerRpcHandlers()
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

	if ucx.AppHeadless(app) {
		return
	}

	if !app.clientWaiterStarted && app.session != nil {
		app.clientWaiterStarted = true
		go app.provisioningWatcher(app.session)
		app.startK8sClientWhenReady(app.session, app.ActiveType)
	}

	go app.refreshStackInfo()
}

// Routing
// =====================================================================================================================
// The route path is the only input to the router. A route starts at the home page, the resource table, a resource
// detail, a maintenance page or one of the cluster pages. The router runs on every render, so it only reads state.

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

// Background services
// =====================================================================================================================
// The dashboard keeps its data fresh with background goroutines. Each goroutine stops when the session closes. The
// Kubernetes client is the last service to start, because the API server needs time to come up.

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

						app.poller = newResourcePoller(client, session, activeType, app.nodeJobIdsPolled, app.provisioningResourceRows, func() {
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

func (app *stackUiApp) refreshClusterHealth() {
	app.mu.Lock()
	client := app.k8sClient
	session := app.session
	app.mu.Unlock()

	if client == nil || session == nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	health, err := client.NodeHealth(ctx)
	if err != nil {
		app.mu.Lock()
		if len(app.clusterHealth.Versions) > 0 {
			app.clusterHealth.Versions = nil
			ucx.AppUpdateUi(app)
		}
		app.mu.Unlock()
		return
	}

	app.mu.Lock()
	changed := !nodeHealthEqual(app.clusterHealth, health)
	if changed {
		app.clusterHealth = health
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()
}

func (app *stackUiApp) nodeJobIds() map[string]string {
	return app.nodeJobIdsWith(app.session, app.Stack)
}

func (app *stackUiApp) nodeJobIdsPolled() map[string]string {
	app.mu.Lock()
	session := app.session
	stack := app.Stack
	app.mu.Unlock()

	return app.nodeJobIdsWith(session, stack)
}

func (app *stackUiApp) nodeJobIdsWith(session *ucx.Session, stack *ucxsvc.Stack) map[string]string {
	record, ok := app.readClusterRecordWith(session, stack)
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
	return app.readClusterRecordWith(app.session, app.Stack)
}

func (app *stackUiApp) readClusterRecordWith(session *ucx.Session, stack *ucxsvc.Stack) (shared.ClusterRecord, bool) {
	if session != nil && stack != nil && stack.Ok {
		client, clientErr := shared.ClusterStateClientNewSession(session, stack.InstanceId)
		if clientErr == nil {
			ctx, cancel := dashboardSessionContext(session)
			record, found, revision, readErr := dashboardStateValueRead[shared.ClusterRecord](ctx, client, shared.ClusterStateRecordKey, "cluster record")
			cancel()
			if readErr == nil && found {
				shared.ClusterStateRecordRevisionSet(&record, revision)
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

func dashboardSessionContext(session *ucx.Session) (context.Context, context.CancelFunc) {
	base := context.Background()
	if session != nil && session.Context() != nil {
		base = session.Context()
	}
	return context.WithTimeout(base, dashboardClusterStateTimeout)
}

func (app *stackUiApp) dashboardContext() (context.Context, context.CancelFunc) {
	return dashboardSessionContext(app.session)
}

func (app *stackUiApp) clusterStateValueClient() (shared.ClusterStateClient, error) {
	session := app.session
	stack := app.Stack
	if session != nil && stack != nil && stack.Ok {
		client, err := shared.ClusterStateClientNewSession(session, stack.InstanceId)
		if err == nil {
			return client, nil
		}
	}
	return shared.ClusterStateClientNewHost()
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

// Shared helpers
// =====================================================================================================================
// These helpers are used by several pages. The async helper runs a slow action away from the render path and reports
// the result to the user.

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

func dashboardStateValueRead[T any](ctx context.Context, client shared.ClusterStateClient, key string, noun string) (T, bool, int64, error) {
	var zero T

	value, err := shared.ClusterStateValueReadEx(ctx, client, key)
	if err != nil {
		return zero, false, 0, fmt.Errorf("could not read the %s: %s", noun, err)
	}

	if !value.Found {
		return zero, false, value.Revision, nil
	}

	var result T
	err = json.Unmarshal(value.Value, &result)
	if err != nil {
		return zero, false, value.Revision, fmt.Errorf("could not parse the %s: %s", noun, err)
	}

	return result, true, value.Revision, nil
}

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

func LocalKubeconfigPath() string {
	return filepath.Join(shared.ManagementMountPath, "kubeconfig-internal")
}
