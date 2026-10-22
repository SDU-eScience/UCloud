package dashboard

import (
	"context"
	"fmt"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
)

const (
	dashboardActionViewYaml       = "viewYaml"
	dashboardActionEditYaml       = "editYaml"
	dashboardActionOpenShell      = "openShell"
	dashboardActionRolloutRestart = "rolloutRestart"
	dashboardActionDeleteResource = "deleteResource"
	dashboardActionOpenService    = "openService"
	dashboardActionGoToJob        = "goToJob"
	dashboardActionCopyNodeName   = "copyNodeName"
	dashboardActionCordonDrain    = "cordonDrain"
	dashboardActionUncordon       = "uncordon"
	dashboardActionUpgradeNode    = "upgradeNode"
	dashboardActionRemoveNode     = "removeNode"
	dashboardActionCreateResource = "createResource"
	dashboardActionAddMachine     = "addMachine"
	dashboardActionAddWorkerPool  = "addWorkerPool"
)

// The resource page
// =====================================================================================================================
// One page shows all three views. The home page is the default. The resource table is the browse view. The YAML
// editor or the log stream is the detail view. The route picks the view, so a reload returns to the same place.

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
		tableActions = append(tableActions, ucx.ResourceTableAction{
			Id:       dashboardActionViewYaml,
			Label:    "View YAML",
			Icon:     ucx.IconHeroCodeBracket,
			Shortcut: "y",
		})

		if activeDef.CanUpdate || app.ActiveType == containersTypeId {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:       dashboardActionEditYaml,
				Label:    "Edit",
				Icon:     ucx.IconHeroPencilSquare,
				Shortcut: "e",
			})
		}

		if app.ActiveType == "pods" || app.ActiveType == containersTypeId {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:       dashboardActionOpenShell,
				Label:    "Open shell",
				Icon:     ucx.IconHeroCommandLine,
				Shortcut: "s",
			})
		}

		if app.ActiveType == "ingresses" {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:    dashboardActionOpenService,
				Label: "Open service",
				Icon:  ucx.IconHeroArrowTopRightOnSquare,
				Kind:  ucx.ResourceTableActionOpenUrl,
			})
		}

		if rolloutRestartSupported(app.ActiveType) {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:       dashboardActionRolloutRestart,
				Label:    "Restart",
				Icon:     ucx.IconHeroArrowPath,
				Shortcut: "r",
			})
		}

		if dashboardResourceDeleteSupported(activeDef) {
			tableActions = append(tableActions, ucx.ResourceTableAction{
				Id:               dashboardActionDeleteResource,
				Label:            "Delete",
				Icon:             ucx.IconHeroTrash,
				Shortcut:         "Delete",
				Destructive:      true,
				SkipConfirmation: true,
			})
		}

		var groupAction *ucx.ResourceTableAction
		var trailingAction *ucx.ResourceTableAction
		if activeDef.CanCreate {
			trailingAction = &ucx.ResourceTableAction{
				Id:       dashboardActionCreateResource,
				Label:    "Create",
				Icon:     ucx.IconHeroPlusSmall,
				Color:    ucx.ColorPrimaryMain,
				Shortcut: "n",
			}
		}
		if app.ActiveType == "nodes" && app.clusterReadyForNodes() {
			tableActions = append(tableActions,
				ucx.ResourceTableAction{
					Id:       dashboardActionCopyNodeName,
					Label:    "Copy node name",
					Icon:     ucx.IconCopy,
					Kind:     ucx.ResourceTableActionCopyText,
					Shortcut: "c",
				},
				ucx.ResourceTableAction{
					Id:       dashboardActionGoToJob,
					Label:    "Go to job",
					Icon:     ucx.IconHeroArrowTopRightOnSquare,
					Shortcut: "o",
				},
				ucx.ResourceTableAction{
					Id:       dashboardActionOpenShell,
					Label:    "Open shell",
					Icon:     ucx.IconHeroCommandLine,
					Shortcut: "s",
				},
			)
			groupAction = &ucx.ResourceTableAction{
				Id:       dashboardActionAddMachine,
				Label:    "Add machine",
				Icon:     ucx.IconHeroPlusSmall,
				Shortcut: "a",
			}
			tableActions = append(tableActions,
				ucx.ResourceTableAction{
					Id:    dashboardActionCordonDrain,
					Label: "Cordon and drain",
					Icon:  ucx.IconBroom,
				},
				ucx.ResourceTableAction{
					Id:    dashboardActionUncordon,
					Label: "Uncordon",
					Icon:  ucx.IconRefresh,
				},
				ucx.ResourceTableAction{
					Id:    dashboardActionUpgradeNode,
					Label: "Upgrade Kubernetes",
					Icon:  ucx.IconHeroArrowUp,
				},
				ucx.ResourceTableAction{
					Id:               dashboardActionRemoveNode,
					Label:            "Remove node",
					Icon:             ucx.IconTrash,
					Shortcut:         "Delete",
					Destructive:      true,
					SkipConfirmation: true,
				},
			)
			trailingAction = &ucx.ResourceTableAction{
				Id:       dashboardActionAddWorkerPool,
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
			typeId, namespace, name, rowKey := rowActivationValue(ev.Value)
			app.handleRowActivated(typeId, namespace, name, rowKey)
		}).On(ucx.UiEventAction, func(ev ucx.UiEvent) {
			app.handleTableAction(ev)
		})}

		if len(app.resourceNavigation) > 1 {
			bottom = append(bottom, dashboardNavigationBack(app))
		}
		bottom = append(bottom, ucx.TableFilter("resourceFilter", app.ActiveType))
		if namespaced && app.ActiveFilter.isEmpty() {
			bottom = append(bottom, app.namespaceSelectorNode())
		}
		bottom = append(bottom, ucx.Box().Sx(ucx.SxFlexGrow(1)))
		if len(app.resourceNavigation) > 1 {
			bottom = append(bottom, dashboardNavigationBreadcrumbs(app))
		} else {
			bottom = append(bottom, ucx.TableCount("resourceCount", app.ActiveType).WithTitle(app.activeTypeLabel(app.allTypeDefs())))
		}
	}
	if inDetail && len(app.resourceNavigation) > 1 {
		bottom = []ucx.UiNode{
			dashboardNavigationBack(app),
			ucx.Box().Sx(ucx.SxFlexGrow(1)),
			dashboardNavigationBreadcrumbs(app),
		}
	}

	escapePath := ""
	if inDetail {
		ref, _ := parseResourceDetail(app.ResourceDetail)
		escapeType := ref.Type
		if escapeType == "provisioning" {
			escapeType = app.ActiveType
			if escapeType == navHomeId {
				escapeType = "nodes"
			}
			escapePath = app.browseRoute(escapeType, resourceFilter{})
		} else if escapeType == containersTypeId {
			escapePath = app.containersBackTarget()
		} else {
			escapePath = app.browseRoute(escapeType, app.ActiveFilter)
		}
	} else if app.ActiveType == containersTypeId && !app.ActiveFilter.isEmpty() {
		escapePath = app.browseRoute("pods", resourceFilter{})
	} else if app.filterOrigin != "" && !app.ActiveFilter.isEmpty() {
		escapePath = app.browseRoute(app.filterOrigin, resourceFilter{})
	}
	if len(app.resourceNavigation) > 1 {
		escapePath = app.resourceNavigation[len(app.resourceNavigation)-2].route
	}

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:        main,
		Bottom:         bottom,
		EscapePath:     escapePath,
		EscapeDisabled: escapePath == "",
	})}
}

func (app *stackUiApp) resourceDetailNode(detail string) ucx.UiNode {
	ref, _ := parseResourceDetail(detail)
	if ref.Type == "provisioning" {
		return ucx.JobLogsBound("detailInitLogs", "logJobId").
			Sx(ucx.SxP(16), ucx.SxBoxSizing("border-box"))
	}
	if ref.Type == containersTypeId {
		return ucx.ContainerLogsBoundEx(fmt.Sprintf("detailContainerLogs-%d", app.containerLogGeneration), "containerLogs").
			Sx(ucx.SxP(16), ucx.SxBoxSizing("border-box"))
	}
	return dashboardResourceEditorNode(app, detail)
}

func (app *stackUiApp) resourceDetailBottomNode(detail string) ucx.UiNode {
	ref, _ := parseResourceDetail(detail)
	typeId := ref.Type
	namespace := ref.Namespace
	name := ref.Name
	if name == "@create" {
		name = "Create"
	}
	container := ""
	if typeId == containersTypeId && ref.hasContainer {
		container = ref.Container
	}
	label := typeId
	if typeId == "provisioning" {
		label = "Provisioning"
	} else if typeId == containersTypeId {
		label = "Container logs"
	} else if def, ok := app.resolveType(typeId); ok {
		label = def.Label
	}

	backTarget := "browse/" + typeId
	if typeId == "provisioning" {
		backTarget = "browse/" + app.ActiveType
		if backTarget == "browse/"+navHomeId {
			backTarget = "browse/nodes"
		}
	} else if typeId == containersTypeId {
		backTarget = app.containersBackTarget()
	}

	segments := []string{label}
	if namespace != "" {
		segments = append(segments, namespace)
	}
	if name != "" {
		segments = append(segments, name)
	}
	if container != "" {
		segments = append(segments, container)
	}

	return ucx.Toolbar().Children(
		ucx.ButtonEx("backToTable", "Back to table", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.RouterPushPage(app, backTarget)
		}),
		ucx.Box(),
		ucx.Text(strings.Join(segments, " / ")).Sx(ucx.SxColor(ucx.ColorTextSecondary)),
	)
}

func (app *stackUiApp) namespaceSelectorNode() ucx.UiNode {
	options := make([]ucx.Option, 0, len(app.Namespaces)+1)
	options = append(options, ucx.Option{Key: "", Value: "All namespaces"})
	for _, namespace := range app.Namespaces {
		options = append(options, ucx.Option{Key: namespace, Value: namespace})
	}

	return ucx.Select("namespaceSelect", "", "activeNamespace", options).Sx(ucx.SxWidth(280)).WithShortcutKey("s")
}

// Table events
// =====================================================================================================================
// A table event names an action and a row. An action without a row is a group action or a toolbar action. The code
// below sends each event to the function that owns the action.

func (app *stackUiApp) handleTableAction(ev ucx.UiEvent) {
	if ev.Event != string(ucx.UiEventAction) || ev.Value.Kind != ucx.ValueObject {
		return
	}

	actionId := ucx.ValueAsString(ev.Value.Object["actionId"])
	rowKey := ucx.ValueAsString(ev.Value.Object["rowKey"])
	group := ucx.ValueAsString(ev.Value.Object["group"])
	if actionId == dashboardActionCreateResource {
		dashboardResourceCreateOpen(app)
		return
	}

	if rowKey != "" {
		app.handleRowAction(ev)
		return
	}

	switch actionId {
	case dashboardActionCordonDrain, dashboardActionUncordon, dashboardActionUpgradeNode, dashboardActionRemoveNode:
		ucxsvc.UiSendFailure(app, "Select a single node before using this action")
	case dashboardActionRolloutRestart, dashboardActionDeleteResource:
		ucxsvc.UiSendFailure(app, "Select a single resource before using this action")
	case dashboardActionAddMachine:
		if group == "" {
			return
		}
		app.TargetDiskGb = 0
		app.TargetCount = 0
		ucxsvc.RouterPushPage(app, "control/"+group)
		ucx.AppUpdateUi(app)
	case dashboardActionAddWorkerPool:
		app.NewPoolName = ""
		app.Machine = accapi.ProductReference{}
		app.TargetDiskGb = 0
		app.TargetCount = 0
		ucxsvc.RouterPushPage(app, "control/new-pool")
		ucx.AppUpdateUi(app)
	}
}

func (app *stackUiApp) handleRowActivated(tableId string, namespace string, name string, rowKey string) {
	if tableId == "" || name == "" {
		return
	}

	if _, ok := app.resolveType(tableId); !ok {
		return
	}

	if jobId := app.provisioningJobId(name); jobId != "" {
		dashboardNavigationStart(app)
		ref := resourceDetailRef{Type: "provisioning", Namespace: namespace, Name: name}
		app.ResourceDetail = ref.String()
		app.prevDetail = app.ResourceDetail
		app.LogJobId = jobId
		app.ResourceYaml = ""
		dashboardNavigationPush(app, dashboardResourceDetailEncode(ref), strings.TrimPrefix(namespace+"/"+name, "/"))
		ucx.AppUpdateUi(app)
		return
	}

	if tableId == "pods" {
		app.openPodContainers(namespace, name)
		return
	}

	if tableId == containersTypeId {
		app.openContainerLogs(namespace, name, rowKey)
		return
	}

	if isWorkloadOwnerType(tableId) {
		app.openOwnedPods(tableId, namespace, name, rowKey)
		return
	}

	if tableId == "nodes" {
		app.openNodePods(name)
		return
	}

	app.openResourceDetail(tableId, namespace, name, false)
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

	if actionId == dashboardActionViewYaml {
		app.openYamlForRowKey(rowKey, false)
		return
	}

	if actionId == dashboardActionEditYaml {
		app.openYamlForRowKey(rowKey, true)
		return
	}

	if actionId == dashboardActionOpenShell && (app.ActiveType == "pods" || app.ActiveType == containersTypeId) {
		app.openShellToPod(rowKey)
		return
	}

	if actionId == dashboardActionRolloutRestart {
		app.openRolloutRestartDialog(rowKey)
		return
	}
	if actionId == dashboardActionDeleteResource {
		dashboardResourceDeleteOpen(app, rowKey)
		return
	}

	if actionId != dashboardActionGoToJob &&
		actionId != dashboardActionOpenShell &&
		actionId != dashboardActionCordonDrain &&
		actionId != dashboardActionUncordon &&
		actionId != dashboardActionUpgradeNode &&
		actionId != dashboardActionRemoveNode {
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
	case dashboardActionCordonDrain, dashboardActionUpgradeNode, dashboardActionRemoveNode:
		if strings.HasPrefix(rowKey, provisioningRowKeyPrefix) {
			if actionId == dashboardActionRemoveNode {
				maintenanceOpenWithMode(app, nodeName, "", maintenanceModeRemove)
			}
			return
		}
		nodeUid, known := app.nodeUidForName(nodeName)
		if !known {
			return
		}
		if actionId == dashboardActionUpgradeNode {
			maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeUpgrade)
		} else if actionId == dashboardActionRemoveNode {
			maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeRemove)
		} else {
			maintenanceOpen(app, nodeName, nodeUid)
		}
	case dashboardActionUncordon:
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
		maintenanceSubmitAsync(app, maintenanceSubmit{kind: "uncordon"}, nodeName, nodeUid, maintenance.Options{})
	case dashboardActionGoToJob:
		jobId := app.nodeJobIds()[nodeName]
		if jobId == "" {
			return
		}
		ucxsvc.OpenUrl(app, fmt.Sprintf("/jobs/properties/%s", jobId))
	case dashboardActionOpenShell:
		jobId := app.nodeJobIds()[nodeName]
		if jobId == "" {
			return
		}
		ucxsvc.TerminalOpenShellToJob(app, jobId, 0, "", false)
	}
}

// Opening a resource
// =====================================================================================================================
// The dashboard has no separate URL for a subview. A detail opens by pushing a route onto the navigation stack. The
// same code builds the route, so a shared link always works.

func (app *stackUiApp) openResourceTable(typeId string, filter resourceFilter, origin string, label string) {
	dashboardNavigationStart(app)
	app.ActiveType = typeId
	app.ActiveFilter = filter
	app.filterOrigin = origin
	app.ResourceDetail = ""
	app.prevDetail = ""
	app.ResourceYaml = ""
	if app.poller != nil {
		app.poller.SetActiveFilter(filter)
		app.poller.SetActiveType(typeId)
	}
	dashboardNavigationPush(app, app.browseRoute(typeId, filter), label)
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) openResourceDetail(tableId string, namespace string, name string, editing bool) {
	if editing && app.resourceEditor.Saving {
		return
	}
	dashboardNavigationStart(app)
	ref := resourceDetailRef{Type: tableId, Namespace: namespace, Name: name}
	app.ResourceDetail = ref.String()
	app.prevDetail = app.ResourceDetail
	dashboardResourceEditorLoad(app, app.ResourceDetail, editing)
	dashboardNavigationPush(app, dashboardResourceDetailEncode(ref), strings.TrimPrefix(namespace+"/"+name, "/"))
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) openYamlForRowKey(rowKey string, editing bool) {
	if app.poller == nil {
		return
	}
	if strings.HasPrefix(rowKey, provisioningRowKeyPrefix) {
		return
	}

	row, ok := app.poller.rowForKey(rowKey)
	if !ok || len(row.Cells) == 0 {
		return
	}

	typeId := app.ActiveType
	namespace := row.Group
	name := row.Cells[0]

	if typeId == containersTypeId {
		podUid, _ := containerRowKeyParts(rowKey)

		pod, ok := app.poller.objectForUid(podUid)
		if !ok {
			return
		}

		typeId = "pods"
		namespace = pod.GetNamespace()
		name = pod.GetName()
	}

	app.openResourceDetail(typeId, namespace, name, editing)
}

func (app *stackUiApp) openPodContainers(namespace string, podName string) {
	fieldSelector := ""
	originPath := podName
	if namespace != "" {
		fieldSelector = "metadata.namespace=" + namespace + ",metadata.name=" + podName
		originPath = namespace + "/" + podName
	} else {
		fieldSelector = "metadata.name=" + podName
	}

	filter := resourceFilter{
		fieldSelector: fieldSelector,
		title:         "Containers of pods/" + originPath,
	}
	app.openResourceTable(containersTypeId, filter, "pods", originPath)
}

func (app *stackUiApp) openContainerLogs(namespace string, containerName string, rowKey string) {
	if app.poller == nil || rowKey == "" {
		return
	}

	podUid, _ := containerRowKeyParts(rowKey)

	pod, ok := app.poller.objectForUid(podUid)
	if !ok {
		return
	}

	podNamespace := pod.GetNamespace()
	if podNamespace == "" {
		podNamespace = namespace
	}
	podName := pod.GetName()

	dashboardNavigationStart(app)
	ref := resourceDetailRef{
		Type:         containersTypeId,
		Namespace:    podNamespace,
		Name:         podName,
		Container:    containerName,
		hasContainer: true,
	}
	app.ResourceDetail = ref.String()
	app.prevDetail = app.ResourceDetail
	app.ResourceYaml = ""
	app.stopContainerLogsLocked()
	app.startContainerLogsLocked(podNamespace, podName, containerName)

	dashboardNavigationPush(app, dashboardResourceDetailEncode(ref), containerName)
	ucx.AppUpdateUi(app)
}

func (app *stackUiApp) openOwnedPods(tableId string, namespace string, name string, rowKey string) {
	if app.poller == nil {
		return
	}

	var owner *unstructured.Unstructured
	if rowKey != "" {
		owner, _ = app.poller.objectForUid(rowKey)
	}
	if owner == nil {
		return
	}

	labelSelector := workloadLabelSelector(owner)
	if labelSelector == "" {
		ucxsvc.UiSendFailure(app, "This workload has no pod selector")
		return
	}

	originPath := name
	if namespace != "" {
		originPath = namespace + "/" + name
	}

	originLabel := tableId
	if def, ok := app.resolveType(tableId); ok && def.Label != "" {
		originLabel = def.Label
	}

	filter := resourceFilter{
		labelSelector: labelSelector,
		title:         "Pods of " + originLabel + "/" + originPath,
	}
	if namespace != "" {
		filter.fieldSelector = "metadata.namespace=" + namespace
	}

	app.openResourceTable("pods", filter, tableId, originPath)
}

func (app *stackUiApp) openNodePods(nodeName string) {
	if app.poller == nil {
		return
	}

	filter := resourceFilter{
		fieldSelector: "spec.nodeName=" + nodeName,
		title:         "Pods on nodes/" + nodeName,
	}

	app.openResourceTable("pods", filter, "nodes", nodeName)
}

func (app *stackUiApp) containersBackTarget() string {
	if !app.ActiveFilter.isEmpty() {
		return app.browseRoute(containersTypeId, app.ActiveFilter)
	}

	ref, ok := parseResourceDetail(app.ResourceDetail)
	if ok && ref.Type == containersTypeId && ref.hasContainer {
		fieldSelector := "metadata.name=" + ref.Name
		if ref.Namespace != "" {
			fieldSelector = "metadata.namespace=" + ref.Namespace + ",metadata.name=" + ref.Name
		}
		return app.browseRoute(containersTypeId, resourceFilter{fieldSelector: fieldSelector})
	}

	return app.browseRoute(containersTypeId, resourceFilter{})
}

func isWorkloadOwnerType(typeId string) bool {
	switch typeId {
	case "deployments", "statefulsets", "daemonsets", "jobs":
		return true
	default:
		return false
	}
}

func containerRowKeyParts(rowKey string) (string, string) {
	podUid, containerName, _ := strings.Cut(rowKey, "/")
	return podUid, containerName
}

// Shell access
// =====================================================================================================================
// The shell runs from a control plane node with a UCloud job. The command escapes every name, so a pod name cannot
// break out of the shell command.

func (app *stackUiApp) openShellToPod(rowKey string) {
	if app.poller == nil {
		return
	}
	if strings.HasPrefix(rowKey, provisioningRowKeyPrefix) {
		return
	}

	namespace := ""
	podName := ""
	containerName := ""

	if app.ActiveType == containersTypeId {
		var podUid string
		podUid, containerName = containerRowKeyParts(rowKey)

		pod, ok := app.poller.objectForUid(podUid)
		if !ok {
			return
		}

		namespace = pod.GetNamespace()
		podName = pod.GetName()
	} else {
		row, ok := app.poller.rowForKey(rowKey)
		if !ok || len(row.Cells) == 0 {
			return
		}

		namespace = row.Group
		podName = row.Cells[0]
	}

	if namespace == "" || podName == "" {
		return
	}

	shellCommand := "sh -c 'if command -v bash >/dev/null 2>&1; then exec bash; else exec sh; fi'"
	title := podName
	if containerName != "" {
		title = podName + "/" + containerName
	}
	command := fmt.Sprintf(
		"printf '\\033]777;ucloud-shell-ready\\007\\033]0;%%s\\007' %s && kubectl exec -it -n %s %s -- %s",
		orcapi.EscapeBash(title),
		orcapi.EscapeBash(namespace),
		orcapi.EscapeBash(podName),
		shellCommand,
	)
	if containerName != "" {
		command = fmt.Sprintf(
			"printf '\\033]777;ucloud-shell-ready\\007\\033]0;%%s\\007' %s && kubectl exec -it -n %s %s -c %s -- %s",
			orcapi.EscapeBash(title),
			orcapi.EscapeBash(namespace),
			orcapi.EscapeBash(podName),
			orcapi.EscapeBash(containerName),
			shellCommand,
		)
	}
	command += "; printf '\\033]777;ucloud-shell-exit;%s\\007' \"$?\""

	jobId, ok := app.firstControlPlaneNodeWithShell()
	if !ok {
		ucxsvc.UiSendFailure(app, "No control-plane node with a UCloud job is available to run the shell from.")
		return
	}

	ucxsvc.TerminalOpenShellToJobEx(app, ucxapi.TerminalOpenShellToJobRequest{
		JobId:         jobId,
		Command:       command,
		SuppressUntil: "\x1b]777;ucloud-shell-ready\x07",
	})
}

func (app *stackUiApp) firstControlPlaneNodeWithShell() (string, bool) {
	client := app.k8sClient
	session := app.session
	if client == nil || session == nil {
		return "", false
	}

	ctx, cancel := context.WithTimeout(session.Context(), 10*time.Second)
	defer cancel()

	nodesDef, ok := ResourceType("nodes")
	if !ok {
		return "", false
	}

	nodeJobIds := app.nodeJobIds()

	firstCordoned := ""
	for _, labelSelector := range []string{
		"node-role.kubernetes.io/control-plane",
		"node-role.kubernetes.io/master",
	} {
		items, err := client.ListWithLabelSelector(ctx, nodesDef, labelSelector)
		if err != nil {
			return "", false
		}

		for i := range items {
			node := &items[i]

			jobId, hasJob := nodeJobIds[node.GetName()]
			if !hasJob || jobId == "" {
				continue
			}

			unschedulable, ok, _ := unstructured.NestedBool(node.Object, "spec", "unschedulable")
			cordoned := ok && unschedulable
			if !cordoned {
				return jobId, true
			}

			if firstCordoned == "" {
				firstCordoned = jobId
			}
		}

		if firstCordoned != "" {
			return firstCordoned, true
		}
	}

	return "", false
}

func (app *stackUiApp) nodeUidForName(nodeName string) (string, bool) {
	if app.poller == nil {
		return "", false
	}
	return app.poller.nodeUidForRowName(nodeName)
}

func rowActivationValue(value ucx.Value) (string, string, string, string) {
	if value.Kind != ucx.ValueObject {
		return "", "", "", ""
	}

	tableId := ucx.ValueAsString(value.Object["stateKey"])
	if tableId == "" {
		tableId = ucx.ValueAsString(value.Object["tableId"])
	}
	group := ucx.ValueAsString(value.Object["group"])
	rowKey := ucx.ValueAsString(value.Object["rowKey"])

	cells := ""
	if rawCells, ok := value.Object["cells"]; ok && rawCells.Kind == ucx.ValueList && len(rawCells.List) > 0 {
		cells = ucx.ValueAsString(rawCells.List[0])
	}

	return tableId, group, cells, rowKey
}
