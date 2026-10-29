package dashboard

import (
	"fmt"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

const maintenanceDefaultTimeoutSeconds = 600

const (
	maintenanceActionUpdate   = "update"
	maintenanceActionK8s      = maintenance.TaskK8sUpgrade
	maintenanceActionOs       = maintenance.TaskOsUpdate
	maintenanceActionCordon   = maintenance.TaskCordon
	maintenanceActionUncordon = maintenance.TaskUncordon
	maintenanceActionRemove   = maintenance.TaskRemove

	maintenanceModeUpgrade = "upgrade"
	maintenanceModeDrain   = "drain"
	maintenanceModeRemove  = "remove"
)

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

func maintenancePage(app *stackUiApp) []ucx.UiNode {
	nodeName := maintenanceNodeFromRoute(app.RoutePath)
	if nodeName == "" {
		return app.maintenanceHubPage()
	}
	return app.maintenanceNodePage(nodeName)
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
	switch mode {
	case maintenanceModeRemove:
		app.MaintenanceTask = maintenanceActionRemove
	case maintenanceModeUpgrade:
		app.MaintenanceTask = maintenanceActionK8s
	default:
		app.MaintenanceTask = maintenanceActionCordon
	}
	app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
	app.MaintenanceDrain = mode != maintenanceModeUpgrade
	app.MaintenanceCordon = mode != maintenanceModeUpgrade
	app.MaintenanceDeleteVolatilePods = false
	app.MaintenanceBypassDisruptionBudgets = false
	app.MaintenanceForceDelete = false
	app.MaintenanceTargetRelease = ""

	ucxsvc.RouterPushPage(app, fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName)))
}

// Hub
// =====================================================================================================================

func (app *stackUiApp) maintenanceHubPage() []ucx.UiNode {
	bottom := []ucx.UiNode{shellBottomNode(app, "nodes", "Maintenance")}

	status, err := maintenance.ReadStatus()
	if err != nil {
		return app.messagePage(bottom, "browse/nodes", ucx.Text(fmt.Sprintf(
			"Could not read the maintenance state: %s",
			err,
		)))
	}

	if !app.maintenanceHubLoaded && !app.maintenancePolicySaving && app.maintenancePolicySaveError == "" {
		app.maintenanceHubLoaded = true
		values := maintenancePolicyValuesFromPolicy(status.Policy)
		app.MaintenanceK8sEnabled = values.K8sEnabled
		app.MaintenanceOsEnabled = values.OsEnabled
		app.MaintenanceWindowDay = values.WindowDay
		app.MaintenanceWindowStart = values.StartHour
	}

	content := []ucx.UiNode{
		ucx.H3Ex("maintenanceHeading", "Maintenance"),
	}
	top := app.maintenanceStatusSummary(status)
	if len(status.ActiveRuns) == 0 {
		if app.maintenanceRunNowConfirm {
			top = append(top, maintenanceRunNowDialog(app, status.Policy))
		}
		top = append(top, app.maintenancePolicyForm())
	} else {
		app.maintenanceRunNowConfirm = false
	}
	content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(top...))
	content = append(content, app.maintenanceRunsSection(status.ActiveRuns)...)

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:    []ucx.UiNode{shellContentBox(900, content...)},
		Bottom:     bottom,
		EscapePath: "browse/nodes",
	})}
}

func (app *stackUiApp) maintenanceStatusSummary(status maintenance.RunStatus) []ucx.UiNode {
	if len(status.ActiveRuns) > 0 {
		run := status.ActiveRuns[0]
		text := "A maintenance run is in progress."
		if run.CancelRequested {
			text = "The maintenance run is being cancelled."
		} else {
			switch run.Phase {
			case maintenance.RunBlocked:
				text = "The maintenance run is blocked."
			case maintenance.RunFailed:
				text = "The maintenance run failed."
			}
		}
		text += maintenanceRunTimingText(run, time.Now().UTC())

		box := ucx.Tip(text)
		if !run.CancelRequested && (run.Phase == maintenance.RunBlocked || run.Phase == maintenance.RunFailed) {
			box = ucx.Warning(text)
		}
		box = box.WarningCentered(true)
		if !run.CancelRequested {
			box = box.Children(
				ucx.ButtonEx(
					"maintenanceCancel-"+run.Uid,
					"Cancel run",
					ucx.ColorErrorMain,
					"",
					"",
					"",
				).ButtonBusy("maintenanceBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					maintenanceCancelAsync(app, run.Uid)
				}),
			)
		}
		return []ucx.UiNode{box.Sx(ucx.SxMb(0))}
	}

	text, isTip := maintenanceScheduleInfoText(status.Policy, time.Now().UTC())
	if status.LastRun != nil && !maintenance.RunActive(*status.LastRun) {
		text += " " + maintenanceLastRunLabel(*status.LastRun)
	}
	box := ucx.Warning(text)
	if isTip {
		box = ucx.Tip(text)
	}
	if status.Policy.Kubernetes.Enabled || status.Policy.Os.Enabled {
		box = box.WarningCentered(true).Children(
			ucx.Button("maintenanceRunNowSubmit", "Run now", ucx.ColorPrimaryMain).
				ButtonBusy("maintenanceBusy").
				On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					if app.MaintenanceBusy || app.Stack == nil {
						return
					}
					app.maintenanceRunNowConfirm = true
					ucx.AppUpdateUi(app)
				}),
		)
	}

	return []ucx.UiNode{box.Sx(ucx.SxMb(0))}
}

func maintenanceLastRunLabel(run maintenance.MaintenanceRun) string {
	return "Last run at " + maintenanceTimestamp(run.RequestedAt) + "."
}

func maintenanceScheduleInfoText(policy maintenance.Policy, now time.Time) (string, bool) {
	enabled := policy.Kubernetes.Enabled || policy.Os.Enabled
	if !enabled {
		return "Automatic maintenance is off.", false
	}

	if _, end, open := maintenance.WindowOpen(now, policy.Window); open {
		return "The maintenance window is open until " + maintenanceWindowEndLabel(end) + ".", true
	}

	start, end, ok := maintenance.NextWindow(now, policy.Window)
	if !ok {
		return "The weekly schedule is not valid.", false
	}
	return maintenanceNextRunLabel(start, end, now), true
}

func maintenanceNextRunLabel(start time.Time, end time.Time, now time.Time) string {
	endLabel := end.UTC().Format("15:04")
	if start.UTC().Format("2006-01-02") != end.UTC().Format("2006-01-02") {
		endLabel = end.UTC().Format("Monday 15:04")
	}
	return fmt.Sprintf(
		"Next run: %s - %s UTC (in %s).",
		start.UTC().Format("Monday 15:04"),
		endLabel,
		maintenanceUntilLabel(start, now),
	)
}

func maintenanceWindowEndLabel(end time.Time) string {
	return end.UTC().Format("Monday 15:04") + " UTC"
}

func maintenanceUntilLabel(target time.Time, now time.Time) string {
	delta := target.Sub(now)
	if delta <= 0 {
		return "now"
	}

	days := int(delta / (24 * time.Hour))
	hours := int((delta % (24 * time.Hour)) / time.Hour)
	minutes := int((delta % time.Hour) / time.Minute)

	switch {
	case days > 0 && hours > 0:
		return fmt.Sprintf("%d d %d h", days, hours)
	case days > 0:
		return fmt.Sprintf("%d d", days)
	case hours > 0 && minutes > 0:
		return fmt.Sprintf("%d h %d min", hours, minutes)
	case hours > 0:
		return fmt.Sprintf("%d h", hours)
	default:
		return fmt.Sprintf("%d min", minutes)
	}
}

func maintenanceRunNowDialog(app *stackUiApp, policy maintenance.Policy) ucx.UiNode {
	message := "Starts the enabled updates. Nodes update one at a time."
	switch {
	case policy.Kubernetes.Enabled && policy.Os.Enabled:
		message = "Kubernetes updates first, then the operating system. Nodes update one at a time."
	case policy.Kubernetes.Enabled:
		message = "The Kubernetes upgrade starts now. Nodes update one at a time."
	case policy.Os.Enabled:
		message = "The operating-system update starts now. Nodes update one at a time."
	}

	return dashboardResourceActionDialog(
		"maintenanceRunNow",
		"Run maintenance now",
		message,
		"",
		ucx.ColorPrimaryMain,
		func() {
			app.maintenanceRunNowConfirm = false
			ucx.AppUpdateUi(app)
		},
		func() {
			app.maintenanceRunNowConfirm = false
			ucx.AppUpdateUi(app)
			if app.MaintenanceBusy || app.Stack == nil {
				return
			}
			app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
				_, err := maintenance.SubmitConfiguredRun("dashboard")
				return err
			}, app.maintenanceReportError)
		},
	)
}

type maintenancePolicyValues struct {
	K8sEnabled bool
	OsEnabled  bool
	WindowDay  string
	StartHour  string
}

func maintenancePolicyValuesFromPolicy(policy maintenance.Policy) maintenancePolicyValues {
	return maintenancePolicyValues{
		K8sEnabled: policy.Kubernetes.Enabled,
		OsEnabled:  policy.Os.Enabled,
		WindowDay:  policy.Window.Day,
		StartHour:  strconv.Itoa(policy.Window.StartHourUtc),
	}
}

func maintenancePolicyValuesFromModel(app *stackUiApp) maintenancePolicyValues {
	return maintenancePolicyValues{
		K8sEnabled: app.MaintenanceK8sEnabled,
		OsEnabled:  app.MaintenanceOsEnabled,
		WindowDay:  app.MaintenanceWindowDay,
		StartHour:  app.MaintenanceWindowStart,
	}
}

func maintenancePolicySaveAsync(app *stackUiApp) {
	session := *app.Session()
	if session == nil {
		return
	}

	values := maintenancePolicyValuesFromModel(app)
	app.maintenancePolicyPending = &values
	app.maintenancePolicySaveError = ""
	if app.maintenancePolicySaving {
		return
	}

	app.maintenancePolicySaving = true
	go func() {
		for {
			app.mu.Lock()
			pending := *app.maintenancePolicyPending
			app.maintenancePolicyPending = nil
			app.mu.Unlock()

			err := maintenancePolicySave(pending)

			app.mu.Lock()
			done := app.maintenancePolicyPending == nil
			if done {
				app.maintenancePolicySaving = false
				if err != nil {
					app.maintenancePolicySaveError = err.Error()
				}
			}
			if session.Context().Err() == nil {
				ucx.AppUpdateUi(app)
			}
			app.mu.Unlock()
			if done {
				return
			}
		}
	}()
}

func maintenancePolicySave(values maintenancePolicyValues) error {
	startHour, err := strconv.Atoi(values.StartHour)
	if err != nil {
		return fmt.Errorf("select a maintenance window period")
	}
	return maintenance.ApplyPolicyUpdate(maintenance.PolicyPatch{
		K8sEnabled:      util.OptValue(values.K8sEnabled),
		OsEnabled:       util.OptValue(values.OsEnabled),
		WindowDay:       values.WindowDay,
		WindowStartHour: util.OptValue(startHour),
	})
}

func (app *stackUiApp) maintenancePolicyForm() ucx.UiNode {
	dayOptions := []ucx.Option{
		{Key: "mon", Value: "Monday"},
		{Key: "tue", Value: "Tuesday"},
		{Key: "wed", Value: "Wednesday"},
		{Key: "thu", Value: "Thursday"},
		{Key: "fri", Value: "Friday"},
		{Key: "sat", Value: "Saturday"},
		{Key: "sun", Value: "Sunday"},
	}

	children := []ucx.UiNode{
		ucx.Checkbox(
			"maintenanceK8sEnabled",
			"**Kubernetes upgrades:** include a Kubernetes upgrade in the scheduled run.",
			"maintenanceK8sEnabled",
			false,
		).Sx(ucx.SxMt(8), ucx.SxMb(0)),
		ucx.Checkbox(
			"maintenanceOsEnabled",
			"**Operating-system updates:** include an operating-system update in the scheduled run.",
			"maintenanceOsEnabled",
			false,
		).Sx(ucx.SxMt(0), ucx.SxMb(8)),
		ucx.FieldRowNodeEx("maintenanceWindowDayRow", "Day", "maintenanceWindowDay").
			FieldRowDescription("The day when weekly maintenance can start.").
			Children(ucx.Select("maintenanceWindowDaySelect", "", "maintenanceWindowDay", dayOptions)),
		ucx.FieldRowNodeEx("maintenanceWindowPeriodRow", "Period (UTC)", "maintenanceWindowStart").
			FieldRowDescription("The four-hour period when maintenance can start.").
			Children(ucx.Select(
				"maintenanceWindowStartSelect",
				"",
				"maintenanceWindowStart",
				maintenanceWindowPeriodOptions(),
			)),
	}
	if app.maintenancePolicySaveError != "" {
		children = append(children,
			ucx.Warning("Could not save the maintenance policy: "+app.maintenancePolicySaveError).Sx(ucx.SxMb(0)),
			ucx.Button("maintenancePolicyRetrySave", "Retry", ucx.ColorSecondaryMain).
				On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					maintenancePolicySaveAsync(app)
					ucx.AppUpdateUi(app)
				}),
		)
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(children...)
}

func maintenanceWindowPeriodOptions() []ucx.Option {
	options := make([]ucx.Option, 0, 24)
	for hour := 0; hour < 24; hour++ {
		endHour := (hour + maintenance.WindowDurationHours) % 24
		label := fmt.Sprintf("%02d:00 - %02d:00 UTC", hour, endHour)
		if endHour <= hour {
			label += " (next day)"
		}
		options = append(options, ucx.Option{
			Key:   strconv.Itoa(hour),
			Value: label,
		})
	}
	return options
}

func (app *stackUiApp) maintenanceRunForm() ucx.UiNode {
	actionOptions := []ucx.Option{
		{Key: maintenanceActionUpdate, Value: "Kubernetes and operating-system update"},
		{Key: maintenanceActionK8s, Value: "Kubernetes upgrade"},
		{Key: maintenanceActionOs, Value: "Operating-system update"},
		{Key: maintenanceActionCordon, Value: "Cordon or drain"},
		{Key: maintenanceActionUncordon, Value: "Uncordon"},
		{Key: maintenanceActionRemove, Value: "Remove node"},
	}
	if app.MaintenanceTask == "" {
		app.MaintenanceTask = maintenanceActionUpdate
	}

	children := []ucx.UiNode{
		ucx.FieldRowNodeEx("maintenanceTaskRow", "Task", "maintenanceTask").
			FieldRowRequired(true).
			FieldRowDescription("Choose the maintenance task to run on this node.").
			Children(ucx.Select("maintenanceTaskSelect", "", "maintenanceTask", actionOptions)),
	}

	if app.MaintenanceTask == maintenanceActionK8s || app.MaintenanceTask == maintenanceActionUpdate {
		children = append(children, app.maintenanceUpgradeOptions()...)
	}
	if app.MaintenanceTask == maintenanceActionCordon {
		children = append(children,
			ucx.Checkbox(
				"maintenanceCordon",
				"**Cordon:** mark the node unschedulable before the task.",
				"maintenanceCordon",
				false,
			).Sx(ucx.SxMy(0)),
			ucx.Checkbox(
				"maintenanceDrain",
				"**Drain:** evict pods from the node after cordoning it.",
				"maintenanceDrain",
				false,
			).Sx(ucx.SxMy(0)),
		)
	}
	children = append(children, ucx.FieldRowNodeEx("maintenanceTimeoutRow", "Timeout (seconds)", "maintenanceTimeoutSeconds").
		FieldRowDescription("The maintenance task gives up after this many seconds.").
		FieldRowRequired(true).
		Children(ucx.InputNumber("maintenanceTimeoutInput", "", "maintenanceTimeoutSeconds", 30, 3600)))

	if app.MaintenanceDrain || app.MaintenanceTask == maintenanceActionUpdate ||
		app.MaintenanceTask == maintenanceActionOs || app.MaintenanceTask == maintenanceActionRemove {
		children = append(children, maintenanceDrainOptionsNode(
			app,
			app.MaintenanceTask == maintenanceActionRemove || app.MaintenanceTask == maintenanceActionCordon,
		))
	}
	children = append(children, ucx.SubmitButton("maintenanceRunSubmit", "Start maintenance run", ucx.ColorPrimaryMain).
		ButtonBusy("maintenanceBusy").
		Sx(ucx.SxJustifyEnd),
	)

	return ucx.Form("maintenanceNodeRunForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.MaintenanceBusy || app.Stack == nil {
			return
		}
		app.maintenanceNodeRunConfirm = true
		ucx.AppUpdateUi(app)
	}).Children(
		ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(children...),
	)
}

func (app *stackUiApp) maintenanceUpgradeOptions() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return []ucx.UiNode{ucx.Warning("Could not read the cluster record.")}
	}

	currentVersion := ""
	if app.maintenanceNodeName != "" {
		currentVersion = app.maintenanceNodeVersion(app.maintenanceNodeName, record)
	}
	options := []ucx.Option{{
		Key:   "",
		Value: "Automatically choose the next supported release",
	}}
	options = append(options, maintenanceUpgradeReleaseOptions(currentVersion)...)

	selected := false
	for _, option := range options {
		if option.Key == app.MaintenanceTargetRelease {
			selected = true
			break
		}
	}
	if !selected {
		app.MaintenanceTargetRelease = ""
	}

	return []ucx.UiNode{
		ucx.FieldRowNodeEx("maintenanceTargetReleaseRow", "Target Kubernetes version", "maintenanceTargetRelease").
			FieldRowDescription("Automatic selection follows the supported upgrade path for every targeted node.").
			Children(ucx.Select("maintenanceTargetReleaseSelect", "", "maintenanceTargetRelease", options)),
	}
}

func maintenanceDrainOptionsNode(app *stackUiApp, includeForceDelete bool) ucx.UiNode {
	children := []ucx.UiNode{
		ucx.Checkbox(
			"maintenanceDeleteVolatilePods",
			"**Delete volatile pods:** also evict pods with emptyDir data and pods without a controller. Their data is lost.",
			"maintenanceDeleteVolatilePods",
			false,
		).Sx(ucx.SxMy(0)),
	}
	if app.MaintenanceTask != maintenanceActionOs {
		children = append(children, ucx.Checkbox(
			"maintenanceBypassDisruptionBudgets",
			"**Bypass budgets:** evict pods even when a `PodDisruptionBudget` forbids it.",
			"maintenanceBypassDisruptionBudgets",
			false,
		).Sx(ucx.SxMy(0)))
	}
	if includeForceDelete {
		children = append(children, ucx.Checkbox(
			"maintenanceForceDelete",
			"**Force delete:** skip graceful shutdown with a zero grace period. Requires bypassing budgets.",
			"maintenanceForceDelete",
			false,
		).Sx(ucx.SxMy(0)))
	}
	return ucx.FieldGroupNode().Children(children...)
}

func maintenanceJobIds(record shared.ClusterRecord) map[string]string {
	jobIds := map[string]string{}
	for _, node := range record.Nodes {
		jobIds[node.Hostname] = node.JobId
	}
	return jobIds
}

func (app *stackUiApp) maintenanceRunsSection(runs []maintenance.MaintenanceRun) []ucx.UiNode {
	if len(runs) == 0 {
		return nil
	}

	record, recordOk := app.readClusterRecord()
	jobIds := maintenanceJobIds(record)

	children := []ucx.UiNode{}
	for _, run := range runs {
		children = append(children, app.maintenanceRunPanel(run, jobIds, record, recordOk, ""))
	}
	return children
}

type maintenanceRunTaskView struct {
	Kind          string
	TargetRelease string
	Items         []maintenance.RunItem
}

func maintenanceRunTaskViews(
	run maintenance.MaintenanceRun,
	targetItems []maintenance.RunItem,
) []maintenanceRunTaskView {
	views := make([]maintenanceRunTaskView, 0, len(run.Effective.Tasks))
	seen := map[string]bool{}
	for _, task := range run.Tasks {
		items := task.Items
		if len(items) == 0 {
			items = maintenanceCopyRunItems(targetItems)
		}
		views = append(views, maintenanceRunTaskView{
			Kind:          task.Kind,
			TargetRelease: task.TargetRelease,
			Items:         items,
		})
		seen[task.Kind] = true
	}
	for _, task := range run.Effective.Tasks {
		if seen[task.Kind] {
			continue
		}
		views = append(views, maintenanceRunTaskView{
			Kind:          task.Kind,
			TargetRelease: task.TargetRelease,
			Items:         maintenanceCopyRunItems(targetItems),
		})
	}
	if len(views) == 0 {
		for _, task := range run.Request.Tasks {
			views = append(views, maintenanceRunTaskView{
				Kind:          task.Kind,
				TargetRelease: task.TargetRelease,
				Items:         maintenanceCopyRunItems(targetItems),
			})
		}
	}
	return views
}

func maintenanceCopyRunItems(items []maintenance.RunItem) []maintenance.RunItem {
	return append([]maintenance.RunItem(nil), items...)
}

func maintenanceRunTargetItems(
	run maintenance.MaintenanceRun,
	record shared.ClusterRecord,
	recordOk bool,
) []maintenance.RunItem {
	seen := map[string]bool{}
	items := []maintenance.RunItem{}
	for _, task := range run.Tasks {
		for _, item := range task.Items {
			if seen[item.NodeName] {
				continue
			}
			seen[item.NodeName] = true
			items = append(items, maintenance.RunItem{
				NodeName: item.NodeName,
				NodeUid:  item.NodeUid,
				Group:    item.Group,
				Phase:    maintenance.RunPending,
			})
		}
	}
	if len(items) > 0 || !recordOk {
		return items
	}

	target := run.Effective.Target
	if !target.All && len(target.Pools) == 0 && len(target.Nodes) == 0 {
		target = run.Request.Target
	}
	pools := map[string]bool{}
	for _, pool := range target.Pools {
		pools[pool] = true
	}
	nodes := map[string]bool{}
	for _, nodeName := range target.Nodes {
		nodes[nodeName] = true
	}
	for _, node := range record.Nodes {
		include := target.All || pools[node.Group] || nodes[node.Hostname]
		if !include {
			continue
		}
		items = append(items, maintenance.RunItem{
			NodeName: node.Hostname,
			Group:    node.Group,
			Phase:    maintenance.RunPending,
		})
	}
	return items
}

func maintenanceRunPendingItem(
	nodeName string,
	record shared.ClusterRecord,
	recordOk bool,
) maintenance.RunItem {
	item := maintenance.RunItem{
		NodeName: nodeName,
		Phase:    maintenance.RunPending,
	}
	if !recordOk {
		return item
	}
	for _, node := range record.Nodes {
		if node.Hostname == nodeName {
			item.Group = node.Group
			break
		}
	}
	return item
}

func maintenanceRunItemsForNode(items []maintenance.RunItem, nodeName string) []maintenance.RunItem {
	for _, item := range items {
		if item.NodeName == nodeName {
			return []maintenance.RunItem{item}
		}
	}
	return nil
}

func (app *stackUiApp) maintenanceRunPanel(
	run maintenance.MaintenanceRun,
	jobIds map[string]string,
	record shared.ClusterRecord,
	recordOk bool,
	nodeName string,
) ucx.UiNode {
	children := []ucx.UiNode{}
	if run.Phase == maintenance.RunFailed {
		children = append(children, progressTagLabel("Failed", ucx.ColorErrorMain))
	}

	viewRun := run
	targetItems := maintenanceRunTargetItems(run, record, recordOk)
	if nodeName != "" {
		targetItems = []maintenance.RunItem{maintenanceRunPendingItem(nodeName, record, recordOk)}
		viewRun.Tasks = nil
		for _, task := range run.Tasks {
			items := maintenanceRunItemsForNode(task.Items, nodeName)
			if len(items) == 0 {
				continue
			}
			task.Items = items
			viewRun.Tasks = append(viewRun.Tasks, task)
		}
	}

	shownDetails := map[string]bool{}
	itemErrors := map[string]bool{}
	for _, task := range viewRun.Tasks {
		for _, item := range task.Items {
			if item.Error != "" {
				itemErrors[item.Error] = true
			}
		}
	}
	runErrorRedundant := false
	for itemError := range itemErrors {
		if strings.Contains(run.Error, itemError) {
			runErrorRedundant = true
			break
		}
	}
	if run.Error != "" && !runErrorRedundant {
		if run.Phase == maintenance.RunFailed {
			children = append(children, ucx.Text(run.Error).Sx(ucx.SxColor(ucx.ColorErrorMain)))
		} else {
			children = append(children, ucx.Warning(run.Error))
		}
		shownDetails[run.Error] = true
	}
	failedChecks := []ucx.UiNode{}
	for _, check := range run.Checks {
		if check.Ok || check.Detail == "" || shownDetails[check.Detail] || itemErrors[check.Detail] {
			continue
		}
		failedChecks = append(failedChecks, ucx.Text(maintenanceCheckLabel(check.Name)+": "+check.Detail).
			Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		shownDetails[check.Detail] = true
	}
	if len(failedChecks) > 0 {
		children = append(children, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).
			Sx(ucx.SxMinWidth(0)).Children(failedChecks...))
	}

	for _, task := range maintenanceRunTaskViews(viewRun, targetItems) {
		children = append(children, app.maintenanceRunTaskNode(viewRun, task, jobIds, nodeName))
	}

	buttons := []ucx.UiNode{}
	if run.Phase == maintenance.RunBlocked || run.Phase == maintenance.RunFailed {
		buttons = append(buttons, ucx.ButtonEx(
			"maintenanceRetry-"+run.Uid,
			"Retry run",
			ucx.ColorPrimaryMain,
			"",
			"",
			"",
		).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			maintenanceRetryAsync(app, run.Uid)
		}))
	}
	if len(buttons) > 0 {
		children = append(children, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Children(buttons...))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
		Sx(ucx.SxMinWidth(0)).Children(children...)
}

func (app *stackUiApp) maintenanceRunTaskNode(
	run maintenance.MaintenanceRun,
	task maintenanceRunTaskView,
	jobIds map[string]string,
	nodeName string,
) ucx.UiNode {
	title := maintenanceTaskTitle(task)
	if len(task.Items) == 0 {
		return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).
			Children(
				ucx.Text(title).Sx(ucx.SxFontWeight("600")),
				ucx.Text("Waiting for target nodes").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
			)
	}

	completed := 0
	for _, item := range task.Items {
		if item.Phase == maintenance.RunCompleted {
			completed++
		}
	}
	allCompleted := completed == len(task.Items)

	grouped := map[string][]maintenance.RunItem{}
	groupNames := []string{}
	for _, item := range task.Items {
		group := item.Group
		if group == "" {
			group = "Other"
		}
		if _, present := grouped[group]; !present {
			groupNames = append(groupNames, group)
		}
		grouped[group] = append(grouped[group], item)
	}
	maintenanceSortGroups(groupNames)

	children := []ucx.UiNode{
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
			Sx(ucx.SxAlignItemsCenter, ucx.SxFlexWrapWrap).
			Children(
				ucx.Text(title).Sx(ucx.SxFontWeight("600")),
				progressTagLabel(
					fmt.Sprintf("%d of %d completed", completed, len(task.Items)),
					ucx.ColorTextSecondary,
				),
			),
	}
	if allCompleted {
		return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).
			Sx(ucx.SxMinWidth(0)).Children(children...)
	}

	for _, group := range groupNames {
		items := grouped[group]
		completedInGroup := 0
		for _, item := range items {
			if item.Phase == maintenance.RunCompleted {
				completedInGroup++
			}
		}

		groupChildren := []ucx.UiNode{}
		if nodeName == "" {
			groupChildren = append(groupChildren, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
				Sx(ucx.SxAlignItemsCenter, ucx.SxFlexWrapWrap).
				Children(
					ucx.Text(maintenanceGroupLabel(group)).Sx(ucx.SxFontWeight("600")),
					ucx.Text(fmt.Sprintf("%d of %d completed", completedInGroup, len(items))).
						Sx(ucx.SxColor(ucx.ColorTextSecondary)),
				))
		}
		if completedInGroup < len(items) {
			for _, item := range items {
				groupChildren = append(groupChildren, app.maintenanceRunItemNode(run, task, item, jobIds))
			}
		}
		if len(groupChildren) == 0 {
			continue
		}

		groupStyle := []ucx.SxOption{ucx.SxMt(8), ucx.SxMinWidth(0)}
		if nodeName == "" {
			groupStyle = append(groupStyle, ucx.SxPl(16))
		}
		children = append(children, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).
			Sx(groupStyle...).Children(groupChildren...))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).
		Sx(ucx.SxMinWidth(0)).Children(children...)
}

func (app *stackUiApp) maintenanceRunItemNode(
	run maintenance.MaintenanceRun,
	task maintenanceRunTaskView,
	item maintenance.RunItem,
	jobIds map[string]string,
) ucx.UiNode {
	color := maintenanceRunItemColor(item.Phase)
	rowChildren := []ucx.UiNode{
		app.progressName(
			"maintenanceRunNode-"+run.Uid+"-"+task.Kind+"-"+item.NodeName,
			item.NodeName,
			"maintenance/"+url.PathEscape(item.NodeName),
		),
	}
	if item.Phase == maintenance.RunRunning {
		rowChildren = append(rowChildren, ucx.Spinner(14))
	} else {
		action := maintenanceRunItemAction(task.Kind, item)
		rowChildren = append(rowChildren, progressTagLabel(action, color))
	}
	rowChildren = append(rowChildren, ucx.Box())
	children := []ucx.UiNode{
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
			Sx(ucx.SxAlignItemsCenter, ucx.SxMinWidth(0)).
			Children(rowChildren...),
	}
	if item.Error != "" {
		if item.Phase == maintenance.RunFailed {
			children = append(children, ucx.Text(item.Error).Sx(ucx.SxColor(ucx.ColorErrorMain)))
		} else {
			children = append(children, ucx.Warning(item.Error))
		}
	}
	if jobId := jobIds[item.NodeName]; jobId != "" &&
		(item.Phase == maintenance.RunRunning || item.Phase == maintenance.RunFailed) {
		children = append(children, ucx.JobLogs(
			"maintenanceRunLog-"+run.Uid+"-"+task.Kind+"-"+item.NodeName,
			jobId,
		).WithLogHeight(260))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
		Sx(
			ucx.SxPy(10),
			ucx.SxPl(16),
			ucx.SxMinWidth(0),
			ucx.SxBorderWidth(0),
			ucx.SxBorderTopWidth(1),
			ucx.SxBorderSolid,
			ucx.SxBorderColor(ucx.ColorBorderColor),
		).Children(children...)
}

func maintenanceTaskTitle(task maintenanceRunTaskView) string {
	switch task.Kind {
	case maintenance.TaskK8sUpgrade:
		if task.TargetRelease != "" {
			return "Kubernetes upgrade to " + task.TargetRelease
		}
		return "Kubernetes upgrade"
	case maintenance.TaskOsUpdate:
		return "Operating-system update"
	case maintenance.TaskCordon:
		return "Cordon or drain"
	case maintenance.TaskUncordon:
		return "Uncordon"
	case maintenance.TaskRemove:
		return "Remove node"
	}
	return task.Kind
}

func maintenanceRunItemAction(
	taskKind string,
	item maintenance.RunItem,
) string {
	switch item.Phase {
	case maintenance.RunCompleted:
		switch taskKind {
		case maintenance.TaskCordon:
			return "Cordoned"
		case maintenance.TaskUncordon:
			return "Uncordoned"
		case maintenance.TaskRemove:
			return "Removed"
		}
		return "Up to date"
	case maintenance.RunCancelled:
		return "Cancelled"
	case maintenance.RunFailed:
		return "Failed"
	}
	return "Waiting to start"
}

func maintenanceCheckLabel(name string) string {
	switch name {
	case "cluster":
		return "Cluster"
	case "recovery":
		return "Recovery"
	case "operations":
		return "Maintenance state"
	case "nodes":
		return "Nodes"
	case "quorum":
		return "Quorum"
	case "agents":
		return "Node maintenance"
	case "versions":
		return "Version spread"
	case "path":
		return "Upgrade path"
	case "backup":
		return "Backup"
	case "os":
		return "Operating-system updates"
	case "upgrade":
		return "Kubernetes upgrade"
	}
	return name
}

func maintenanceRunItemColor(phase string) ucx.Color {
	switch phase {
	case maintenance.RunCompleted:
		return ucx.ColorSuccessMain
	case maintenance.RunFailed:
		return ucx.ColorErrorMain
	}
	return ucx.ColorTextSecondary
}

func maintenanceRunTimestamp(value time.Time) string {
	return value.UTC().Format("Jan 2, 15:04 UTC")
}

func maintenanceRunTimingText(run maintenance.MaintenanceRun, now time.Time) string {
	text := ""
	if !run.StartedAt.IsZero() {
		text += " Started " + maintenanceRunTimestamp(run.StartedAt) + "."
	}
	if elapsed, ok := maintenanceElapsedLabel(run.StartedAt, run.FinishedAt, now); ok {
		text += " " + elapsed + "."
	}
	return text
}

func maintenanceElapsedLabel(startedAt time.Time, finishedAt time.Time, now time.Time) (string, bool) {
	if startedAt.IsZero() {
		return "", false
	}
	end := now
	if !finishedAt.IsZero() {
		end = finishedAt
	}
	if end.Before(startedAt) {
		return "", false
	}
	return "Elapsed " + maintenanceDurationShort(end.Sub(startedAt)), true
}

func maintenanceDurationShort(delta time.Duration) string {
	switch {
	case delta < time.Minute:
		return fmt.Sprintf("%d s", int(delta/time.Second))
	case delta < time.Hour:
		return fmt.Sprintf("%d min %d s", int(delta/time.Minute), int(delta/time.Second)%60)
	default:
		return fmt.Sprintf("%d h %d min", int(delta/time.Hour), int(delta/time.Minute)%60)
	}
}

func maintenanceSortGroups(groupNames []string) {
	sort.Slice(groupNames, func(i int, j int) bool {
		leftControlPlane := groupNames[i] == shared.GroupControlPlane
		rightControlPlane := groupNames[j] == shared.GroupControlPlane
		if leftControlPlane != rightControlPlane {
			return leftControlPlane
		}
		return groupNames[i] < groupNames[j]
	})
}

func maintenanceGroupLabel(group string) string {
	if group == shared.GroupControlPlane {
		return "Control plane"
	}
	if group == "" {
		return "Other"
	}
	return group
}

// Node page
// =====================================================================================================================

func (app *stackUiApp) maintenanceNodePage(nodeName string) []ucx.UiNode {
	if app.maintenanceNodeName != nodeName {
		app.maintenanceNodeName = nodeName
		app.MaintenanceTask = maintenanceActionK8s
		app.MaintenanceTimeoutSeconds = maintenanceDefaultTimeoutSeconds
		app.MaintenanceDrain = false
		app.MaintenanceCordon = false
		app.MaintenanceDeleteVolatilePods = false
		app.MaintenanceBypassDisruptionBudgets = false
		app.MaintenanceForceDelete = false
		app.MaintenanceTargetRelease = ""
		app.maintenanceNodeRunConfirm = false
	}

	bottom := []ucx.UiNode{shellBottomNode(app, "nodes", "Node maintenance", nodeName)}

	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return app.messagePage(bottom, "browse/nodes", ucx.Text("Could not read the cluster record."))
	}

	known := false
	var nodeRecord shared.ClusterNodeRecord
	for i := range record.Nodes {
		if record.Nodes[i].Hostname == nodeName {
			known = true
			nodeRecord = record.Nodes[i]
			break
		}
	}

	runs, runsErr := maintenance.RunsSnapshot()
	if runsErr != nil {
		return app.messagePage(bottom, "browse/nodes", ucx.Text(fmt.Sprintf(
			"Could not read the maintenance runs: %s",
			runsErr,
		)))
	}
	run, hasRun := maintenanceRunForNode(runs, nodeName, nodeRecord.Group, known)
	if !known && !hasRun {
		return app.messagePage(bottom, "browse/nodes", ucx.Text(fmt.Sprintf(
			"The node %s is no longer part of the cluster.",
			nodeName,
		)))
	}

	content := []ucx.UiNode{ucx.H3Ex("maintenanceNodeHeading", nodeName)}
	if !known {
		content = append(content, ucx.Text(fmt.Sprintf(
			"The node %s is no longer part of the cluster.",
			nodeName,
		)))
	}

	runActive := hasRun && maintenance.RunActive(run)
	if runActive {
		app.maintenanceNodeRunConfirm = false
		content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(
			app.maintenanceNodeRunInfo(run, nodeName),
			app.maintenanceRunPanel(run, maintenanceJobIds(record), record, recordOk, nodeName),
		))
	} else {
		idle := []ucx.UiNode{}
		if hasRun {
			idle = append(idle, ucx.Text("Last maintenance at "+maintenanceTimestamp(run.RequestedAt)+".").
				Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}
		if known {
			idle = append(idle, app.maintenanceRunForm())
		}
		if len(idle) > 0 {
			content = append(content, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(idle...))
		}
		if known && app.maintenanceNodeRunConfirm {
			content = append(content, maintenanceNodeRunDialog(app, nodeName))
		}
	}

	return []ucx.UiNode{app.appShell(appShellProps{
		Content:    []ucx.UiNode{shellContentBox(800, content...)},
		Bottom:     bottom,
		EscapePath: "browse/nodes",
	})}
}

func (app *stackUiApp) maintenanceNodeRunInfo(
	run maintenance.MaintenanceRun,
	nodeName string,
) ucx.UiNode {
	text := maintenanceNodeRunStatusText(run, nodeName) + maintenanceRunTimingText(run, time.Now().UTC())
	box := ucx.Tip(text)
	needsAttention := run.Phase == maintenance.RunFailed || run.Phase == maintenance.RunBlocked
	if needsAttention && !run.CancelRequested {
		box = ucx.Warning(text)
	}
	box = box.WarningCentered(true).Sx(ucx.SxMb(0))
	if !run.CancelRequested {
		box = box.Children(
			ucx.ButtonEx(
				"maintenanceNodeCancel-"+run.Uid,
				"Cancel run",
				ucx.ColorErrorMain,
				"",
				"",
				"",
			).ButtonBusy("maintenanceBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				maintenanceCancelAsync(app, run.Uid)
			}),
		)
	}
	return box
}

func maintenanceNodeRunStatusText(run maintenance.MaintenanceRun, nodeName string) string {
	if run.CancelRequested {
		return "Maintenance is being cancelled on this node."
	}

	phase, found := maintenanceRunNodePhase(run, nodeName)
	if found {
		switch phase {
		case maintenance.RunFailed:
			return "Maintenance failed on this node."
		case maintenance.RunRunning:
			return "Maintenance is running on this node."
		case maintenance.RunCompleted:
			return "Maintenance is complete on this node."
		}
	}

	switch run.Phase {
	case maintenance.RunFailed:
		return "Maintenance failed."
	case maintenance.RunBlocked:
		return "Maintenance is blocked."
	}
	return "Maintenance is waiting to start on this node."
}

func maintenanceRunNodePhase(run maintenance.MaintenanceRun, nodeName string) (string, bool) {
	found := false
	running := false
	pending := false
	completed := false
	for _, task := range run.Tasks {
		for _, item := range task.Items {
			if item.NodeName != nodeName {
				continue
			}
			found = true
			switch item.Phase {
			case maintenance.RunFailed:
				return maintenance.RunFailed, true
			case maintenance.RunRunning:
				running = true
			case maintenance.RunPending:
				pending = true
			case maintenance.RunCompleted:
				completed = true
			}
		}
	}

	switch {
	case running:
		return maintenance.RunRunning, true
	case pending:
		return maintenance.RunPending, true
	case completed:
		return maintenance.RunCompleted, true
	}
	return "", found
}

func maintenanceNodeRunDialog(app *stackUiApp, nodeName string) ucx.UiNode {
	return dashboardResourceActionDialog(
		"maintenanceNodeRun",
		"Start maintenance run",
		maintenanceNodeRunMessage(app.MaintenanceTask, nodeName),
		"",
		ucx.ColorPrimaryMain,
		func() {
			app.maintenanceNodeRunConfirm = false
			ucx.AppUpdateUi(app)
		},
		func() {
			app.maintenanceNodeRunConfirm = false
			ucx.AppUpdateUi(app)
			maintenanceSubmitRunAsync(app, nodeName)
		},
	)
}

func maintenanceNodeRunMessage(task string, nodeName string) string {
	switch task {
	case maintenanceActionUpdate:
		return "Starts the Kubernetes and operating-system update on " + nodeName + "."
	case maintenanceActionK8s:
		return "Starts the Kubernetes upgrade on " + nodeName + "."
	case maintenanceActionOs:
		return "Starts the operating-system update on " + nodeName + "."
	case maintenanceActionCordon:
		return "Starts cordon or drain on " + nodeName + "."
	case maintenanceActionUncordon:
		return "Uncordons " + nodeName + "."
	case maintenanceActionRemove:
		return "Removes " + nodeName + " after cordoning and draining it."
	}
	return "Starts maintenance on " + nodeName + "."
}

// Submission
// =====================================================================================================================

func maintenanceSubmitRunAsync(app *stackUiApp, nodeName string) {
	if app.MaintenanceBusy {
		return
	}

	request, err := maintenanceRequestFromApp(app, nodeName)
	if err != nil {
		ucxsvc.UiSendFailure(app, err.Error())
		return
	}

	app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
		_, err := maintenance.SubmitRun(request, "dashboard")
		return err
	}, app.maintenanceReportError)
}

func maintenanceRequestFromApp(app *stackUiApp, nodeName string) (maintenance.RunRequest, error) {
	timeout := app.MaintenanceTimeoutSeconds
	if timeout < 30 || timeout > 3600 {
		return maintenance.RunRequest{}, fmt.Errorf("the timeout must be from 30 to 3600 seconds")
	}
	usesForce := app.MaintenanceTask == maintenanceActionCordon ||
		app.MaintenanceTask == maintenanceActionRemove
	if usesForce && app.MaintenanceForceDelete && !app.MaintenanceBypassDisruptionBudgets {
		return maintenance.RunRequest{}, fmt.Errorf("force delete requires bypassing the disruption budgets")
	}

	preparation := maintenance.PreparationNone
	if app.MaintenanceDrain {
		preparation = maintenance.PreparationDrain
	} else if app.MaintenanceCordon {
		preparation = maintenance.PreparationCordon
	}

	tasks := []maintenance.TaskRequest{}
	action := app.MaintenanceTask
	switch action {
	case maintenanceActionUpdate:
		tasks = append(tasks,
			maintenance.TaskRequest{
				Kind:                    maintenance.TaskK8sUpgrade,
				TargetRelease:           app.MaintenanceTargetRelease,
				TimeoutSeconds:          timeout,
				DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
				BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
			},
			maintenance.TaskRequest{
				Kind:               maintenance.TaskOsUpdate,
				TimeoutSeconds:     timeout,
				DeleteVolatilePods: app.MaintenanceDeleteVolatilePods,
			},
		)
	case maintenanceActionK8s:
		tasks = append(tasks, maintenance.TaskRequest{
			Kind:                    maintenance.TaskK8sUpgrade,
			TargetRelease:           app.MaintenanceTargetRelease,
			TimeoutSeconds:          timeout,
			DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
			BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
		})
	case maintenanceActionOs:
		tasks = append(tasks, maintenance.TaskRequest{
			Kind:               maintenance.TaskOsUpdate,
			TimeoutSeconds:     timeout,
			DeleteVolatilePods: app.MaintenanceDeleteVolatilePods,
		})
	case maintenanceActionCordon:
		tasks = append(tasks, maintenance.TaskRequest{
			Kind:                    maintenance.TaskCordon,
			Preparation:             preparation,
			TimeoutSeconds:          timeout,
			DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
			BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
			ForceDelete:             app.MaintenanceForceDelete,
		})
	case maintenanceActionUncordon:
		tasks = append(tasks, maintenance.TaskRequest{
			Kind:           maintenance.TaskUncordon,
			TimeoutSeconds: timeout,
		})
	case maintenanceActionRemove:
		tasks = append(tasks, maintenance.TaskRequest{
			Kind:                    maintenance.TaskRemove,
			TimeoutSeconds:          timeout,
			DeleteVolatilePods:      app.MaintenanceDeleteVolatilePods,
			BypassDisruptionBudgets: app.MaintenanceBypassDisruptionBudgets,
			ForceDelete:             app.MaintenanceForceDelete,
		})
	default:
		return maintenance.RunRequest{}, fmt.Errorf("select a maintenance task")
	}

	return maintenance.RunRequest{
		Tasks:               tasks,
		Target:              maintenance.Target{Nodes: []string{nodeName}},
		MaxParallelPerGroup: 1,
	}, nil
}

func maintenanceSubmitRemoveNode(nodeName string) error {
	_, err := maintenance.SubmitRun(maintenance.RunRequest{
		Tasks: []maintenance.TaskRequest{{
			Kind:           maintenance.TaskRemove,
			TimeoutSeconds: maintenanceDefaultTimeoutSeconds,
		}},
		Target: maintenance.Target{Nodes: []string{nodeName}},
	}, "dashboard")
	return err
}

func maintenanceUncordonAsync(app *stackUiApp, nodeName string) {
	if app.MaintenanceBusy {
		return
	}
	app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
		_, err := maintenance.SubmitRun(maintenance.RunRequest{
			Tasks: []maintenance.TaskRequest{{
				Kind:           maintenance.TaskUncordon,
				TimeoutSeconds: maintenanceDefaultTimeoutSeconds,
			}},
			Target: maintenance.Target{Nodes: []string{nodeName}},
		}, "dashboard")
		return err
	}, app.maintenanceReportError)
}

func maintenanceCancelAsync(app *stackUiApp, runUid string) {
	if app.MaintenanceBusy {
		return
	}
	app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
		_, err := maintenance.CancelRun(runUid, "")
		return err
	}, app.maintenanceReportError)
}

func maintenanceRetryAsync(app *stackUiApp, runUid string) {
	if app.MaintenanceBusy {
		return
	}
	app.runAsyncAction("maintenanceBusy", &app.MaintenanceBusy, func() error {
		_, err := maintenance.RetryRun(runUid, "")
		return err
	}, app.maintenanceReportError)
}

func (app *stackUiApp) maintenanceReportError(err error) {
	if err == nil {
		return
	}
	session := *app.Session()
	if session == nil {
		return
	}
	dashboardSendUiMessage(session, err.Error(), false)
}

func maintenanceRunForNode(
	runs []maintenance.MaintenanceRun,
	nodeName string,
	nodeGroup string,
	nodeKnown bool,
) (maintenance.MaintenanceRun, bool) {
	var fallback *maintenance.MaintenanceRun
	for runIndex := range runs {
		run := runs[runIndex]
		targeted := maintenanceRunContainsNode(run, nodeName)
		if !targeted && nodeKnown {
			targeted = maintenanceRunTargetsGroup(run, nodeGroup)
		}
		if !targeted {
			continue
		}
		if maintenance.RunActive(run) {
			return run, true
		}
		if fallback == nil {
			fallback = &runs[runIndex]
		}
	}
	if fallback != nil {
		return *fallback, true
	}
	return maintenance.MaintenanceRun{}, false
}

func maintenanceRunContainsNode(run maintenance.MaintenanceRun, nodeName string) bool {
	for _, name := range run.Effective.Target.Nodes {
		if name == nodeName {
			return true
		}
	}
	for _, task := range run.Tasks {
		for _, item := range task.Items {
			if item.NodeName == nodeName {
				return true
			}
		}
	}
	return false
}

func maintenanceRunTargetsGroup(run maintenance.MaintenanceRun, nodeGroup string) bool {
	if nodeGroup == "" {
		return false
	}
	if run.Effective.Target.All {
		return true
	}
	for _, pool := range run.Effective.Target.Pools {
		if pool == nodeGroup {
			return true
		}
	}
	return false
}

func maintenanceUpgradeReleaseOptions(currentVersion string) []ucx.Option {
	now := time.Now().UTC()
	options := []ucx.Option{}
	for _, release := range shared.StableReleases(now) {
		if currentVersion != "" && !shared.NodeAgentUpgradeAllowed(currentVersion, release.Release) {
			continue
		}
		options = append(options, ucx.Option{Key: release.Release, Value: shared.ReleaseSupportLabel(release, now)})
	}
	return options
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

func maintenanceTimestamp(value time.Time) string {
	if value.IsZero() {
		return "unknown"
	}
	return value.UTC().Format("Mon Jan 2, 15:04 UTC")
}

func (app *stackUiApp) homeMaintenanceMetric() []ucx.UiNode {
	value := "Unknown"
	color := ucx.ColorTextSecondary

	status, err := maintenance.ReadStatus()
	if err == nil {
		now := time.Now().UTC()
		_, _, windowOpen := maintenance.WindowOpen(now, status.Policy.Window)
		needsAttention := false
		for _, run := range status.ActiveRuns {
			if run.Phase == maintenance.RunFailed || run.Phase == maintenance.RunBlocked {
				needsAttention = true
				break
			}
		}
		switch {
		case needsAttention:
			value = "Needs attention"
			color = ucx.ColorErrorMain
		case len(status.ActiveRuns) > 0:
			value = "Maintenance in progress"
			color = ucx.ColorTextPrimary
		case !status.Policy.Kubernetes.Enabled && !status.Policy.Os.Enabled:
			value = "Off"
		case windowOpen:
			value = "Window open"
			color = ucx.ColorTextPrimary
		default:
			if start, _, ok := maintenance.NextWindow(now, status.Policy.Window); ok {
				value = start.UTC().Format("Mon 15:04")
				color = ucx.ColorTextPrimary
			}
		}
	}

	link := ucx.LinkButton("homeMaintenance", "(View)", ucx.ColorLinkColor).Sx(
		ucx.SxFontSize(12),
		ucx.SxFontWeight("400"),
	).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		ucxsvc.RouterPushPage(app, "maintenance")
	})

	return app.homeMetricEx("Maintenance", []ucx.UiNode{link}, homeMetricText(value, color))
}
