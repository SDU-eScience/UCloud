package dashboard

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

const maintenanceDefaultTimeoutSeconds = 600

const maintenanceUiMessageSendTimeout = 10 * time.Second

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

	ucxsvc.RouterPushPage(app, fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName)))
}

func maintenanceOptionsFromApp(app *stackUiApp) maintenance.MaintenanceOptions {
	return maintenance.MaintenanceOptions{
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
	options maintenance.MaintenanceOptions,
) {
	if app.MaintenanceBusy {
		return
	}

	app.MaintenanceBusy = true

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
			err = maintenance.MaintenanceCordon(nodeName, nodeUid, options)
		case "start":
			err = maintenance.MaintenanceStart(nodeName, nodeUid, options)
		case "retry":
			err = maintenance.MaintenanceRetry(nodeName, nodeUid, options)
		case "uncordon":
			err = maintenance.MaintenanceUncordon(nodeName, nodeUid)
		case "cancel":
			err = maintenance.MaintenanceCancel(nodeName)
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
			} else {
				maintenanceSendUiMessage(session, maintenanceSubmitMessage(action, nodeName), true)
			}
			if err == nil && (action == "cordon" || action == "start" || action == "retry") {
				maintenanceReturnToNodes(session, app)
			}
		}
	}()
}

func maintenanceReturnToNodes(session *ucx.Session, app *stackUiApp) {
	if session.Context().Err() != nil {
		return
	}

	app.mu.Lock()
	app.maintenanceNodeName = ""
	app.maintenanceNodeUid = ""
	app.maintenanceRetryOptionsFor = ""
	app.mu.Unlock()

	_, _ = ucxapi.RouterPushPage.InvokeEx(session.Context(), session, ucxapi.RouterPushPageRequest{Path: "browse/nodes"})
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
	case "cordon":
		return fmt.Sprintf("Cordon of %s started", nodeName)
	case "start":
		return fmt.Sprintf("Cordon and drain of %s started", nodeName)
	case "retry":
		return fmt.Sprintf("Retry of the drain of %s started", nodeName)
	case "uncordon":
		return fmt.Sprintf("Uncordon of %s started", nodeName)
	case "cancel":
		return fmt.Sprintf("The operation on %s was asked to stop", nodeName)
	}
	return ""
}

func maintenancePage(app *stackUiApp) []ucx.UiNode {
	nodeName := maintenanceNodeFromRoute(app.RoutePath)

	surface := ucx.Surface().Sx(ucx.SxMaxWidth(800)).Children(
		ucx.Toolbar().Children(
			ucx.H2("Node maintenance"),
			ucx.Link("browse/nodes").Children(ucx.Text("Back to overview")),
		),
	)

	if nodeName == "" {
		return []ucx.UiNode{surface.Children(
			ucx.Text("Select a node in the node table to start maintenance."),
		)}
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
	}

	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return []ucx.UiNode{surface.Children(
			ucx.Text("Could not read the cluster record."),
		)}
	}

	known := false
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName {
			known = true
			break
		}
	}
	if !known {
		return []ucx.UiNode{surface.Children(
			ucx.Text(fmt.Sprintf("Unknown node: %s", nodeName)),
		)}
	}

	snapshot, err := maintenance.MaintenanceSnapshot()
	if err != nil {
		return []ucx.UiNode{surface.Children(
			ucx.Text(fmt.Sprintf("Could not read the maintenance state: %s", err)),
		)}
	}

	operation, hasOperation := snapshot[nodeName]

	targetUid := ""
	if app.maintenanceNodeName == nodeName && app.maintenanceNodeUid != "" {
		targetUid = app.maintenanceNodeUid
	} else if hasOperation {
		targetUid = operation.NodeUid
	}
	operationMatchesTarget := hasOperation && operation.NodeUid == targetUid

	mutationsBlocked := app.Stack == nil

	content := []ucx.UiNode{
		ucx.Text(fmt.Sprintf("Node: %s", nodeName)),
	}

	if hasOperation && operation.Error != "" {
		content = append(content, ucx.Text(fmt.Sprintf("Error: %s", operation.Error)).Sx(ucx.SxColor(ucx.ColorErrorMain)))
	}

	if mutationsBlocked {
		content = append(content, ucx.Text(
			"Maintenance actions are unavailable until the application is connected to the stack.",
		))
	}

	if hasOperation && maintenance.MaintenancePhaseActive(operation.Phase) && !mutationsBlocked {
		cancelLabel := "Cancel drain"
		if operation.Kind == maintenance.MaintenanceKindUncordon {
			cancelLabel = "Cancel uncordon"
		} else if operation.Kind == maintenance.MaintenanceKindCordon {
			cancelLabel = "Cancel cordon"
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
			maintenanceSubmitAsync(app, "cancel", nodeName, "", maintenance.MaintenanceOptions{})
		}))
	}

	retryable := operationMatchesTarget && operation.Kind != maintenance.MaintenanceKindUncordon &&
		operation.Kind != maintenance.MaintenanceKindCordon &&
		(operation.Phase == maintenance.MaintenancePhaseFailed ||
			operation.Phase == maintenance.MaintenancePhaseBlocked ||
			operation.Phase == maintenance.MaintenancePhaseCancelled)

	nodeCordoned := app.nodeIsCordoned(nodeName)

	if !hasOperation && targetUid == "" {
		content = append(content, ucx.Text(
			"Select the node in the live node table to start maintenance on it.",
		))
	}

	if !mutationsBlocked && targetUid != "" && !maintenance.MaintenancePhaseActive(operation.Phase) {
		if nodeCordoned {
			content = append(content, ucx.Text(
				"The node is cordoned. Use Uncordon in the node table to allow scheduling again.",
			))
		} else if retryable {
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

	return []ucx.UiNode{surface.Children(content...)}
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
