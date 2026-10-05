package dashboard

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

const maintenanceDefaultTimeoutSeconds = 600

const maintenanceUiMessageSendTimeout = 10 * time.Second

const maintenanceModeUpgrade = "upgrade"
const maintenanceModeDrain = "drain"

func maintenanceNodeFromRoute(routePath string) string {
	rest := strings.TrimPrefix(routePath, "maintenance")
	rest = strings.TrimPrefix(rest, "/")
	if rest == "" {
		return ""
	}

	name, err := url.PathUnescape(rest)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(name)
}

func maintenanceOpen(app *stackUiApp, nodeName string, nodeUid string) {
	maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeDrain)
}

func maintenanceOpenWithMode(app *stackUiApp, nodeName string, nodeUid string, mode string) {
	nodeName = strings.TrimSpace(nodeName)
	nodeUid = strings.TrimSpace(nodeUid)
	if nodeName == "" || nodeUid == "" {
		return
	}

	app.maintenanceNodeName = nodeName
	app.maintenanceNodeUid = nodeUid
	app.maintenanceRetryOptionsFor = ""
	app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
	app.MaintenanceDrain = false
	app.MaintenanceDeleteVolatilePods = false
	app.MaintenanceBypassDisruptionBudgets = false
	app.MaintenanceForceDelete = false
	app.MaintenanceTargetRelease = ""
	app.maintenanceMode = mode

	ucxsvc.RouterPushPage(app, fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName)))
}

func maintenanceOptionsFromApp(app *stackUiApp) maintenance.Options {
	return maintenance.Options{
		TimeoutSeconds:          app.MaintenanceTimeoutSeconds,
		DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
		BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
		ForceDelete:             app.MaintenanceForceDelete,
	}
}

func maintenanceSubmitAsync(
	app *stackUiApp,
	action string,
	nodeName string,
	nodeUid string,
	options maintenance.Options,
) {
	if app.MaintenanceBusy {
		return
	}

	app.MaintenanceBusy = true
	targetRelease := app.MaintenanceTargetRelease

	session := *app.Session()
	if session == nil {
		app.MaintenanceBusy = false
		return
	}

	ucx.AppUpdateModelPatch(session, map[string]ucx.Value{
		"maintenanceBusy": ucx.VBool(true),
	})

	go func() {
		var err error
		switch action {
		case "cordon":
			err = maintenance.Cordon(nodeName, nodeUid, options)
		case "start":
			err = maintenance.Start(nodeName, nodeUid, options)
		case "retry":
			err = maintenance.Start(nodeName, nodeUid, options)
		case "upgrade-start":
			err = maintenance.UpgradeStart(nodeName, nodeUid, targetRelease, options)
		case "upgrade-retry":
			err = maintenance.UpgradeStart(nodeName, nodeUid, targetRelease, options)
		case "uncordon":
			err = maintenance.Uncordon(nodeName, nodeUid)
		case "cancel":
			err = maintenance.CancelActiveOperation(nodeName)
		}

		app.mu.Lock()
		app.MaintenanceBusy = false
		updatedUi := app.UserInterface()
		updatedModel := ucx.AppSnapshot(app)
		app.mu.Unlock()

		if session.Context().Err() == nil {
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
			if err != nil {
				maintenanceSendUiMessage(session, err.Error(), false)
			} else if !maintenanceSubmitStartsOperation(action) {
				maintenanceSendUiMessage(session, maintenanceSubmitMessage(action, nodeName), true)
			}
		}
	}()
}

func maintenanceSubmitStartsOperation(action string) bool {
	switch action {
	case "cordon":
		return true
	case "start":
		return true
	case "retry":
		return true
	case "upgrade-start":
		return true
	case "upgrade-retry":
		return true
	default:
		return false
	}
}

func maintenanceSendUiMessage(session *ucx.Session, message string, success bool) {
	if session == nil || session.Context().Err() != nil {
		return
	}

	ctx, cancel := context.WithTimeout(session.Context(), maintenanceUiMessageSendTimeout)
	defer cancel()

	_, _ = ucxapi.UiSendMessage.InvokeEx(ctx, session, ucxapi.UiSendMessageRequest{
		Message: message,
		Success: success,
	})
}

func maintenanceSubmitMessage(action string, nodeName string) string {
	switch action {
	case "uncordon":
		return fmt.Sprintf("Uncordon of %s started", nodeName)
	case "cancel":
		return fmt.Sprintf("The operation on %s was asked to stop", nodeName)
	}
	return ""
}

func maintenancePage(app *stackUiApp) []ucx.UiNode {
	nodeName := maintenanceNodeFromRoute(app.RoutePath)

	if nodeName == "" {
		return []ucx.UiNode{app.appShell(appShellProps{
			Content: []ucx.UiNode{shellContentBox(800,
				ucx.Text("Select a node in the node table to start maintenance."),
			)},
			EscapePath: "browse/nodes",
		})}
	}

	if app.maintenanceNodeName != nodeName {
		app.maintenanceNodeName = nodeName
		app.maintenanceNodeUid = ""
		app.maintenanceRetryOptionsFor = ""
		app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
		app.MaintenanceDrain = false
		app.MaintenanceDeleteVolatilePods = false
		app.MaintenanceBypassDisruptionBudgets = false
		app.MaintenanceForceDelete = false
		app.MaintenanceTargetRelease = ""
		app.maintenanceMode = ""
	}

	bottom := []ucx.UiNode{shellBottomNode(app, "nodes", nodeName, "Node maintenance")}

	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return []ucx.UiNode{app.appShell(appShellProps{
			Content:    []ucx.UiNode{shellContentBox(800, ucx.Text("Could not read the cluster record."))},
			Bottom:     bottom,
			EscapePath: "browse/nodes",
		})}
	}

	known := false
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName {
			known = true
			break
		}
	}
	if !known {
		return []ucx.UiNode{app.appShell(appShellProps{
			Content:    []ucx.UiNode{shellContentBox(800, ucx.Text(fmt.Sprintf("Unknown node: %s", nodeName)))},
			Bottom:     bottom,
			EscapePath: "browse/nodes",
		})}
	}

	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return []ucx.UiNode{app.appShell(appShellProps{
			Content:    []ucx.UiNode{shellContentBox(800, ucx.Text(fmt.Sprintf("Could not read the maintenance state: %s", err)))},
			Bottom:     bottom,
			EscapePath: "browse/nodes",
		})}
	}

	operation, hasOperation := snapshot[nodeName]

	targetUid := ""
	if app.maintenanceNodeName == nodeName && app.maintenanceNodeUid != "" {
		targetUid = app.maintenanceNodeUid
	} else if hasOperation {
		targetUid = operation.NodeUid
	}
	operationMatchesTarget := hasOperation && operation.NodeUid == targetUid
	isUpgrade := hasOperation && maintenance.KindIsUpgrade(operation.Kind)
	recoveryBlocked := isUpgrade && operation.ExecutorSubmitted && operation.RecoveryRequired

	mutationsBlocked := app.Stack == nil

	content := []ucx.UiNode{
		ucx.H3Ex("maintenanceNodeHeading", nodeName),
	}

	if hasOperation && operation.Error != "" {
		content = append(content, ucx.Text(fmt.Sprintf("Error: %s", operation.Error)).Sx(ucx.SxColor(ucx.ColorErrorMain)))
	}

	logJobId := ""
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName && record.Nodes[i].JobId != "" {
			logJobId = record.Nodes[i].JobId
			break
		}
	}
	if logJobId != "" && hasOperation && maintenance.PhaseActive(operation.Phase) {
		content = append(content,
			ucx.JobLogs(fmt.Sprintf("maintenanceLog-%s", logJobId), logJobId),
		)
	}

	if mutationsBlocked {
		content = append(content, ucx.Text(
			"Maintenance actions are unavailable until the application is connected to the stack.",
		))
	}

	if hasOperation && maintenance.PhaseActive(operation.Phase) && !mutationsBlocked && !(isUpgrade && operation.ExecutorSubmitted) {
		cancelLabel := "Cancel drain"
		if operation.Kind == maintenance.KindUncordon {
			cancelLabel = "Cancel uncordon"
		} else if operation.Kind == maintenance.KindCordon {
			cancelLabel = "Cancel cordon"
		} else if isUpgrade {
			cancelLabel = "Cancel upgrade"
		}
		content = append(content, ucx.ButtonEx(
			"maintenanceCancel",
			cancelLabel,
			ucx.ColorErrorMain,
			"",
			"",
			"",
		).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			if app.MaintenanceBusy || app.Stack == nil {
				return
			}
			maintenanceSubmitAsync(app, "cancel", nodeName, "", maintenance.Options{})
		}))
	}

	retryable := operationMatchesTarget && operation.Kind != maintenance.KindUncordon &&
		operation.Kind != maintenance.KindCordon &&
		(operation.Phase == maintenance.PhaseFailed ||
			operation.Phase == maintenance.PhaseBlocked ||
			operation.Phase == maintenance.PhaseCancelled)

	nodeCordoned := app.nodeIsCordoned(nodeName)

	if !hasOperation && targetUid == "" {
		content = append(content, ucx.Text(
			"Select the node in the live node table to start maintenance on it.",
		))
	}
	if isUpgrade && operation.ExecutorSubmitted && maintenance.PhaseActive(operation.Phase) {
		content = append(content, ucx.Text("The upgrade is running on the node and cannot be cancelled."))
	}
	if recoveryBlocked && !operationMatchesTarget {
		content = append(content, ucx.Text(
			"The earlier node requires upgrade recovery. Retry the upgrade or uncordon it in the node table.",
		))
	}
	if recoveryBlocked && operationMatchesTarget && !maintenance.PhaseActive(operation.Phase) {
		content = append(content, ucx.Text(
			"The upgrade failed and the node remains cordoned. Retry the upgrade, or use Uncordon in the node table to return the node to service on its current release.",
		))
	}
	upgradePage := app.maintenanceMode == maintenanceModeUpgrade || (app.maintenanceMode == "" && isUpgrade)
	if upgradePage && !mutationsBlocked && targetUid != "" && !maintenance.PhaseActive(operation.Phase) {
		if retryable && isUpgrade {
			if app.maintenanceRetryOptionsFor != nodeName {
				app.maintenanceRetryOptionsFor = nodeName
				app.MaintenanceTimeoutSeconds = operation.Options.TimeoutSeconds
				app.MaintenanceDeleteVolatilePods = operation.Options.DeleteVolatilePods
				app.MaintenanceBypassDisruptionBudgets = operation.Options.BypassDisruptionBudgets
				app.MaintenanceTargetRelease = operation.TargetRelease
			}
			content = append(content, maintenanceUpgradeForm(app, "upgrade-retry", nodeName, targetUid, operation, record))
		} else if !recoveryBlocked {
			content = append(content, maintenanceUpgradeForm(app, "upgrade-start", nodeName, targetUid, operation, record))
		}
	}

	if !upgradePage && !recoveryBlocked && !mutationsBlocked && targetUid != "" && !maintenance.PhaseActive(operation.Phase) {
		if nodeCordoned {
			content = append(content, ucx.Text(
				"The node is cordoned. Use Uncordon in the node table to allow scheduling again.",
			))
		} else if retryable && !isUpgrade {
			if app.maintenanceRetryOptionsFor != nodeName {
				app.maintenanceRetryOptionsFor = nodeName
				app.MaintenanceTimeoutSeconds = operation.Options.TimeoutSeconds
				if app.MaintenanceTimeoutSeconds <= 0 {
					app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
				}
				app.MaintenanceDeleteVolatilePods = operation.Options.DeleteVolatilePods
				app.MaintenanceBypassDisruptionBudgets = operation.Options.BypassDisruptionBudgets
				app.MaintenanceForceDelete = operation.Options.ForceDelete
			}
			content = append(content, maintenanceDrainForm(app, "retry", nodeName, targetUid))
		} else {
			if app.MaintenanceTimeoutSeconds <= 0 {
				app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
			}
			content = append(content, maintenanceDrainForm(app, "start", nodeName, targetUid))
		}
	}

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:    []ucx.UiNode{shellContentBox(800, content...)},
		Bottom:     bottom,
		EscapePath: "browse/nodes",
	})}
}

func (app *stackUiApp) nodeIsCordoned(nodeName string) bool {
	if app.poller == nil {
		return false
	}
	rowKey, ok := app.poller.nodeUidForRowName(nodeName)
	if !ok {
		return false
	}
	row, ok := app.poller.nodeRowForKey(rowKey)
	return ok && len(row.Cells) > 2 && row.Cells[2] == "Cordoned"
}

func maintenanceDrainForm(app *stackUiApp, mode string, nodeName string, nodeUid string) ucx.UiNode {
	drainEnabled := app.MaintenanceDrain || mode == "retry"

	submitId := "maintenanceDrainSubmit"
	submitLabel := "Cordon and drain"
	submitColor := ucx.ColorErrorMain
	holdToConfirm := true
	if mode == "start" && !drainEnabled {
		submitLabel = "Cordon node"
		submitColor = ucx.ColorWarningMain
		holdToConfirm = false
	}

	children := []ucx.UiNode{}
	if mode == "start" {
		children = append(children, ucx.Checkbox(
			"maintenanceDrain",
			"**Drain:** evict all pods from the node after it is cordoned. Replacements may be unavailable immediately.",
			"maintenanceDrain",
			false,
		))
	}
	if drainEnabled {
		children = append(children,
			ucx.FieldGroupNode().Children(
				ucx.FieldRowNodeEx("maintenanceTimeoutRow", "Timeout (seconds)", "maintenanceTimeoutSeconds").
					FieldRowDescription("The drain gives up after this many seconds.").
					FieldRowRequired(true).
					Children(ucx.InputNumber(
						"maintenanceTimeout",
						"",
						"maintenanceTimeoutSeconds",
						30,
						3600,
					)),
				ucx.Checkbox(
					"maintenanceDeleteVolatilePods",
					"**Delete volatile pods:** also evict pods with emptyDir data and pods without a controller. Their data is lost.",
					"maintenanceDeleteVolatilePods",
					false,
				),
				ucx.Checkbox(
					"maintenanceBypassDisruptionBudgets",
					"**Bypass budgets:** evict pods even when a `PodDisruptionBudget` forbids it.",
					"maintenanceBypassDisruptionBudgets",
					false,
				),
				ucx.Checkbox(
					"maintenanceForceDelete",
					"**Force delete:** skip graceful shutdown with a zero grace period. Requires bypassing budgets.",
					"maintenanceForceDelete",
					false,
				),
			),
		)
	}

	submit := ucx.SubmitButton(submitId, submitLabel, submitColor).
		ButtonBusy("maintenanceBusy").
		Sx(ucx.SxJustifyEnd)
	if holdToConfirm {
		submit = submit.ButtonHoldToConfirm(true)
	}
	children = append(children, submit)

	return ucx.Form("maintenanceDrainForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.MaintenanceBusy || app.Stack == nil {
			return
		}

		options := maintenanceOptionsFromApp(app)
		if options.ForceDelete && !options.BypassDisruptionBudgets {
			ucxsvc.UiSendFailure(app, "A zero grace period requires bypassing the disruption budgets")
			return
		}

		action := mode
		if mode == "start" && !app.MaintenanceDrain {
			action = "cordon"
		}

		maintenanceSubmitAsync(app, action, nodeName, nodeUid, options)
	}).Children(children...)
}

func (app *stackUiApp) maintenanceNodeVersion(nodeName string, record shared.ClusterRecord) string {
	if app.poller != nil {
		row, ok := app.poller.nodeRowByName(nodeName)
		if ok && len(row.Cells) > 5 && row.Cells[5] != "" {
			return row.Cells[5]
		}
	}
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			return node.DesiredVersion
		}
	}
	return ""
}

func maintenanceUpgradeReleaseOptions(currentVersion string) []ucx.Option {
	options := []ucx.Option{}
	for _, release := range shared.K3sCatalog {
		if !shared.NodeAgentUpgradeAllowed(currentVersion, release.Release) {
			continue
		}
		options = append(options, ucx.Option{Key: release.Release, Value: release.Release})
	}
	return options
}

func maintenanceUpgradeForm(
	app *stackUiApp,
	mode string,
	nodeName string,
	nodeUid string,
	operation maintenance.Operation,
	record shared.ClusterRecord,
) ucx.UiNode {
	isRetry := mode == "upgrade-retry"
	currentVersion := app.maintenanceNodeVersion(nodeName, record)
	options := maintenanceUpgradeReleaseOptions(currentVersion)
	submitLabel := "Upgrade Kubernetes"
	if isRetry {
		submitLabel = "Retry upgrade"
		app.MaintenanceTargetRelease = operation.TargetRelease
		options = []ucx.Option{{Key: operation.TargetRelease, Value: operation.TargetRelease}}
	}
	if len(options) == 0 {
		return ucx.Text(fmt.Sprintf(
			"The node already runs the newest supported release, %s.",
			currentVersion,
		))
	}
	selected := false
	for _, option := range options {
		if option.Key == app.MaintenanceTargetRelease {
			selected = true
			break
		}
	}
	if !selected {
		app.MaintenanceTargetRelease = options[0].Key
	}
	if app.MaintenanceTimeoutSeconds <= 0 {
		app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
	}
	children := []ucx.UiNode{}
	controlPlanes := 0
	targetIsControlPlane := false
	for _, node := range record.Nodes {
		if node.Group == shared.GroupControlPlane {
			controlPlanes++
			if node.Hostname == nodeName {
				targetIsControlPlane = true
			}
		}
	}
	if targetIsControlPlane && controlPlanes == 1 {
		children = append(children, ucx.Text("This is the only control-plane node. Upgrading it causes control-plane downtime.").Sx(ucx.SxColor(ucx.ColorWarningMain)))
	}
	if isRetry {
		children = append(children, ucx.Text(fmt.Sprintf("Retry target: %s", operation.TargetRelease)))
	} else {
		children = append(children, ucx.Text(fmt.Sprintf("Current version: %s", currentVersion)))
		children = append(children, ucx.FieldRowNodeEx("maintenanceTargetReleaseRow", "Target release", "maintenanceTargetRelease").
			FieldRowRequired(true).
			Children(ucx.Select("maintenanceTargetReleaseSelect", "", "maintenanceTargetRelease", options)))
	}
	children = append(children,
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("maintenanceUpgradeTimeoutRow", "Drain timeout (seconds)", "maintenanceTimeoutSeconds").
				FieldRowDescription("The drain gives up after this many seconds. Upgrading and verifying the node have a separate time limit.").
				FieldRowRequired(true).
				Children(ucx.InputNumber("maintenanceUpgradeTimeout", "", "maintenanceTimeoutSeconds", 30, 3600)),
			ucx.Checkbox("maintenanceDeleteVolatilePods", "**Delete volatile pods:** also evict pods with emptyDir data and pods without a controller. Their data is lost.", "maintenanceDeleteVolatilePods", false),
			ucx.Checkbox("maintenanceBypassDisruptionBudgets", "**Bypass budgets:** evict pods even when a `PodDisruptionBudget` forbids it.", "maintenanceBypassDisruptionBudgets", false),
		),
		ucx.SubmitButton("maintenanceUpgradeSubmit", submitLabel, ucx.ColorErrorMain).
			ButtonBusy("maintenanceBusy").
			ButtonHoldToConfirm(true).
			Sx(ucx.SxJustifyEnd),
	)
	return ucx.Form("maintenanceUpgradeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.MaintenanceBusy || app.Stack == nil {
			return
		}
		target := app.MaintenanceTargetRelease
		if isRetry {
			target = operation.TargetRelease
		}
		valid := false
		for _, option := range options {
			if option.Key == target {
				valid = true
				break
			}
		}
		if !valid {
			ucxsvc.UiSendFailure(app, "Please select a supported target release")
			return
		}
		app.MaintenanceTargetRelease = target
		upgradeOptions := maintenanceOptionsFromApp(app)
		upgradeOptions.ForceDelete = false
		maintenanceSubmitAsync(app, mode, nodeName, nodeUid, upgradeOptions)
	}).Children(children...)
}
