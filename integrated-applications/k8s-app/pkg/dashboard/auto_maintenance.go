package dashboard

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

type dashboardAutoStatus struct {
	PolicyFound bool
	Policy      maintenance.AutoPolicy
	StateFound  bool
	State       maintenance.AutoState
}

func (app *stackUiApp) autoStatusRead() (dashboardAutoStatus, error) {
	client, err := app.clusterStateValueClient()
	if err != nil {
		return dashboardAutoStatus{}, err
	}

	result := dashboardAutoStatus{}

	policyValue, err := shared.ClusterStateValueRead(client, maintenance.AutoPolicyKey)
	if err != nil {
		return dashboardAutoStatus{}, fmt.Errorf("could not read the automatic maintenance policy: %s", err)
	}

	result.PolicyFound = policyValue.Found && !policyValue.Empty

	if result.PolicyFound {
		err = json.Unmarshal(policyValue.Value, &result.Policy)
		if err != nil {
			return dashboardAutoStatus{}, fmt.Errorf("could not parse the automatic maintenance policy: %s", err)
		}
	}

	stateValue, err := shared.ClusterStateValueRead(client, maintenance.AutoStateKey)
	if err != nil {
		return dashboardAutoStatus{}, fmt.Errorf("could not read the automatic maintenance state: %s", err)
	}

	result.StateFound = stateValue.Found && !stateValue.Empty

	if result.StateFound {
		err = json.Unmarshal(stateValue.Value, &result.State)
		if err != nil {
			return dashboardAutoStatus{}, fmt.Errorf("could not parse the automatic maintenance state: %s", err)
		}
	}

	return result, nil
}

type autoSettingsForm struct {
	K8sEnabled bool
	OsEnabled  bool
	WindowDay  string
	StartHour  string
}

func autoSettingsFromModel(app *stackUiApp) autoSettingsForm {
	return autoSettingsForm{
		K8sEnabled: app.AutoMaintenanceK8sEnabled,
		OsEnabled:  app.AutoMaintenanceOsEnabled,
		WindowDay:  app.AutoWindowDay,
		StartHour:  app.AutoWindowStartHour,
	}
}

func autoPolicySave(client shared.ClusterStateClient, form autoSettingsForm) error {
	for attempt := 0; attempt < 3; attempt++ {
		value, err := shared.ClusterStateValueRead(client, maintenance.AutoPolicyKey)
		if err != nil {
			return fmt.Errorf("could not read the automatic maintenance policy: %s", err)
		}

		policy := maintenance.AutoDefaultPolicy()
		if value.Found && !value.Empty {
			err = json.Unmarshal(value.Value, &policy)
			if err != nil {
				return fmt.Errorf("could not parse the automatic maintenance policy: %s", err)
			}
		}

		policy = autoPolicyFromForm(form, policy)

		encoded, err := json.Marshal(policy)
		if err != nil {
			return err
		}

		_, writeErr := shared.ClusterStateValueWrite(client, maintenance.AutoPolicyKey, encoded, value.Revision)
		if writeErr == nil {
			return nil
		}
		if !shared.IsConflict(writeErr) {
			latest, latestErr := shared.ClusterStateValueRead(client, maintenance.AutoPolicyKey)
			if latestErr != nil || latest.Revision == value.Revision {
				return fmt.Errorf("could not save the automatic maintenance policy: %s", writeErr)
			}
		}
	}

	return errors.New("the automatic maintenance policy changed while saving; please try again")
}

func autoPolicyFromForm(form autoSettingsForm, policy maintenance.AutoPolicy) maintenance.AutoPolicy {
	policy.SchemaRevision = maintenance.AutoSchemaRevision
	policy.K8s.Enabled = form.K8sEnabled
	policy.Os.Enabled = form.OsEnabled

	day := strings.ToLower(strings.TrimSpace(form.WindowDay))
	if maintenance.AutoDayValid(day) {
		policy.Window.Days = []string{day}
	}

	startHour, hourErr := strconv.Atoi(strings.TrimSpace(form.StartHour))
	if hourErr == nil && startHour >= 0 && startHour <= 23 {
		policy.Window.StartHourUtc = startHour
	}

	return maintenance.AutoPolicyNormalize(policy)
}

func autoSettingsSaveAsync(app *stackUiApp) {
	session := *app.Session()
	if session == nil {
		return
	}
	form := autoSettingsFromModel(app)
	app.autoSettingsPending = &form
	app.autoSettingsSaveError = ""
	if app.autoSettingsSaving {
		return
	}
	client, err := app.clusterStateValueClient()
	if err != nil {
		app.autoSettingsSaveError = err.Error()
		return
	}
	app.autoSettingsSaving = true
	go func() {
		for {
			app.mu.Lock()
			pending := *app.autoSettingsPending
			app.autoSettingsPending = nil
			app.mu.Unlock()

			err := autoPolicySave(client, pending)

			app.mu.Lock()
			done := app.autoSettingsPending == nil
			if done {
				app.autoSettingsSaving = false
				if err != nil {
					app.autoSettingsSaveError = err.Error()
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

func autoMaintenancePage(app *stackUiApp) []ucx.UiNode {
	now := time.Now().UTC()

	content := []ucx.UiNode{
		ucx.Text("Automatic maintenance").Sx(ucx.SxFontSize(20), ucx.SxFontWeight("600")),
	}

	status, err := app.autoStatusRead()
	if err != nil {
		content = append(content, ucx.Warning(fmt.Sprintf("Could not read the automatic maintenance settings: %s", err)))
		return autoMaintenancePageShell(app, content)
	}

	policy := maintenance.AutoDefaultPolicy()
	if status.PolicyFound {
		policy = maintenance.AutoPolicyNormalize(status.Policy)
	}

	content = append(content, autoMaintenanceInfoRow(app, status, policy, now)...)
	if app.autoRunNowConfirm {
		content = append(content, autoMaintenanceRunNowDialog(app, policy))
	}

	autoMaintenanceInitForm(app, policy)
	if status.StateFound && autoMaintenanceRunVisible(status) {
		content = append(content, autoMaintenanceSection("Update run", autoMaintenanceRunNodes(app, status, policy)...))
	}

	content = append(content, autoMaintenanceSection("Updates", autoMaintenanceUpdatesNodes(app, status, policy, now)...))
	content = append(content, autoMaintenanceSection("Weekly schedule", autoMaintenanceWindowNodes(app, status, policy, now)...))

	return autoMaintenancePageShell(app, content)
}

func autoMaintenanceRunVisible(status dashboardAutoStatus) bool {
	run := status.State.LastRun
	if maintenance.AutoRunActive(run) || maintenance.AutoStateBlocked(status.State) {
		return true
	}

	nodes, err := maintenance.AutoOsRecoveryNodes()
	if err == nil && len(nodes) > 0 {
		return true
	}

	if run.Uid == "" {
		return false
	}

	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return false
	}
	for _, operation := range snapshot {
		if operation.AutoRunUid == run.Uid && operation.Phase != maintenance.PhaseCompleted {
			return true
		}
	}

	return false
}

func autoMaintenanceInfoRow(
	app *stackUiApp,
	status dashboardAutoStatus,
	policy maintenance.AutoPolicy,
	now time.Time,
) []ucx.UiNode {
	text, isTip := autoMaintenanceInfoText(status, policy, now)

	box := ucx.Warning(text)
	if isTip {
		box = ucx.Tip(text)
	}
	if autoMaintenanceCanRunNow(status, policy) {
		box = box.WarningCentered(true).Children(ucx.Button("autoMaintenanceRunNow", "Run now", ucx.ColorPrimaryMain).
			ButtonBusy("autoMaintenanceBusy").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				app.autoRunNowConfirm = true
				ucx.AppUpdateUi(app)
			}))
	}

	return []ucx.UiNode{box}
}

func autoMaintenanceInfoText(status dashboardAutoStatus, policy maintenance.AutoPolicy, now time.Time) (string, bool) {
	if !policy.K8s.Enabled && !policy.Os.Enabled {
		return "Automatic maintenance is off. Enable at least one update.", false
	}

	windowStart, windowEnd, windowOpen := autoMaintenanceOpenWindow(policy, now)
	if windowOpen {
		if !autoMaintenanceWindowUsed(status, policy, windowStart) {
			if maintenance.AutoTestWindowOpen(now, policy) {
				return "The manual update window closes " + autoTimestampLabel(windowEnd) + ".", true
			}
			return "The maintenance window is open until " + windowEnd.UTC().Format("Monday 15:04") + " UTC.", true
		}
		if label, ok := autoMaintenanceNextRunLabelFrom(policy, windowEnd, now); ok {
			return label, true
		}
		return "The weekly schedule is not valid.", false
	}

	if label, ok := autoMaintenanceNextRunLabel(policy, now); ok {
		return label, true
	}
	return "The weekly schedule is not valid.", false
}

func autoMaintenanceOpenWindow(policy maintenance.AutoPolicy, now time.Time) (time.Time, time.Time, bool) {
	if maintenance.AutoTestWindowOpen(now, policy) {
		return policy.TestWindowStart.UTC(), policy.TestWindowEnd.UTC(), true
	}
	if start, end, ok := maintenance.AutoNextWindow(now, policy.Window); ok && !start.After(now) {
		return start.UTC(), end.UTC(), true
	}
	return time.Time{}, time.Time{}, false
}

func autoMaintenanceWindowUsed(status dashboardAutoStatus, policy maintenance.AutoPolicy, windowStart time.Time) bool {
	if !status.StateFound {
		return false
	}

	state := status.State
	run := state.LastRun
	if maintenance.AutoRunActive(run) || maintenance.AutoStateBlocked(state) {
		return false
	}
	if run.Phase != maintenance.AutoPhaseCompleted {
		return false
	}
	if run.WindowStart.IsZero() || !run.WindowStart.Equal(windowStart) {
		return false
	}

	return !maintenance.AutoWindowChannelPending(state, policy, windowStart)
}

func autoMaintenanceCanRunNow(status dashboardAutoStatus, policy maintenance.AutoPolicy) bool {
	if !policy.K8s.Enabled && !policy.Os.Enabled {
		return false
	}
	if !status.StateFound {
		return true
	}
	return !maintenance.AutoRunActive(status.State.LastRun) && !maintenance.AutoStateBlocked(status.State)
}

func autoMaintenanceRunNowDialog(app *stackUiApp, policy maintenance.AutoPolicy) ucx.UiNode {
	message := "Starts the enabled updates. Nodes update one at a time."
	switch {
	case policy.K8s.Enabled && policy.Os.Enabled:
		message = "Kubernetes updates first, then the operating system. Nodes update one at a time."
	case policy.K8s.Enabled:
		message = "The Kubernetes upgrade starts now. Nodes update one at a time."
	case policy.Os.Enabled:
		message = "The operating-system update starts now. Nodes update one at a time."
	}

	return dashboardResourceActionDialog(
		"autoMaintenanceRunNow",
		"Run maintenance now",
		message,
		"",
		ucx.ColorPrimaryMain,
		func() {
			app.autoRunNowConfirm = false
			ucx.AppUpdateUi(app)
		},
		func() {
			app.autoRunNowConfirm = false
			ucx.AppUpdateUi(app)
			kubeconfig := LocalKubeconfigPath()
			autoMaintenanceActionAsync(app, "Maintenance started.", func() error {
				return maintenance.AutoOpenWindowNow(context.Background(), kubeconfig)
			})
		},
	)
}

func autoMaintenanceSection(title string, children ...ucx.UiNode) ucx.UiNode {
	content := []ucx.UiNode{ucx.Text(title).Sx(ucx.SxFontSize(18), ucx.SxFontWeight("600"))}
	content = append(content, children...)
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).
		Sx(ucx.SxMinWidth(0)).Children(content...)
}

func autoMaintenancePageShell(app *stackUiApp, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(900,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
}

type autoNodeVersion struct {
	Name    string
	Version string
}

func autoMaintenanceToggle(toggle ucx.UiNode, description []ucx.UiNode) ucx.UiNode {
	children := []ucx.UiNode{toggle}
	if len(description) > 0 {
		children = append(children, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).
			Sx(ucx.SxPl(28), ucx.SxMinWidth(0)).Children(description...))
	}
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 0}).
		Sx(ucx.SxMinWidth(0)).Children(children...)
}

func autoMaintenanceUpdatesNodes(
	app *stackUiApp,
	status dashboardAutoStatus,
	policy maintenance.AutoPolicy,
	now time.Time,
) []ucx.UiNode {
	nodes := []ucx.UiNode{}
	nodes = append(nodes, autoMaintenanceToggle(
		ucx.Checkbox("autoMaintenanceK8sEnabled", "Update Kubernetes automatically", "autoMaintenanceK8sEnabled", true).
			Sx(ucx.SxMb(0)),
		autoMaintenanceKubernetesSummary(app, now),
	))

	nodes = append(nodes, autoMaintenanceToggle(
		ucx.Checkbox("autoMaintenanceOsEnabled", "Update the operating system automatically", "autoMaintenanceOsEnabled", true).
			Sx(ucx.SxMb(0)),
		autoMaintenanceOsSummary(status, policy),
	))

	return nodes
}

func autoMaintenanceNote(text string) ucx.UiNode {
	return ucx.Text(text).Sx(ucx.SxColor(ucx.ColorTextSecondary))
}

func autoMaintenanceKubernetesSummary(app *stackUiApp, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{}

	record, recordOk := app.readClusterRecord()

	entries := []autoNodeVersion{}
	if app.clusterHealth != nil {
		for name, nodeVersion := range app.clusterHealth.Versions {
			if nodeVersion != "" {
				entries = append(entries, autoNodeVersion{Name: name, Version: nodeVersion})
			}
		}
		sort.Slice(entries, func(i, j int) bool {
			return entries[i].Name < entries[j].Name
		})
	}
	if len(entries) == 0 && recordOk && record.K8sVersion != "" {
		entries = append(entries, autoNodeVersion{Version: record.K8sVersion})
	}

	if len(entries) == 0 {
		return append(nodes, autoMaintenanceNote("The node versions are not available yet."))
	}

	versions := make([]string, 0, len(entries))
	for _, entry := range entries {
		versions = append(versions, entry.Version)
	}

	target, targetOk := shared.RecommendedClusterTarget(versions, now)
	if !targetOk {
		nodes = append(nodes, ucx.Warning("No supported upgrade is available for every node."))
	} else {
		pending := autoNodesNeedingUpgrade(versions, target.Release)
		line := fmt.Sprintf("Target %s.", target.Release)
		if target.SecurityFixes {
			line += " Security release."
		}
		if pending > 0 {
			line += fmt.Sprintf(" %d of %d nodes need an upgrade.", pending, len(versions))
		} else {
			line += fmt.Sprintf(" All %s are up to date.", autoNodeCount(len(versions)))
		}
		nodes = append(nodes, autoMaintenanceNote(line))
	}

	warnings := autoUnreachableReleaseWarnings(entries, now)
	warnings = append(warnings, autoOutOfSupportWarnings(versions, now)...)
	if len(warnings) > 0 {
		nodes = append(nodes, ucx.Warning(strings.Join(warnings, " ")))
	}

	return nodes
}

func autoUnreachableReleaseWarnings(entries []autoNodeVersion, now time.Time) []string {
	releases := shared.SupportedReleases(now)
	if len(releases) == 0 {
		return nil
	}
	newest := releases[0]

	counts := map[string]int{}
	steps := map[string]string{}
	blocked := map[string]int{}
	for _, entry := range entries {
		if entry.Name == "" {
			continue
		}
		nodeTarget, ok := shared.RecommendedTarget(entry.Version, now)
		switch {
		case ok && shared.SameRelease(nodeTarget.Release, newest.Release):
		case ok:
			counts[entry.Version]++
			steps[entry.Version] = nodeTarget.Release
		default:
			blocked[entry.Version]++
		}
	}

	versions := make([]string, 0, len(counts)+len(blocked))
	for version := range counts {
		versions = append(versions, version)
	}
	for version := range blocked {
		if _, present := counts[version]; !present {
			versions = append(versions, version)
		}
	}
	sort.Strings(versions)

	result := []string{}
	for _, version := range versions {
		if count := counts[version]; count > 0 {
			result = append(result, fmt.Sprintf(
				"%s run %s and must upgrade to %s first.",
				autoNodeCount(count),
				version,
				steps[version],
			))
		}
		if count := blocked[version]; count > 0 {
			result = append(result, fmt.Sprintf("%s run %s and have no supported upgrade path.", autoNodeCount(count), version))
		}
	}
	return result
}

func autoNodeCount(count int) string {
	if count == 1 {
		return "1 node"
	}
	return fmt.Sprintf("%d nodes", count)
}

func autoMaintenanceOsSummary(status dashboardAutoStatus, policy maintenance.AutoPolicy) []ucx.UiNode {
	nodes := []ucx.UiNode{}

	reports := []maintenance.AutoUpdateReport{}
	if status.StateFound {
		reports = status.State.OsReports
	}

	if len(reports) == 0 {
		return append(nodes, autoMaintenanceNote("No update check has run yet."))
	}

	mostPending := 0
	mostSecurity := 0
	rebootNodes := 0
	failedNodes := 0
	for _, report := range reports {
		if report.Error != "" {
			failedNodes++
		}
		if report.RebootRequired {
			rebootNodes++
		}
		if report.Pending > mostPending {
			mostPending = report.Pending
			mostSecurity = report.Security
		}
	}

	line := fmt.Sprintf("All %s are up to date.", autoNodeCount(len(reports)))
	if mostPending > 0 {
		line = fmt.Sprintf("%d packages pending, %d security.", mostPending, mostSecurity)
	}
	if rebootNodes > 0 {
		if mostPending == 0 {
			line = "No packages are pending."
		}
		line += fmt.Sprintf(" %s need a restart.", autoNodeCount(rebootNodes))
	}
	nodes = append(nodes, autoMaintenanceNote(line))

	warnings := []string{}
	if failedNodes > 0 {
		warnings = append(warnings, fmt.Sprintf("The update check failed on %s.", autoNodeCount(failedNodes)))
	}
	if policy.Os.RebootPolicy == maintenance.AutoRebootAlways {
		warnings = append(warnings, "Every node reboots during its update.")
	}
	if len(warnings) > 0 {
		nodes = append(nodes, ucx.Warning(strings.Join(warnings, " ")))
	}

	return nodes
}

func autoMaintenanceNextRunLabel(policy maintenance.AutoPolicy, now time.Time) (string, bool) {
	return autoMaintenanceNextRunLabelFrom(policy, now, now)
}

func autoMaintenanceNextRunLabelFrom(policy maintenance.AutoPolicy, floor time.Time, now time.Time) (string, bool) {
	start, end, ok := autoMaintenanceNextWindow(policy, floor, now)
	if !ok {
		return "", false
	}

	endLabel := end.UTC().Format("15:04")
	if end.UTC().Format("2006-01-02") != start.UTC().Format("2006-01-02") {
		endLabel = end.UTC().Format("Monday 15:04")
	}

	return fmt.Sprintf(
		"Next run: %s - %s UTC (in %s).",
		start.UTC().Format("Monday 15:04"),
		endLabel,
		autoUntilLabel(start, now),
	), true
}

func autoMaintenanceNextWindow(policy maintenance.AutoPolicy, floor time.Time, now time.Time) (time.Time, time.Time, bool) {
	floor = floor.UTC()
	if now.After(floor) {
		floor = now.UTC()
	}
	if policy.DeferUntil.After(floor) {
		floor = policy.DeferUntil.UTC()
	}

	start, end, ok := maintenance.AutoNextWindow(floor, policy.Window)
	if !ok || !start.After(now) {
		return time.Time{}, time.Time{}, false
	}
	return start.UTC(), end.UTC(), true
}

func autoMaintenanceWindowNodes(
	app *stackUiApp,
	status dashboardAutoStatus,
	policy maintenance.AutoPolicy,
	now time.Time,
) []ucx.UiNode {
	nodes := []ucx.UiNode{
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("autoWindowDayRow", "Day", "autoWindowDay").
				FieldRowRequired(true).
				FieldRowDescription("The day when weekly maintenance can start.").
				Children(ucx.Select("autoWindowDaySelect", "", "autoWindowDay", autoDayOptions())),
			ucx.FieldRowNodeEx("autoWindowStartRow", "Update window (UTC)", "autoWindowStartHour").
				FieldRowRequired(true).
				FieldRowDescription("The four-hour period when node updates can start.").
				Children(ucx.Select("autoWindowStartSelect", "", "autoWindowStartHour", autoHourOptions())),
		),
	}

	if len(policy.Window.Days) > 1 {
		extra := []string{}
		for _, day := range policy.Window.Days {
			extra = append(extra, autoDayLabel(day))
		}
		notice := fmt.Sprintf(
			"The stored policy has several window days (%s). Changing a setting keeps only the selected day.",
			strings.Join(extra, ", "),
		)
		nodes = append(nodes, ucx.Warning(notice))
	}

	if app.autoSettingsSaveError != "" {
		nodes = append(nodes,
			ucx.Warning("Could not save settings: "+app.autoSettingsSaveError),
			ucx.Button("autoMaintenanceRetrySave", "Retry", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoSettingsSaveAsync(app)
				ucx.AppUpdateUi(app)
			}),
		)
	}

	nodes = append(nodes, autoMaintenancePauseActions(app, policy, now)...)
	return nodes
}

func autoMaintenanceRunNodes(app *stackUiApp, status dashboardAutoStatus, policy maintenance.AutoPolicy) []ucx.UiNode {
	now := time.Now().UTC()
	nodes := []ucx.UiNode{}

	blocked := status.StateFound && maintenance.AutoStateBlocked(status.State)
	active := status.StateFound && maintenance.AutoRunActive(status.State.LastRun)
	stoppable := active && status.State.LastRun.Phase != maintenance.AutoPhaseRunning

	run := status.State.LastRun

	kubernetesLine := ""
	if run.K8sRelease != "" {
		kubernetesLine = "Kubernetes " + run.K8sRelease
		if !run.K8sCompletedAt.IsZero() {
			kubernetesLine += ": completed"
		} else if run.Channel != maintenance.AutoChannelOs {
			kubernetesLine += ": " + maintenancePhaseLabel(run.Phase)
		} else {
			kubernetesLine += ": not updated"
		}
	}

	osLine := ""
	if run.Channel == maintenance.AutoChannelOs {
		osLine = "Operating system: " + maintenancePhaseLabel(run.Phase)
	}

	header := []ucx.UiNode{}
	switch {
	case kubernetesLine != "" && osLine != "":
		header = append(header, ucx.Text(kubernetesLine).Sx(ucx.SxFontWeight("600"), ucx.SxColor(ucx.ColorTextPrimary)))
		header = append(header, ucx.Text(osLine).Sx(ucx.SxFontWeight("600"), ucx.SxColor(ucx.ColorTextPrimary)))
	case osLine != "":
		header = append(header, ucx.Text(osLine).Sx(ucx.SxFontWeight("600"), ucx.SxColor(ucx.ColorTextPrimary)))
	case kubernetesLine != "":
		header = append(header, ucx.Text(kubernetesLine).Sx(ucx.SxFontWeight("600"), ucx.SxColor(ucx.ColorTextPrimary)))
	default:
		header = append(header, ucx.Text(maintenancePhaseLabel(run.Phase)).Sx(ucx.SxFontWeight("600"), ucx.SxColor(ucx.ColorTextPrimary)))
	}

	timestamps := []ucx.UiNode{}
	if !run.StartedAt.IsZero() {
		timestamps = append(timestamps, ucx.Text("Started "+run.StartedAt.UTC().Format("Jan 2, 15:04 UTC")).
			Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWhiteSpace("nowrap")))
	}
	if !run.FinishedAt.IsZero() {
		format := "Jan 2, 15:04 UTC"
		if run.StartedAt.UTC().Format("2006-01-02") == run.FinishedAt.UTC().Format("2006-01-02") {
			format = "15:04 UTC"
		}
		timestamps = append(timestamps, ucx.Text("Finished "+run.FinishedAt.UTC().Format(format)).
			Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWhiteSpace("nowrap")))
	}
	activityNow := now
	if !run.FinishedAt.IsZero() {
		activityNow = run.FinishedAt
	}
	if activity, ok := maintenanceActivityLabel(run.StartedAt, activityNow); ok {
		timestamps = append(timestamps, ucx.Text(activity).
			Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWhiteSpace("nowrap")))
	}
	if len(timestamps) > 0 {
		header = append(header, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 24}).Sx(ucx.SxFlexWrapWrap).Children(timestamps...))
	}
	nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Sx(ucx.SxMinWidth(0)).Children(header...))

	notices := []ucx.UiNode{}
	if blocked {
		notices = append(notices, ucx.Warning(run.Error))
	}

	for _, check := range run.Checks {
		if check.Ok || check.Detail == "" || blocked {
			continue
		}
		notices = append(notices, ucx.Text(autoCheckLabel(check.Name)+": "+check.Detail).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	}
	if active && run.Trigger == maintenance.AutoTriggerWindow {
		paused := !maintenance.AutoScheduleOpen(now, policy)
		manualWindow := maintenance.AutoTestWindowOpen(now, policy)
		if !manualWindow && ((run.Channel == maintenance.AutoChannelOs && !policy.Os.Enabled) ||
			(run.Channel != maintenance.AutoChannelOs && !policy.K8s.Enabled)) {
			paused = true
		}
		if paused {
			notices = append(notices, ucx.Text(autoSchedulePausedLabel(run.Channel, policy, now)).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}
	}
	if len(notices) > 0 {
		nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Sx(ucx.SxMinWidth(0)).Children(notices...))
	}

	nodes = append(nodes, autoMaintenanceNodeProgress(app, status)...)

	actions := []ucx.UiNode{}
	recovery := app.autoOsRecoveryButtons(blocked)
	actions = append(actions, recovery...)
	if blocked {
		if len(recovery) == 0 {
			actions = append(actions, ucx.Button("autoMaintenanceResume", "Resume automatic maintenance", ucx.ColorPrimaryMain).
				ButtonBusy("autoMaintenanceBusy").
				WithTooltip("Allows the schedule to start new runs. A new run starts now if the maintenance window is open.").
				On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					autoMaintenanceActionAsync(app, "Automatic maintenance resumed.", maintenance.AutoResume)
				}))
		}
	} else if stoppable {
		actions = append(actions, ucx.Button("autoMaintenanceStop", "Stop run", ucx.ColorErrorMain).
			ButtonBusy("autoMaintenanceBusy").
			WithTooltip("Stops the current run before it touches any node. The schedule stays stopped until you resume.").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoMaintenanceActionAsync(app, "The automatic run was stopped.", maintenance.AutoStopRun)
			}))
	}

	if len(actions) > 0 {
		nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).Sx(ucx.SxFlexWrapWrap, ucx.SxMt(8)).Children(actions...))
	}

	return nodes
}

func (app *stackUiApp) autoOsRecoveryButtons(blocked bool) []ucx.UiNode {
	nodes, err := maintenance.AutoOsRecoveryNodes()
	if err != nil {
		return nil
	}

	buttons := []ucx.UiNode{}
	for _, nodeName := range nodes {
		name := nodeName
		label := "Retry OS update on " + name
		tooltip := "Retries the failed operating-system update on the node. The update runs with the same safety rules."
		success := "The operating-system update was retried on " + name + "."
		if blocked {
			label = "Retry and resume maintenance"
			if len(nodes) > 1 {
				label = "Retry and resume maintenance on " + name
			}
			tooltip = "Retries the failed operating-system update on " + name + ", then lets the schedule continue the maintenance."
			success = "The operating-system update was retried on " + name + " and automatic maintenance resumed."
		}

		buttons = append(buttons, ucx.Button("autoMaintenanceOsRetry-"+name, label, ucx.ColorWarningMain).
			ButtonBusy("autoMaintenanceBusy").
			WithTooltip(tooltip).
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				kubeconfig := LocalKubeconfigPath()
				autoMaintenanceActionAsync(app, success, func() error {
					if err := maintenance.AutoRetryOsUpdate(context.Background(), kubeconfig, name); err != nil {
						return err
					}
					if !blocked {
						return nil
					}
					return maintenance.AutoResume()
				})
			}))
	}

	return buttons
}

func autoSchedulePausedLabel(channel string, policy maintenance.AutoPolicy, now time.Time) string {
	if channel == maintenance.AutoChannelOs && !policy.Os.Enabled {
		return "Automatic operating-system updates are disabled. Enable them to continue scheduled node updates."
	}
	if channel != maintenance.AutoChannelOs && !policy.K8s.Enabled {
		return "Automatic Kubernetes upgrades are disabled. Enable them to continue scheduled node upgrades."
	}
	if policy.DeferUntil.After(now) {
		return "The schedule is deferred until " + autoDateLabel(policy.DeferUntil) + ". The run continues in the next window after the deferral."
	}

	start, _, ok := maintenance.AutoEffectiveWindow(now, policy)
	if !ok {
		return "The window is closed. The run continues in the next window."
	}

	return "The window is closed. The run continues in the next window, " + start.UTC().Format("Monday 15:04") + " UTC."
}

func autoCheckLabel(name string) string {
	switch name {
	case maintenance.AutoCheckCluster:
		return "Cluster"
	case maintenance.AutoCheckRecovery:
		return "Recovery"
	case maintenance.AutoCheckOperations:
		return "Maintenance state"
	case maintenance.AutoCheckNodes:
		return "Nodes"
	case maintenance.AutoCheckQuorum:
		return "Quorum"
	case maintenance.AutoCheckAgents:
		return "Node agents"
	case maintenance.AutoCheckVersions:
		return "Version spread"
	case maintenance.AutoCheckPath:
		return "Upgrade path"
	case maintenance.AutoCheckRolling:
		return "Rolling upgrade"
	case maintenance.AutoCheckBackup:
		return "Backup"
	case maintenance.AutoCheckOs:
		return "OS updates"
	}
	return name
}

func autoMaintenanceActionAsync(app *stackUiApp, success string, action func() error) {
	if app.AutoMaintenanceBusy {
		return
	}

	session := *app.Session()
	if session == nil {
		return
	}

	app.AutoMaintenanceBusy = true
	ucx.AppUpdateModelPatch(session, map[string]ucx.Value{
		"autoMaintenanceBusy": ucx.VBool(true),
	})

	go func() {
		err := action()

		app.mu.Lock()
		app.AutoMaintenanceBusy = false
		updatedUi := app.UserInterface()
		updatedModel := ucx.AppSnapshot(app)
		app.mu.Unlock()

		if session.Context().Err() == nil {
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
			if err != nil {
				maintenanceSendUiMessage(session, err.Error(), false)
			} else {
				maintenanceSendUiMessage(session, success, true)
			}
		}
	}()
}

func autoMaintenanceInitForm(app *stackUiApp, policy maintenance.AutoPolicy) {
	if app.autoMaintenanceLoaded || app.autoSettingsSaving || app.autoSettingsSaveError != "" {
		return
	}

	days := policy.Window.Days
	if len(days) == 0 {
		days = []string{"sun"}
	}

	app.AutoMaintenanceK8sEnabled = policy.K8s.Enabled
	app.AutoMaintenanceOsEnabled = policy.Os.Enabled
	app.AutoWindowDay = strings.ToLower(strings.TrimSpace(days[0]))
	app.AutoWindowStartHour = strconv.Itoa(policy.Window.StartHourUtc)
	app.autoMaintenanceLoaded = true
}

func (app *stackUiApp) homeAutoMaintenanceMetric() []ucx.UiNode {
	value := "Unknown"
	color := ucx.ColorTextSecondary

	status, err := app.autoStatusRead()
	if err == nil {
		policy := maintenance.AutoDefaultPolicy()
		if status.PolicyFound {
			policy = maintenance.AutoPolicyNormalize(status.Policy)
		}

		now := time.Now().UTC()
		switch {
		case status.StateFound && maintenance.AutoRunActive(status.State.LastRun):
			value = "Maintenance in progress"
			color = ucx.ColorTextPrimary
		case !policy.K8s.Enabled && !policy.Os.Enabled:
			value = "Off"
		case status.StateFound && maintenance.AutoStateBlocked(status.State):
			value = "Blocked"
			color = ucx.ColorErrorMain
		case policy.DeferUntil.After(now) && !maintenance.AutoTestWindowOpen(now, policy):
			value = "Deferred until " + autoDateLabel(policy.DeferUntil)
			color = ucx.ColorWarningMain
		default:
			windowStart, windowEnd, windowOpen := autoMaintenanceOpenWindow(policy, now)
			floor := now
			if windowOpen {
				floor = windowEnd
				if !autoMaintenanceWindowUsed(status, policy, windowStart) {
					value = "Open until " + windowEnd.UTC().Format("Mon 15:04")
					color = ucx.ColorTextPrimary
					break
				}
			}
			if start, _, ok := autoMaintenanceNextWindow(policy, floor, now); ok {
				value = start.UTC().Format("Mon 15:04")
				color = ucx.ColorTextPrimary
			}
		}
	}

	link := ucx.LinkButton("homeAutomaticMaintenance", "(View)", ucx.ColorLinkColor).Sx(
		ucx.SxFontSize(12),
		ucx.SxFontWeight("400"),
	).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		ucxsvc.RouterPushPage(app, "automatic-maintenance")
	})

	return app.homeMetricEx("Automatic maintenance", []ucx.UiNode{link}, homeMetricText(value, color))
}

var autoDayLabels = []struct {
	Key   string
	Value string
}{
	{Key: "mon", Value: "Monday"},
	{Key: "tue", Value: "Tuesday"},
	{Key: "wed", Value: "Wednesday"},
	{Key: "thu", Value: "Thursday"},
	{Key: "fri", Value: "Friday"},
	{Key: "sat", Value: "Saturday"},
	{Key: "sun", Value: "Sunday"},
}

func autoDayLabel(day string) string {
	day = strings.ToLower(strings.TrimSpace(day))
	for _, candidate := range autoDayLabels {
		if candidate.Key == day {
			return candidate.Value
		}
	}
	return day
}

func autoDayOptions() []ucx.Option {
	options := make([]ucx.Option, 0, len(autoDayLabels))
	for _, day := range autoDayLabels {
		options = append(options, ucx.Option{Key: day.Key, Value: day.Value})
	}
	return options
}

func autoHourOptions() []ucx.Option {
	options := make([]ucx.Option, 0, 24)
	for hour := 0; hour < 24; hour++ {
		endHour := (hour + maintenance.AutoWindowDurationHours) % 24
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

func autoNodesNeedingUpgrade(versions []string, target string) int {
	count := 0
	for _, nodeVersion := range versions {
		if !shared.SameRelease(nodeVersion, target) {
			count++
		}
	}
	return count
}

func autoOutOfSupportWarnings(versions []string, now time.Time) []string {
	seen := map[string]bool{}
	result := []string{}

	for _, nodeVersion := range versions {
		if seen[nodeVersion] {
			continue
		}
		seen[nodeVersion] = true

		eolAt, ok := shared.SupportEnds(nodeVersion)
		if ok && !eolAt.After(now) {
			result = append(result, fmt.Sprintf("%s is past its end of support.", nodeVersion))
		}
	}

	return result
}

func autoTimestampLabel(t time.Time) string {
	return t.UTC().Format("Mon Jan 2, 15:04 UTC")
}

func autoDateLabel(t time.Time) string {
	return t.UTC().Format("Jan 2, 2006")
}

func autoUntilLabel(t time.Time, now time.Time) string {
	delta := t.Sub(now)
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
