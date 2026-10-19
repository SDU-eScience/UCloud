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

const (
	autoDeferKeep  = "keep"
	autoDeferClear = "clear"
	autoDeferDay   = "24h"
	autoDeferThree = "72h"
	autoDeferWeek  = "168h"
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
	Duration   string
	Defer      string
}

func autoSettingsFromModel(app *stackUiApp) autoSettingsForm {
	return autoSettingsForm{
		K8sEnabled: app.AutoMaintenanceK8sEnabled,
		OsEnabled:  app.AutoMaintenanceOsEnabled,
		WindowDay:  app.AutoWindowDay,
		StartHour:  app.AutoWindowStartHour,
		Duration:   app.AutoWindowDuration,
		Defer:      app.AutoDefer,
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

	duration, durationErr := strconv.Atoi(strings.TrimSpace(form.Duration))
	if durationErr == nil && duration >= 1 && duration <= 12 {
		policy.Window.DurationHours = duration
	}

	now := time.Now().UTC()
	switch form.Defer {
	case autoDeferClear:
		policy.DeferUntil = time.Time{}
	case autoDeferDay:
		policy.DeferUntil = now.Add(24 * time.Hour)
	case autoDeferThree:
		policy.DeferUntil = now.Add(72 * time.Hour)
	case autoDeferWeek:
		policy.DeferUntil = now.Add(168 * time.Hour)
	}

	return maintenance.AutoPolicyNormalize(policy)
}

func autoSettingsSubmitAsync(app *stackUiApp, client shared.ClusterStateClient, form autoSettingsForm) {
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
		err := autoPolicySave(client, form)

		app.mu.Lock()
		app.AutoMaintenanceBusy = false
		if app.AutoDefer == form.Defer && form.Defer != autoDeferKeep {
			app.AutoDefer = autoDeferKeep
		}
		updatedUi := app.UserInterface()
		updatedModel := ucx.AppSnapshot(app)
		app.mu.Unlock()

		if session.Context().Err() == nil {
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
			if err != nil {
				maintenanceSendUiMessage(session, err.Error(), false)
			} else {
				maintenanceSendUiMessage(session, "The automatic maintenance settings were saved.", true)
			}
		}
	}()
}

func autoMaintenancePage(app *stackUiApp) []ucx.UiNode {
	now := time.Now().UTC()

	content := []ucx.UiNode{
		ucx.Text("Automatic maintenance").Sx(ucx.SxFontSize(20), ucx.SxFontWeight("600")),
		ucx.Text("The service upgrades Kubernetes and the operating system inside the maintenance window. Manual actions stay available and are never stopped by the schedule.").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
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

	autoMaintenanceInitForm(app, policy)

	if !status.PolicyFound {
		content = append(content, ucx.Text("Automatic maintenance is off until you enable it and save the settings.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	}

	formChildren := []ucx.UiNode{}
	formChildren = append(formChildren, autoMaintenanceKubernetesNodes(app, now)...)
	formChildren = append(formChildren, autoMaintenanceOsNodes(status, now)...)
	formChildren = append(formChildren, autoMaintenanceWindowNodes(policy, now)...)
	formChildren = append(formChildren, autoMaintenanceSubmitNodes()...)

	content = append(content, ucx.Form("autoMaintenanceForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.AutoMaintenanceBusy {
			return
		}

		client, clientErr := app.clusterStateValueClient()
		if clientErr != nil {
			maintenanceSendUiMessage(*app.Session(), clientErr.Error(), false)
			return
		}

		autoSettingsSubmitAsync(app, client, autoSettingsFromModel(app))
	}).Children(formChildren...))

	content = append(content, autoMaintenanceRunNodes(app, status, policy)...)

	return autoMaintenancePageShell(app, content)
}

func autoMaintenancePageShell(app *stackUiApp, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(900,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
}

type autoNodeVersion struct {
	Name    string
	Version string
}

func autoMaintenanceKubernetesNodes(app *stackUiApp, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{
		ucx.Text("Kubernetes upgrades").Sx(ucx.SxFontSize(16), ucx.SxFontWeight("600")),
		ucx.Checkbox("autoMaintenanceK8sEnabled", "Upgrade Kubernetes in the maintenance window. The target is the newest supported release that every node can reach.", "autoMaintenanceK8sEnabled", true),
		ucx.Text("Disabling pauses scheduled node upgrades. Running nodes finish, and manual runs continue.").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
	}

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
		nodes = append(nodes, ucx.Text("The node versions are not available yet.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		return nodes
	}

	versions := make([]string, 0, len(entries))
	for _, entry := range entries {
		versions = append(versions, entry.Version)
	}

	target, targetOk := shared.RecommendedClusterTarget(versions, now)
	if !targetOk {
		nodes = append(nodes, ucx.Warning("No supported upgrade is available for every node. A node may be more than one minor behind, or every release may be past its end of support."))
	} else {
		label := fmt.Sprintf(
			"Target %s · %d of %d nodes need an upgrade.",
			shared.ReleaseSupportLabel(target, now),
			autoNodesNeedingUpgrade(versions, target.Release),
			len(versions),
		)
		nodes = append(nodes, ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)))

		if target.SecurityFixes {
			nodes = append(nodes, ucx.Text("The target release carries security fixes.").Sx(ucx.SxColor(ucx.ColorWarningMain)))
		}
	}

	newest, newestOk := shared.K3sRelease{}, false
	if releases := shared.SupportedReleases(now); len(releases) > 0 {
		newest, newestOk = releases[0], true
	}

	for _, entry := range entries {
		if entry.Name == "" || !newestOk {
			continue
		}

		nodeTarget, nodeOk := shared.RecommendedTarget(entry.Version, now)
		switch {
		case nodeOk && shared.SameRelease(nodeTarget.Release, newest.Release):
			continue
		case nodeOk:
			nodes = append(nodes, ucx.Warning(fmt.Sprintf("Node %s runs %s and cannot reach %s. Its next supported step is %s.", entry.Name, entry.Version, newest.Release, nodeTarget.Release)))
		default:
			nodes = append(nodes, ucx.Warning(fmt.Sprintf("Node %s runs %s and has no supported upgrade path.", entry.Name, entry.Version)))
		}
	}

	for _, warning := range autoOutOfSupportWarnings(versions, now) {
		nodes = append(nodes, ucx.Warning(warning))
	}

	return nodes
}

func autoMaintenanceOsNodes(status dashboardAutoStatus, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{
		ucx.Text("Operating system updates").Sx(ucx.SxFontSize(16), ucx.SxFontWeight("600")),
		ucx.Checkbox("autoMaintenanceOsEnabled", "Apply operating-system updates in the maintenance window. A node reboots only when the update requires it.", "autoMaintenanceOsEnabled", true),
	}

	reports := []maintenance.AutoUpdateReport{}
	if status.StateFound {
		reports = status.State.OsReports
	}

	if len(reports) == 0 {
		nodes = append(nodes, ucx.Text("No update check has run yet. The coordinator asks each node before the next window.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		return nodes
	}

	pending := 0
	security := 0
	pendingNodes := 0
	rebootNodes := 0
	failedNodes := 0
	lastChecked := time.Time{}
	for _, report := range reports {
		if report.Error != "" {
			failedNodes++
		}
		if report.Pending > 0 {
			pendingNodes++
		}
		if report.RebootRequired {
			rebootNodes++
		}
		pending += report.Pending
		security += report.Security
		if report.CheckedAt.After(lastChecked) {
			lastChecked = report.CheckedAt
		}
	}

	nodes = append(nodes, ucx.Text(fmt.Sprintf("Pending: %d packages on %d nodes, %d of them security.", pending, pendingNodes, security)).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	if rebootNodes > 0 {
		nodes = append(nodes, ucx.Text(fmt.Sprintf("Reboot required on %d nodes.", rebootNodes)).Sx(ucx.SxColor(ucx.ColorWarningMain)))
	}
	if failedNodes > 0 {
		nodes = append(nodes, ucx.Warning(fmt.Sprintf("The update check failed on %d nodes.", failedNodes)))
	}
	if !lastChecked.IsZero() {
		nodes = append(nodes, ucx.Text("Last check "+autoAgeLabel(lastChecked, now)+".").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	}

	for _, report := range reports {
		line := report.NodeName + " · up to date"
		if report.Error != "" {
			line = report.NodeName + " · check failed: " + report.Error
		} else if report.Pending > 0 {
			line = fmt.Sprintf("%s · %d pending, %d security", report.NodeName, report.Pending, report.Security)
		}
		if report.RebootRequired {
			line += " · reboot required"
		}
		nodes = append(nodes, ucx.Text(line).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	}

	return nodes
}

func autoMaintenanceWindowNodes(policy maintenance.AutoPolicy, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{
		ucx.Text("Maintenance window").Sx(ucx.SxFontSize(16), ucx.SxFontWeight("600")),
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("autoWindowDayRow", "Day", "autoWindowDay").
				FieldRowRequired(true).
				Children(ucx.Select("autoWindowDaySelect", "", "autoWindowDay", autoDayOptions())),
			ucx.FieldRowNodeEx("autoWindowStartRow", "Start (UTC)", "autoWindowStartHour").
				FieldRowRequired(true).
				Children(ucx.Select("autoWindowStartSelect", "", "autoWindowStartHour", autoHourOptions())),
			ucx.FieldRowNodeEx("autoWindowDurationRow", "Duration", "autoWindowDuration").
				FieldRowRequired(true).
				Children(ucx.Select("autoWindowDurationSelect", "", "autoWindowDuration", autoDurationOptions())),
			ucx.FieldRowNodeEx("autoDeferRow", "Deferral", "autoDefer").
				FieldRowDescription("Suppresses automatic runs until the deferral passes. Manual actions stay available.").
				Children(ucx.Select("autoDeferSelect", "", "autoDefer", autoDeferOptions())),
		),
	}

	if len(policy.Window.Days) > 1 {
		extra := []string{}
		for _, day := range policy.Window.Days {
			extra = append(extra, autoDayLabel(day))
		}
		notice := fmt.Sprintf(
			"The stored policy has several window days (%s). This page edits one day; saving keeps the selected day only.",
			strings.Join(extra, ", "),
		)
		nodes = append(nodes, ucx.Warning(notice))
	}

	if policy.DeferUntil.After(now) {
		nodes = append(nodes, ucx.Text("Deferred until "+autoTimestampLabel(policy.DeferUntil)+".").Sx(ucx.SxColor(ucx.ColorWarningMain)))
	}

	start, end, ok := maintenance.AutoEffectiveWindow(now, policy)
	if ok && start.After(now) {
		endLabel := end.UTC().Format("15:04")
		if end.UTC().Format("2006-01-02") != start.UTC().Format("2006-01-02") {
			endLabel = end.UTC().Format("Monday 15:04")
		}
		label := fmt.Sprintf(
			"Next run: %s → %s UTC (in %s).",
			start.UTC().Format("Monday 15:04"),
			endLabel,
			autoUntilLabel(start, now),
		)
		nodes = append(nodes, ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	} else if ok {
		nodes = append(nodes, ucx.Text("The maintenance window is open until "+end.UTC().Format("Monday 15:04")+" UTC.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	} else {
		nodes = append(nodes, ucx.Warning("The maintenance window is not valid."))
	}

	return nodes
}

func autoMaintenanceSubmitNodes() []ucx.UiNode {
	return []ucx.UiNode{
		ucx.Tip("A node that is already running finishes even when the window ends. The next node starts only while the window is open."),
		ucx.SubmitButton("autoMaintenanceSubmit", "Save settings", ucx.ColorPrimaryMain).
			ButtonBusy("autoMaintenanceBusy").
			Sx(ucx.SxJustifyEnd),
	}
}

func autoMaintenanceRunNodes(app *stackUiApp, status dashboardAutoStatus, policy maintenance.AutoPolicy) []ucx.UiNode {
	now := time.Now().UTC()
	nodes := []ucx.UiNode{
		ucx.Text("Last automatic run").Sx(ucx.SxFontSize(16), ucx.SxFontWeight("600")),
	}

	blocked := status.StateFound && maintenance.AutoStateBlocked(status.State)
	active := status.StateFound && maintenance.AutoRunActive(status.State.LastRun)
	stoppable := active && status.State.LastRun.Phase != maintenance.AutoPhaseRunning

	if !status.StateFound || status.State.LastRun.Phase == "" {
		nodes = append(nodes, ucx.Text("No automatic run has started yet.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
	} else {
		run := status.State.LastRun
		channel := "Kubernetes"
		if run.Channel == maintenance.AutoChannelOs {
			channel = "Operating system"
		}

		line := fmt.Sprintf("%s · %s", channel, run.Phase)
		if run.TargetRelease != "" {
			line += " · target " + run.TargetRelease
		}
		if run.Trigger != "" {
			line += " · " + autoTriggerLabel(run.Trigger)
		}
		if label := autoRunWindowLabel(run); label != "" {
			line += " · " + label
		}
		if !run.FinishedAt.IsZero() {
			line += " · finished " + autoTimestampLabel(run.FinishedAt)
		} else if !run.StartedAt.IsZero() {
			line += " · started " + autoTimestampLabel(run.StartedAt)
		}
		if run.Phase == maintenance.AutoPhaseBlocked && !blocked {
			line += " · resolved"
		}
		nodes = append(nodes, ucx.Text(line).Sx(ucx.SxColor(ucx.ColorTextSecondary)))

		if blocked {
			nodes = append(nodes, ucx.Warning("Automatic maintenance is stopped: "+run.Error))
			nodes = append(nodes, ucx.Text("Resolve the problem, then press Resume automatic maintenance. The next window starts a fresh run.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}

		if active {
			for _, check := range run.Checks {
				if check.Ok || check.Detail == "" {
					continue
				}
				nodes = append(nodes, ucx.Text(autoCheckLabel(check.Name)+" · "+check.Detail).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
			}
			if run.Trigger == maintenance.AutoTriggerWindow && (!policy.K8s.Enabled || !maintenance.AutoScheduleOpen(now, policy)) {
				nodes = append(nodes, ucx.Text(autoSchedulePausedLabel(policy, now)).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
			}
		}
	}

	actions := []ucx.UiNode{}
	if blocked {
		actions = append(actions, ucx.Button("autoMaintenanceResume", "Resume automatic maintenance", ucx.ColorPrimaryMain).
			ButtonBusy("autoMaintenanceBusy").
			WithTooltip("Allows the schedule to start new runs. The blocked run keeps its recorded reason.").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoMaintenanceActionAsync(app, "Automatic maintenance resumed.", maintenance.AutoResume)
			}))
	} else if stoppable {
		actions = append(actions, ucx.Button("autoMaintenanceStop", "Stop run", ucx.ColorErrorMain).
			ButtonBusy("autoMaintenanceBusy").
			WithTooltip("Stops the current run before it touches any node. The schedule stays stopped until you resume.").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoMaintenanceActionAsync(app, "The automatic run was stopped.", maintenance.AutoStopRun)
			}))
	} else if !active && policy.K8s.Enabled {
		actions = append(actions, ucx.Button("autoMaintenanceRunNow", "Run now", ucx.ColorSecondaryMain).
			ButtonBusy("autoMaintenanceBusy").
			WithTooltip("Starts an automatic run outside the maintenance window. The gates and the fresh-backup rule still apply.").
			On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				kubeconfig := LocalKubeconfigPath()
				autoMaintenanceActionAsync(app, "The automatic run started.", func() error {
					return maintenance.AutoRunNow(context.Background(), kubeconfig)
				})
			}))
	}

	if len(actions) > 0 {
		nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Children(actions...))
	}

	return nodes
}

func autoTriggerLabel(trigger string) string {
	if trigger == maintenance.AutoTriggerManual {
		return "started manually"
	}
	return "started by the window"
}

func autoRunWindowLabel(run maintenance.AutoRun) string {
	if run.WindowStart.IsZero() {
		return ""
	}

	start := run.WindowStart.UTC()
	end := run.WindowEnd.UTC()
	endLabel := end.Format("15:04")
	if end.Format("2006-01-02") != start.Format("2006-01-02") {
		endLabel = end.Format("Mon 15:04")
	}

	return fmt.Sprintf("window %s %s–%s UTC", start.Format("Mon"), start.Format("15:04"), endLabel)
}

func autoSchedulePausedLabel(policy maintenance.AutoPolicy, now time.Time) string {
	if !policy.K8s.Enabled {
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
	if app.autoMaintenanceLoaded {
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
	app.AutoWindowDuration = strconv.Itoa(policy.Window.DurationHours)
	app.AutoDefer = autoDeferKeep
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
		case !policy.K8s.Enabled && !policy.Os.Enabled:
			value = "Off"
		case status.StateFound && maintenance.AutoStateBlocked(status.State):
			value = "Blocked"
			color = ucx.ColorErrorMain
		case policy.DeferUntil.After(now):
			value = "Deferred until " + autoDateLabel(policy.DeferUntil)
			color = ucx.ColorWarningMain
		default:
			if start, end, ok := maintenance.AutoEffectiveWindow(now, policy); ok {
				if start.After(now) {
					value = start.UTC().Format("Mon 15:04")
				} else {
					value = "Open until " + end.UTC().Format("Mon 15:04")
				}
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
		options = append(options, ucx.Option{
			Key:   strconv.Itoa(hour),
			Value: fmt.Sprintf("%02d:00", hour),
		})
	}
	return options
}

func autoDurationOptions() []ucx.Option {
	options := make([]ucx.Option, 0, 12)
	for hour := 1; hour <= 12; hour++ {
		value := fmt.Sprintf("%d hours", hour)
		if hour == 1 {
			value = "1 hour"
		}
		options = append(options, ucx.Option{
			Key:   strconv.Itoa(hour),
			Value: value,
		})
	}
	return options
}

func autoDeferOptions() []ucx.Option {
	return []ucx.Option{
		{Key: autoDeferKeep, Value: "Keep the current deferral"},
		{Key: autoDeferClear, Value: "Clear the deferral"},
		{Key: autoDeferDay, Value: "Defer for 1 day"},
		{Key: autoDeferThree, Value: "Defer for 3 days"},
		{Key: autoDeferWeek, Value: "Defer for 1 week"},
	}
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
			result = append(result, fmt.Sprintf("The running version %s is past its end of support (%s).", nodeVersion, autoDateLabel(eolAt)))
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

func autoAgeLabel(t time.Time, now time.Time) string {
	delta := now.Sub(t)
	if delta < time.Minute {
		return "just now"
	}
	if delta < time.Hour {
		return fmt.Sprintf("%d min ago", int(delta/time.Minute))
	}
	if delta < 24*time.Hour {
		return fmt.Sprintf("%d h ago", int(delta/time.Hour))
	}
	days := int(delta / (24 * time.Hour))
	if days == 1 {
		return "1 day ago"
	}
	return fmt.Sprintf("%d days ago", days)
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
