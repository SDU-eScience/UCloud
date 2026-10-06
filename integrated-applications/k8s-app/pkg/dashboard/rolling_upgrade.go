package dashboard

import (
	"context"
	"fmt"
	"net/url"
	"sort"
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
	}
	upgrade, err := maintenance.RollingUpgradeSnapshot()
	if err != nil {
		content = append(content, ucx.Warning(fmt.Sprintf("Could not read the rolling upgrade: %s", err)))
		return rollingUpgradePageShell(app, content)
	}
	if upgrade.Uid != "" {
		status := fmt.Sprintf("%s · %s", upgrade.TargetRelease, upgrade.Phase)
		if maintenance.PhaseActive(upgrade.Phase) {
			status = "Upgrading to " + upgrade.TargetRelease
		}
		content = append(content, ucx.Text(status))
		if upgrade.Error != "" {
			content = append(content, ucx.Warning(upgrade.Error))
			content = append(content, ucx.Text("Recover failed nodes before restarting. Upgrades already running will finish.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
		}
		snapshot, snapshotErr := maintenance.Snapshot()
		if snapshotErr != nil {
			content = append(content, ucx.Warning(fmt.Sprintf("Could not read node progress: %s", snapshotErr)))
			return rollingUpgradePageShell(app, content)
		}
		content = append(content, rollingUpgradeProgress(app, upgrade, snapshot))
		if maintenance.PhaseActive(upgrade.Phase) {
			content = append(content, ucx.Text("You can leave this page. The upgrade will continue.").Sx(ucx.SxColor(ucx.ColorTextSecondary)))
			return rollingUpgradePageShell(app, content)
		}
	}
	record, found := app.readClusterRecord()
	if !found || record.Phase != "created" || len(record.Nodes) == 0 {
		content = append(content, ucx.Warning("The cluster is not ready to upgrade."))
		return rollingUpgradePageShell(app, content)
	}
	options := rollingUpgradeReleaseOptions(app, record)
	if len(options) == 0 {
		content = append(content, ucx.Text("No supported upgrade is available for all nodes."))
		return rollingUpgradePageShell(app, content)
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
	return rollingUpgradePageShell(app, content)
}

func rollingUpgradePageShell(app *stackUiApp, content []ucx.UiNode) []ucx.UiNode {
	return []ucx.UiNode{app.appShell(appShellProps{
		Content: []ucx.UiNode{shellContentBox(800,
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(content...),
		)},
		EscapePath: "browse/home",
	})}
}

func rollingUpgradeProgress(app *stackUiApp, upgrade maintenance.RollingUpgrade, snapshot map[string]maintenance.Operation) ucx.UiNode {
	rows := map[string][]ucx.UiNode{}
	completed := map[string]int{}
	groupNames := []string{}
	for i, node := range upgrade.Nodes {
		if _, present := rows[node.Group]; !present {
			groupNames = append(groupNames, node.Group)
		}
		phase := node.Phase
		submitted := false
		if operation, present := snapshot[node.Name]; present && operation.Uid == node.OperationUid {
			phase = operation.Phase
			submitted = true
		}
		active := submitted && maintenance.PhaseActive(phase)
		label := phase
		color := ucx.ColorTextSecondary
		if active {
			label = "Upgrading"
			color = ucx.ColorTextPrimary
		}
		if phase == maintenance.PhasePending && !submitted {
			label = "Waiting"
			if !maintenance.PhaseActive(upgrade.Phase) {
				label = "Not started"
			}
		}
		if phase == maintenance.PhaseCompleted {
			completed[node.Group]++
			color = ucx.ColorSuccessMain
		} else if phase == maintenance.PhaseFailed || phase == maintenance.PhaseBlocked {
			color = ucx.ColorErrorMain
		}
		children := []ucx.UiNode{
			ucx.Box().Sx(ucx.SxFlexGrow(1)).Children(
				ucx.LinkButton(fmt.Sprintf("rollingUpgradeNode%d", i), node.Name, ucx.ColorLinkColor).
					Sx(ucx.SxFontSize(14), ucx.SxFontWeight("400")).
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.RouterPushPage(app, "maintenance/"+url.PathEscape(node.Name))
					}),
			),
			ucx.Text(label).Sx(ucx.SxFontSize(12), ucx.SxColor(color)),
		}
		if active {
			children = append(children, ucx.Spinner(16))
		}
		rows[node.Group] = append(rows[node.Group], ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).
			Sx(ucx.SxAlignItemsCenter).
			Children(children...))
	}
	sort.Slice(groupNames, func(i, j int) bool {
		if groupNames[i] == shared.GroupControlPlane || groupNames[j] == shared.GroupControlPlane {
			return groupNames[i] == shared.GroupControlPlane
		}
		return groupNames[i] < groupNames[j]
	})
	groups := []ucx.UiNode{}
	for _, name := range groupNames {
		children := []ucx.UiNode{
			ucx.Text(fmt.Sprintf("%s · %d / %d completed", name, completed[name], len(rows[name]))).
				Sx(ucx.SxFontSize(14), ucx.SxFontWeight("600")),
		}
		children = append(children, rows[name]...)
		groups = append(groups, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(children...))
	}
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 16}).Children(groups...)
}

func rollingUpgradeReleaseOptions(app *stackUiApp, record shared.ClusterRecord) []ucx.Option {
	options := []ucx.Option{}
	for _, release := range shared.K3sCatalog {
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
			options = append(options, ucx.Option{Key: release.Release, Value: release.Release})
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
	app.RollingUpgradeBusy = true
	ucx.AppUpdateModelPatch(session, map[string]ucx.Value{"rollingUpgradeBusy": ucx.VBool(true)})
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 30*time.Second)
		defer cancel()
		err := maintenance.RollingUpgradeStart(ctx, LocalKubeconfigPath(), release, groups)
		app.mu.Lock()
		app.RollingUpgradeBusy = false
		updatedUi := app.UserInterface()
		updatedModel := ucx.AppSnapshot(app)
		app.mu.Unlock()
		if session.Context().Err() == nil {
			ucx.AppUpdateUiLocked(session, updatedUi, updatedModel)
			if err != nil {
				maintenanceSendUiMessage(session, err.Error(), false)
			}
		}
	}()
}
