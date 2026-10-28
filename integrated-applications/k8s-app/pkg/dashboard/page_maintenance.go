package dashboard

import (
	"fmt"
	"net/url"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

const maintenanceDefaultTimeoutSeconds = 600

const maintenanceModeUpgrade = "upgrade"

const maintenanceModeDrain = "drain"

const maintenanceModeRemove = "remove"

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

// The page
// =====================================================================================================================
// The page reads the cluster record and the maintenance snapshot. It then shows the operation that is active on the
// node, or the form that starts a new one. The page never writes. A submit calls the maintenance package.

func maintenancePage(app *stackUiApp) []ucx.UiNode {
	nodeName := maintenanceNodeFromRoute(app.RoutePath)

	if nodeName == "" {
		return app.messagePage(nil, "browse/nodes", ucx.Text("Select a node in the node table to start maintenance."))
	}

	if app.maintenanceNodeName != nodeName {
		app.maintenanceNodeName = nodeName
		app.maintenanceNodeUid = ""
		app.maintenanceRetryOptionsFor = ""
		app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
		app.MaintenanceDrain = false
		app.MaintenanceCordon = false
		app.MaintenanceDeleteVolatilePods = false
		app.MaintenanceBypassDisruptionBudgets = false
		app.MaintenanceForceDelete = false
		app.MaintenanceTargetRelease = ""
		app.maintenanceMode = ""
	}

	bottom := []ucx.UiNode{shellBottomNode(app, "nodes", "Node maintenance", "nodes/"+nodeName)}

	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return app.messagePage(bottom, "browse/nodes", ucx.Text("Could not read the cluster record."))
	}

	known := false
	nodeRecord := shared.ClusterNodeRecord{}
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName {
			known = true
			nodeRecord = record.Nodes[i]
			break
		}
	}

	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return app.messagePage(bottom, "browse/nodes", ucx.Text(fmt.Sprintf("Could not read the maintenance state: %s", err)))
	}

	isRemoveRetryOp := false
	if op, present := snapshot[nodeName]; present && op.Kind == maintenance.KindRemove && !maintenance.PhaseActive(op.Phase) {
		isRemoveRetryOp = true
	}

	if !known && !isRemoveRetryOp {
		ucxsvc.RouterPushPage(app, "browse/nodes")
		return app.messagePage(bottom, "browse/nodes", ucx.Text(fmt.Sprintf("The node %s is no longer part of the cluster", nodeName)))
	}

	operation, hasOperation := snapshot[nodeName]

	targetUid := ""
	if app.maintenanceNodeName == nodeName && app.maintenanceNodeUid != "" {
		targetUid = app.maintenanceNodeUid
	} else if hasOperation {
		targetUid = operation.NodeUid
	}
	operationMatchesTarget := hasOperation && operation.NodeUid == targetUid
	isUpgrade := hasOperation && operation.Kind == maintenance.KindUpgrade
	isOsUpdate := hasOperation && operation.Kind == maintenance.KindOsUpdate
	isRemove := hasOperation && operation.Kind == maintenance.KindRemove
	recoveryBlocked := hasOperation && maintenance.KindRequiresRecovery(operation.Kind) &&
		operation.ExecutorSubmitted && operation.RecoveryRequired

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

	if hasOperation && maintenance.PhaseActive(operation.Phase) && !mutationsBlocked &&
		!(maintenance.KindRequiresRecovery(operation.Kind) && operation.ExecutorSubmitted) &&
		!maintenance.RemovePastPointOfNoReturn(operation) {
		cancelLabel := "Cancel drain"
		if operation.Kind == maintenance.KindUncordon {
			cancelLabel = "Cancel uncordon"
		} else if operation.Kind == maintenance.KindCordon {
			cancelLabel = "Cancel cordon"
		} else if isUpgrade {
			cancelLabel = "Cancel upgrade"
		} else if isOsUpdate {
			cancelLabel = "Cancel update"
		} else if isRemove {
			cancelLabel = "Cancel removal"
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
			maintenanceSubmitAsync(app, maintenanceSubmit{kind: "cancel"}, nodeName, "", maintenance.Options{})
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
	if isOsUpdate && operation.ExecutorSubmitted && maintenance.PhaseActive(operation.Phase) {
		content = append(content, ucx.Text("The operating-system update is running on the node and cannot be cancelled."))
	}
	if isRemove && maintenance.PhaseActive(operation.Phase) && maintenance.RemovePastPointOfNoReturn(operation) {
		content = append(content, ucx.Text(
			"The removal passed the point of no return and cannot be cancelled. It continues until the node is gone.",
		))
	}
	if recoveryBlocked && !operationMatchesTarget {
		if isOsUpdate {
			content = append(content, ucx.Text(
				"The earlier node requires operating-system update recovery. Retry it from the automatic maintenance page, or uncordon the node in the node table.",
			))
		} else {
			content = append(content, ucx.Text(
				"The earlier node requires upgrade recovery. Retry the upgrade or uncordon it in the node table.",
			))
		}
	}
	if recoveryBlocked && operationMatchesTarget && !maintenance.PhaseActive(operation.Phase) {
		if isOsUpdate {
			content = append(content, ucx.Text(
				"The operating-system update failed and the node remains cordoned. Retry it from the automatic maintenance page, or use Uncordon in the node table to return the node to service.",
			))
		} else if maintenance.UpgradeCordons(operation) {
			content = append(content, ucx.Text(
				"The upgrade failed and the node remains cordoned. Retry the upgrade, or use Uncordon in the node table to return the node to service on its current release.",
			))
		} else {
			content = append(content, ucx.Text(
				"The upgrade failed. Retry the upgrade to recover the node.",
			))
		}
	}
	upgradePage := app.maintenanceMode == maintenanceModeUpgrade || (app.maintenanceMode == "" && isUpgrade)
	if upgradePage {
		content = append(content, ucx.LinkButton("maintenanceRollingUpgrade", "Want to upgrade the entire cluster in a rolling fashion?", ucx.ColorLinkColor).
			Sx(ucx.SxFontSize(14), ucx.SxFontWeight("400")).
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.RouterPushPage(app, "rolling-upgrade")
			}))
	}
	removePage := app.maintenanceMode == maintenanceModeRemove || (app.maintenanceMode == "" && isRemove)
	if upgradePage && !mutationsBlocked && targetUid != "" && !maintenance.PhaseActive(operation.Phase) {
		if retryable && isUpgrade {
			if maintenanceOptionsRestore(app, nodeName, operation) {
				app.MaintenanceTargetRelease = operation.TargetRelease
			}
			content = append(content, maintenanceUpgradeForm(
				app,
				maintenanceSubmit{kind: "upgrade", retry: true},
				nodeName,
				targetUid,
				operation,
				record,
			))
		} else if !recoveryBlocked {
			content = append(content, maintenanceUpgradeForm(
				app,
				maintenanceSubmit{kind: "upgrade"},
				nodeName,
				targetUid,
				operation,
				record,
			))
		}
	}

	if removePage && !mutationsBlocked && !maintenance.PhaseActive(operation.Phase) {
		if retryable && isRemove {
			maintenanceOptionsRestore(app, nodeName, operation)
			content = append(content, maintenanceRemoveForm(
				app,
				maintenanceSubmit{kind: "remove", retry: true},
				nodeName,
				targetUid,
				operation,
				nodeRecord,
				record,
			))
		} else if !recoveryBlocked || isRemove {
			content = append(content, maintenanceRemoveForm(
				app,
				maintenanceSubmit{kind: "remove"},
				nodeName,
				targetUid,
				maintenance.Operation{},
				nodeRecord,
				record,
			))
		}
	}

	if !upgradePage && !removePage && !recoveryBlocked && !mutationsBlocked && targetUid != "" && !maintenance.PhaseActive(operation.Phase) {
		if isOsUpdate {
			content = append(content, ucx.Text(
				"Operating-system updates are scheduled from the automatic maintenance page.",
			))
		} else if nodeCordoned {
			content = append(content, ucx.Text(
				"The node is cordoned. Use Uncordon in the node table to allow scheduling again.",
			))
		} else if retryable && !isUpgrade {
			maintenanceOptionsRestore(app, nodeName, operation)
			content = append(content, maintenanceDrainForm(
				app,
				maintenanceSubmit{kind: "start", retry: true},
				nodeName,
				targetUid,
			))
		} else {
			if app.MaintenanceTimeoutSeconds <= 0 {
				app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
			}
			content = append(content, maintenanceDrainForm(
				app,
				maintenanceSubmit{kind: "start"},
				nodeName,
				targetUid,
			))
		}
	}

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:    []ucx.UiNode{shellContentBox(800, content...)},
		Bottom:     bottom,
		EscapePath: "browse/nodes",
	})}
}

func maintenanceOpen(app *stackUiApp, nodeName string, nodeUid string) {
	maintenanceOpenWithMode(app, nodeName, nodeUid, maintenanceModeDrain)
}

func maintenanceOpenWithMode(app *stackUiApp, nodeName string, nodeUid string, mode string) {
	nodeName = strings.TrimSpace(nodeName)
	nodeUid = strings.TrimSpace(nodeUid)
	if nodeName == "" || (nodeUid == "" && mode != maintenanceModeRemove) {
		return
	}

	app.maintenanceNodeName = nodeName
	app.maintenanceNodeUid = nodeUid
	app.maintenanceRetryOptionsFor = ""
	app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
	app.MaintenanceDrain = mode == maintenanceModeRemove
	app.MaintenanceCordon = mode == maintenanceModeRemove
	app.MaintenanceDeleteVolatilePods = false
	app.MaintenanceBypassDisruptionBudgets = false
	app.MaintenanceForceDelete = false
	app.MaintenanceTargetRelease = ""
	app.maintenanceMode = mode

	ucxsvc.RouterPushPage(app, fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName)))
}

// The forms
// =====================================================================================================================
// Each operation has one form. A form is a plain UI node. The form validates the unsafe options, then it hands the
// options to the maintenance package. A retry form starts with the options of the failed operation.

func maintenanceDrainOptionsNode(
	idPrefix string,
	timeoutLabel string,
	description string,
	includeForceDelete bool,
) ucx.UiNode {
	children := []ucx.UiNode{
		ucx.FieldRowNodeEx(idPrefix+"TimeoutRow", timeoutLabel, "maintenanceTimeoutSeconds").
			FieldRowDescription(description).
			FieldRowRequired(true).
			Children(ucx.InputNumber(
				idPrefix+"Timeout",
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
	}
	if includeForceDelete {
		children = append(children, ucx.Checkbox(
			"maintenanceForceDelete",
			"**Force delete:** skip graceful shutdown with a zero grace period. Requires bypassing budgets.",
			"maintenanceForceDelete",
			false,
		))
	}
	return ucx.FieldGroupNode().Children(children...)
}

func maintenanceDrainForm(app *stackUiApp, submit maintenanceSubmit, nodeName string, nodeUid string) ucx.UiNode {
	drainEnabled := app.MaintenanceDrain || submit.retry

	submitId := "maintenanceDrainSubmit"
	submitLabel := "Cordon and drain"
	submitColor := ucx.ColorErrorMain
	holdToConfirm := true
	if !submit.retry && !drainEnabled {
		submitLabel = "Cordon node"
		submitColor = ucx.ColorWarningMain
		holdToConfirm = false
	}

	children := []ucx.UiNode{}
	if !submit.retry {
		children = append(children, ucx.Checkbox(
			"maintenanceDrain",
			"**Drain:** evict all pods from the node after it is cordoned. Replacements may be unavailable immediately.",
			"maintenanceDrain",
			false,
		))
	}
	if drainEnabled {
		children = append(children, maintenanceDrainOptionsNode(
			"maintenance",
			"Timeout (seconds)",
			"The drain gives up after this many seconds.",
			true,
		))
	}

	submitButton := ucx.SubmitButton(submitId, submitLabel, submitColor).
		ButtonBusy("maintenanceBusy").
		Sx(ucx.SxJustifyEnd)
	if holdToConfirm {
		submitButton = submitButton.ButtonHoldToConfirm(true)
	}
	children = append(children, submitButton)

	return ucx.Form("maintenanceDrainForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.MaintenanceBusy || app.Stack == nil {
			return
		}

		options := maintenanceOptionsFromApp(app)
		if options.ForceDelete && !options.BypassDisruptionBudgets {
			ucxsvc.UiSendFailure(app, "A zero grace period requires bypassing the disruption budgets")
			return
		}

		action := submit
		if !submit.retry && !app.MaintenanceDrain {
			action.kind = "cordon"
		}

		maintenanceSubmitAsync(app, action, nodeName, nodeUid, options)
	}).Children(children...)
}

func maintenanceRemoveForm(
	app *stackUiApp,
	submit maintenanceSubmit,
	nodeName string,
	nodeUid string,
	operation maintenance.Operation,
	nodeRecord shared.ClusterNodeRecord,
	record shared.ClusterRecord,
) ucx.UiNode {
	isRetry := submit.retry

	children := []ucx.UiNode{}

	controlPlanes := len(shared.NodesInGroup(record, shared.GroupControlPlane))

	if nodeRecord.Group == shared.GroupControlPlane {
		if controlPlanes <= 2 {
			return ucx.Warning(
				"The control plane must keep at least two nodes for the etcd quorum, so this node cannot be removed. Add another control-plane node first.",
			)
		}
		if controlPlanes == 3 {
			children = append(children, ucx.Warning(
				"The control plane goes from three to two nodes. The etcd quorum survives the removal, but a single later failure loses the quorum.",
			))
		} else {
			children = append(children, ucx.Text(
				"This is a control-plane node. Its etcd member is removed when the node is removed.",
			))
		}
	} else {
		workers := len(record.Nodes) - controlPlanes
		if workers == 1 {
			children = append(children, ucx.Warning(
				"This is the last worker node. The cluster keeps its control plane, but no workloads can run until a worker is added again.",
			))
		}
	}

	children = append(children, ucx.Text(
		"The node is removed from the cluster and its machine is deleted. This cannot be undone.",
	).Sx(ucx.SxColor(ucx.ColorErrorMain)))

	if isRetry {
		if operation.NodeDeleted {
			children = append(children, ucx.Text(
				"The Kubernetes node was already deleted. The retry continues with the machine removal.",
			))
		}
		children = append(children, ucx.Text(fmt.Sprintf("Retry target: %s", nodeName)))
	}

	children = append(children,
		ucx.Checkbox(
			"maintenanceRemoveDrain",
			"**Drain:** evict all pods from the node before it is removed. Uncheck this only if the node is already unreachable.",
			"maintenanceDrain",
			false,
		),
	)

	if app.MaintenanceDrain {
		children = append(children, maintenanceDrainOptionsNode(
			"maintenanceRemove",
			"Drain timeout (seconds)",
			"The drain gives up after this many seconds.",
			true,
		))
	}

	submitLabel := "Remove node"
	if isRetry {
		submitLabel = "Retry removal"
	}

	children = append(children,
		ucx.SubmitButton("maintenanceRemoveSubmit", submitLabel, ucx.ColorErrorMain).
			ButtonBusy("maintenanceBusy").
			ButtonHoldToConfirm(true).
			Sx(ucx.SxJustifyEnd),
	)

	return ucx.Form("maintenanceRemoveForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.MaintenanceBusy || app.Stack == nil {
			return
		}

		options := maintenanceOptionsFromApp(app)
		if options.ForceDelete && !options.BypassDisruptionBudgets {
			ucxsvc.UiSendFailure(app, "A zero grace period requires bypassing the disruption budgets")
			return
		}
		options.Cordon = options.Drain

		maintenanceSubmitAsync(app, submit, nodeName, nodeUid, options)
	}).Children(children...)
}

func maintenanceUpgradeForm(
	app *stackUiApp,
	submit maintenanceSubmit,
	nodeName string,
	nodeUid string,
	operation maintenance.Operation,
	record shared.ClusterRecord,
) ucx.UiNode {
	isRetry := submit.retry
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
			"No upgrade path is allowed for the node running %s. It may already run the newest supported release, or the jump may be more than one minor. A jump of more than one minor needs a manual multi-step upgrade or node replacement.",
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
	controlPlaneNodes := shared.NodesInGroup(record, shared.GroupControlPlane)
	controlPlanes := len(controlPlaneNodes)
	targetIsControlPlane := false
	for _, node := range controlPlaneNodes {
		if node.Hostname == nodeName {
			targetIsControlPlane = true
			break
		}
	}
	if targetIsControlPlane && controlPlanes == 1 {
		children = append(children, ucx.Warning("This is the only control-plane node. Upgrading it causes control-plane downtime."))
	}
	if isRetry {
		children = append(children, ucx.Text(fmt.Sprintf("Retry target: %s", operation.TargetRelease)))
	} else {
		children = append(children, ucx.Text(fmt.Sprintf("Current version: %s", shared.VersionSupportLabel(currentVersion, time.Now().UTC()))))
		children = append(children, ucx.FieldRowNodeEx("maintenanceTargetReleaseRow", "Target release", "maintenanceTargetRelease").
			FieldRowRequired(true).
			Children(ucx.Select("maintenanceTargetReleaseSelect", "", "maintenanceTargetRelease", options)))
	}
	children = append(children,
		ucx.Tip("Neither cordon nor drain is usually needed for a Kubernetes upgrade. Leave both unchecked unless your workloads require it. Nodes cordoned by this upgrade are automatically uncordoned after success. Nodes already cordoned stay cordoned."),
		ucx.Checkbox(
			"maintenanceUpgradeCordon",
			"**Cordon:** mark the node as unschedulable before the upgrade. No new pods are placed on it while it upgrades.",
			"maintenanceCordon",
			false,
		),
		ucx.Checkbox(
			"maintenanceUpgradeDrain",
			"**Drain:** evict all pods from the node after it is cordoned. Replacements may be unavailable immediately.",
			"maintenanceDrain",
			false,
		),
	)
	if app.MaintenanceDrain {
		children = append(children, maintenanceDrainOptionsNode(
			"maintenanceUpgrade",
			"Drain timeout (seconds)",
			"The drain gives up after this many seconds. Upgrading and verifying the node have a separate time limit.",
			false,
		))
	}
	children = append(children,
		ucx.SubmitButton("maintenanceUpgradeSubmit", submitLabel, ucx.ColorSuccessMain).
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
		if upgradeOptions.Drain {
			upgradeOptions.Cordon = true
		}
		maintenanceSubmitAsync(app, submit, nodeName, nodeUid, upgradeOptions)
	}).Children(children...)
}

func (app *stackUiApp) nodeIsCordoned(nodeName string) bool {
	if app.poller == nil {
		return false
	}
	row, ok := app.poller.nodeRowByName(nodeName)
	return ok && len(row.Cells) > 2 && row.Cells[2] == "Cordoned"
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
	now := time.Now().UTC()
	options := []ucx.Option{}
	for _, release := range shared.StableReleases(now) {
		if !shared.NodeAgentUpgradeAllowed(currentVersion, release.Release) {
			continue
		}
		options = append(options, ucx.Option{Key: release.Release, Value: shared.ReleaseSupportLabel(release, now)})
	}
	return options
}

// Submission
// =====================================================================================================================
// A submit runs away from the render path. The busy flag stops a second click. The result becomes a UI message.

func maintenanceOptionsFromApp(app *stackUiApp) maintenance.Options {
	return maintenance.Options{
		TimeoutSeconds:          app.MaintenanceTimeoutSeconds,
		Cordon:                  app.MaintenanceCordon,
		Drain:                   app.MaintenanceDrain,
		DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
		BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
		ForceDelete:             app.MaintenanceForceDelete,
	}
}

func maintenanceOptionsRestore(app *stackUiApp, nodeName string, operation maintenance.Operation) bool {
	if app.maintenanceRetryOptionsFor == nodeName {
		return false
	}
	app.maintenanceRetryOptionsFor = nodeName
	app.MaintenanceTimeoutSeconds = operation.Options.TimeoutSeconds
	if app.MaintenanceTimeoutSeconds <= 0 {
		app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
	}
	app.MaintenanceCordon = operation.Options.Cordon
	app.MaintenanceDrain = operation.Options.Drain
	app.MaintenanceDeleteVolatilePods = operation.Options.DeleteVolatilePods
	app.MaintenanceBypassDisruptionBudgets = operation.Options.BypassDisruptionBudgets
	app.MaintenanceForceDelete = operation.Options.ForceDelete
	return true
}

type maintenanceSubmit struct {
	kind  string
	retry bool
}

func (submit maintenanceSubmit) run(
	nodeName string,
	nodeUid string,
	targetRelease string,
	options maintenance.Options,
) error {
	switch submit.kind {
	case "cordon":
		return maintenance.SubmitCordon(nodeName, nodeUid, options)
	case "start":
		return maintenance.SubmitCordonDrain(nodeName, nodeUid, options)
	case "upgrade":
		return maintenance.SubmitUpgrade(nodeName, nodeUid, targetRelease, options)
	case "remove":
		return maintenance.SubmitRemove(nodeName, nodeUid, options)
	case "uncordon":
		return maintenance.SubmitUncordon(nodeName, nodeUid)
	case "cancel":
		return maintenance.CancelActiveOperation(nodeName)
	}
	return nil
}

func (submit maintenanceSubmit) startsOperation() bool {
	switch submit.kind {
	case "cordon", "start", "upgrade", "remove":
		return true
	default:
		return false
	}
}

func maintenanceSubmitAsync(
	app *stackUiApp,
	submit maintenanceSubmit,
	nodeName string,
	nodeUid string,
	options maintenance.Options,
) {
	if app.MaintenanceBusy {
		return
	}

	targetRelease := app.MaintenanceTargetRelease

	app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
		return submit.run(nodeName, nodeUid, targetRelease, options)
	}, func(err error) {
		switch {
		case err != nil:
			dashboardSendUiMessage(*app.Session(), err.Error(), false)
		case !submit.startsOperation():
			dashboardSendUiMessage(*app.Session(), maintenanceSubmitMessage(submit.kind, nodeName), true)
		}
	})
}

func maintenanceSubmitMessage(kind string, nodeName string) string {
	switch kind {
	case "uncordon":
		return fmt.Sprintf("Uncordon of %s started", nodeName)
	case "cancel":
		return fmt.Sprintf("The operation on %s was asked to stop", nodeName)
	}
	return ""
}
