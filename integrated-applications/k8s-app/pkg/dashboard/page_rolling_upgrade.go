package dashboard

import (
	"context"
	"fmt"
	"net/url"
	"sort"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

type rollingUpgradeGroup struct {
	Name        string `ucx:"-"`
	Count       int    `ucx:"-"`
	Preparation string
}

func rollingUpgradePage(app *stackUiApp) []ucx.UiNode {
	content := []ucx.UiNode{
		ucx.Text("Rolling upgrade").Sx(ucx.SxFontSize(20), ucx.SxFontWeight("600")),
		ucx.Text("The cluster will upgrade in a rolling fashion. Control plane nodes first, then worker groups in parallel. One node at a time per group.").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxAlignItemsCenter).Children(
			ucx.Text("Upgrades can also run automatically in the maintenance window.").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
			ucx.LinkButton("rollingUpgradeAutoMaintenance", "Automatic maintenance", ucx.ColorLinkColor).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.RouterPushPage(app, "automatic-maintenance")
			}),
		),
	}
	upgrade, err := maintenance.RollingUpgradeSnapshot()
	if err != nil {
		content = append(content, ucx.Warning(fmt.Sprintf("Could not read the rolling upgrade: %s", err)))
		return pageShell(app, 800, 16, content)
	}
	if upgrade.Uid != "" {
		status := fmt.Sprintf("%s: %s", upgrade.TargetRelease, upgrade.Phase)
		if maintenance.PhaseActive(upgrade.Phase) {
			status = "Upgrading to " + upgrade.TargetRelease
		}
		content = append(content, ucx.Text(status))
		if upgrade.Trigger == maintenance.AutoTriggerWindow {
			label := "Started automatically in the maintenance window."
			if upgrade.AutoRunUid != "" {
				label += " Run " + upgrade.AutoRunUid + "."
			}
			content = append(content, ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}
		if upgrade.Error != "" {
			content = append(content, ucx.Warning(upgrade.Error))
			content = append(content, ucx.Text("Recover failed nodes before restarting. Upgrades already running will finish.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}
		snapshot, snapshotErr := maintenance.Snapshot()
		if snapshotErr != nil {
			content = append(content, ucx.Warning(fmt.Sprintf("Could not read node progress: %s", snapshotErr)))
			return pageShell(app, 800, 16, content)
		}
		content = append(content, rollingUpgradeProgress(app, upgrade, snapshot))
		if maintenance.PhaseActive(upgrade.Phase) {
			content = append(content, ucx.Text("You can leave this page. The upgrade will continue.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
			return pageShell(app, 800, 16, content)
		}
	}
	record, found := app.readClusterRecord()
	if !found || record.Phase != "created" || len(record.Nodes) == 0 {
		content = append(content, ucx.Warning("The cluster is not ready to upgrade."))
		return pageShell(app, 800, 16, content)
	}
	options := rollingUpgradeReleaseOptions(app, record)
	if len(options) == 0 {
		content = append(content, ucx.Text("No supported upgrade is available for all nodes."))
		return pageShell(app, 800, 16, content)
	}
	selected := false
	for _, option := range options {
		if option.Key == app.RollingUpgradeTargetRelease {
			selected = true
		}
	}
	if !selected {
		app.RollingUpgradeTargetRelease = options[0].Key
	}
	rollingUpgradeSyncGroups(app, record)
	form := []ucx.UiNode{
		ucx.FieldRowNodeEx("rollingUpgradeReleaseRow", "Target release", "rollingUpgradeTargetRelease").
			FieldRowRequired(true).
			Children(ucx.Select("rollingUpgradeRelease", "", "rollingUpgradeTargetRelease", options)),
		ucx.Tip("Neither cordon nor drain is usually needed. Nodes cordoned by the upgrade are uncordoned after success. Previously cordoned nodes stay cordoned. Use only when you have special requirements."),
	}
	preparationOptions := []ucx.Option{
		{
			Key:   "neither",
			Value: "Neither (recommended)",
		},
		{
			Key:   "cordon",
			Value: "Cordon",
		},
		{
			Key:   "drain",
			Value: "Cordon + drain",
		},
	}
	groupFields := []ucx.UiNode{}
	for i, group := range app.RollingUpgradeGroups {
		binding := fmt.Sprintf("rollingUpgradeGroups.%d.preparation", i)
		groupFields = append(groupFields, ucx.FieldRowNodeEx(fmt.Sprintf("rollingUpgradeGroup%d", i), fmt.Sprintf("%s (%d nodes)", group.Name, group.Count), binding).
			Children(ucx.Select(fmt.Sprintf("rollingUpgradePreparation%d", i), "", binding, preparationOptions)))
		if group.Name == shared.GroupControlPlane && group.Count == 1 {
			groupFields = append(groupFields, ucx.Warning("Upgrading the only control-plane node causes control-plane downtime."))
		}
	}
	form = append(form,
		ucx.FieldGroupNode().Children(groupFields...),
		ucx.Text("Cordon stops new scheduling. Drain also evicts pods, respecting disruption budgets, with a 10-minute timeout. Pods with volatile data or no controller block draining.").Sx(ucx.SxFontSize(12), ucx.SxColor(ucx.ColorTextSecondary)),
		ucx.SubmitButton("rollingUpgradeSubmit", "Start rolling upgrade", ucx.ColorSuccessMain).
			ButtonBusy("rollingUpgradeBusy").
			ButtonHoldToConfirm(true).
			Sx(ucx.SxJustifyEnd),
	)
	content = append(content, ucx.Form("rollingUpgradeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		rollingUpgradeSubmit(app, options)
	}).Children(form...))
	return pageShell(app, 800, 16, content)
}

func rollingUpgradeProgress(app *stackUiApp, upgrade maintenance.RollingUpgrade, snapshot map[string]maintenance.Operation) ucx.UiNode {
	jobIds := map[string]string{}
	if record, found := app.readClusterRecord(); found {
		for _, node := range record.Nodes {
			jobIds[node.Hostname] = node.JobId
		}
	}

	now := time.Now().UTC()
	rows := map[string][]ucx.UiNode{}
	completed := map[string]int{}
	groupNames := []string{}
	for i, node := range upgrade.Nodes {
		if _, present := rows[node.Group]; !present {
			groupNames = append(groupNames, node.Group)
		}

		phase := node.Phase
		operation := maintenance.Operation{}
		submitted := false
		if candidate, present := snapshot[node.Name]; present && candidate.Uid == node.OperationUid {
			operation = candidate
			phase = candidate.Phase
			submitted = true
		}
		active := submitted && maintenance.PhaseActive(phase)

		label := maintenancePhaseLabel(phase)
		color := ucx.ColorTextSecondary
		switch {
		case active:
			label = ""
			color = ucx.ColorTextPrimary
		case phase == maintenance.PhaseCompleted:
			completed[node.Group]++
			label = "Completed"
			color = ucx.ColorSuccessMain
		case phase == maintenance.PhasePending:
			label = "Waiting"
			if !submitted && !maintenance.PhaseActive(upgrade.Phase) {
				label = "Not started"
			}
		case phase == maintenance.PhaseFailed || phase == maintenance.PhaseBlocked:
			color = ucx.ColorErrorMain
		}

		heading := []ucx.UiNode{
			ucx.Box().Sx(ucx.SxFlexGrow(1), ucx.SxMinWidth(0), ucx.SxTextOverflow("ellipsis"), ucx.SxOverflow("hidden"), ucx.SxWhiteSpace("nowrap")).Children(
				ucx.LinkButton(fmt.Sprintf("rollingUpgradeNode%d", i), node.Name, ucx.ColorLinkColor).
					Sx(ucx.SxFontSize(14), ucx.SxFontWeight("400")).
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.RouterPushPage(app, "maintenance/"+url.PathEscape(node.Name))
					}),
			),
		}
		if active {
			heading = append(heading, ucx.Spinner(16))
		} else if label != "" {
			heading = append(heading, ucx.Text(label).Sx(ucx.SxFontSize(12), ucx.SxColor(color), ucx.SxWhiteSpace("nowrap")))
		}
		children := []ucx.UiNode{ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
			Sx(ucx.SxAlignItemsCenter, ucx.SxMinWidth(0)).
			Children(heading...)}

		failed := phase == maintenance.PhaseFailed || phase == maintenance.PhaseBlocked
		if active || failed {
			if jobId := jobIds[node.Name]; jobId != "" {
				children = append(children, ucx.JobLogs("rollingUpgradeLog-"+jobId, jobId).
					WithLogHeight(260))
			}
		}
		if !active && operation.Error != "" && !strings.Contains(upgrade.Error, operation.Error) {
			children = append(children, ucx.Warning(operation.Error))
		}

		rows[node.Group] = append(rows[node.Group], ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
			Sx(ucx.SxMinWidth(0)).Children(children...))
	}
	maintenanceSortGroups(groupNames)
	groups := []ucx.UiNode{}
	if activity, ok := maintenanceActivityLabel(upgrade.StartedAt, now); ok {
		groups = append(groups, ucx.Text(activity).Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxFontSize(12), ucx.SxWhiteSpace("nowrap")))
	}
	for _, name := range groupNames {
		children := []ucx.UiNode{
			ucx.Text(fmt.Sprintf("%s: %d of %d completed", name, completed[name], len(rows[name]))).
				Sx(ucx.SxFontSize(14), ucx.SxFontWeight("600")),
		}
		children = append(children, rows[name]...)
		groups = append(groups, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(children...))
	}
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(groups...)
}

func rollingUpgradeReleaseOptions(app *stackUiApp, record shared.ClusterRecord) []ucx.Option {
	now := time.Now().UTC()
	options := []ucx.Option{}
	for _, release := range shared.StableReleases(now) {
		allowed := true
		needsUpgrade := false
		for _, node := range record.Nodes {
			current := app.maintenanceNodeVersion(node.Hostname, record)
			if current == release.Release {
				continue
			}
			if !shared.NodeAgentUpgradeAllowed(current, release.Release) {
				allowed = false
				break
			}
			needsUpgrade = true
		}
		if allowed && needsUpgrade {
			options = append(options, ucx.Option{Key: release.Release, Value: shared.ReleaseSupportLabel(release, now)})
		}
	}
	return options
}

func rollingUpgradeSyncGroups(app *stackUiApp, record shared.ClusterRecord) {
	previous := map[string]string{}
	for _, group := range app.RollingUpgradeGroups {
		previous[group.Name] = group.Preparation
	}
	counts := map[string]int{}
	for _, node := range record.Nodes {
		counts[node.Group]++
	}
	groups := []rollingUpgradeGroup{}
	for name, count := range counts {
		preparation := previous[name]
		if preparation == "" {
			preparation = "neither"
		}
		groups = append(groups, rollingUpgradeGroup{
			Name:        name,
			Count:       count,
			Preparation: preparation,
		})
	}
	sort.Slice(groups, func(i, j int) bool {
		if groups[i].Name == shared.GroupControlPlane || groups[j].Name == shared.GroupControlPlane {
			return groups[i].Name == shared.GroupControlPlane
		}
		return groups[i].Name < groups[j].Name
	})
	app.RollingUpgradeGroups = groups
}

func rollingUpgradeSubmit(app *stackUiApp, releases []ucx.Option) {
	if app.RollingUpgradeBusy || app.Stack == nil {
		return
	}
	session := *app.Session()
	if session == nil {
		return
	}
	validRelease := false
	for _, release := range releases {
		if release.Key == app.RollingUpgradeTargetRelease {
			validRelease = true
		}
	}
	if !validRelease {
		ucxsvc.UiSendFailure(app, "Select a supported target release")
		return
	}
	groups := map[string]maintenance.Options{}
	for _, group := range app.RollingUpgradeGroups {
		if group.Preparation != "neither" && group.Preparation != "cordon" && group.Preparation != "drain" {
			ucxsvc.UiSendFailure(app, "Select preparation for each node group")
			return
		}
		groups[group.Name] = maintenance.Options{
			TimeoutSeconds: maintenanceDefaultTimeoutSeconds,
			Cordon:         group.Preparation != "neither",
			Drain:          group.Preparation == "drain",
		}
	}
	release := app.RollingUpgradeTargetRelease
	app.runAsyncAction("rollingUpgradeBusy", &app.RollingUpgradeBusy, func() error {
		ctx, cancel := context.WithTimeout((*app.Session()).Context(), 30*time.Second)
		defer cancel()
		return maintenance.RollingUpgradeStart(ctx, LocalKubeconfigPath(), release, groups)
	}, func(err error) {
		if err != nil {
			dashboardSendUiMessage(*app.Session(), err.Error(), false)
		}
	})
}
