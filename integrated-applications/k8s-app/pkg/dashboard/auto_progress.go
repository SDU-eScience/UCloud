package dashboard

import (
	"fmt"
	"sort"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
)

func autoMaintenancePauseActions(app *stackUiApp, policy maintenance.AutoPolicy, now time.Time) []ucx.UiNode {
	nodes := []ucx.UiNode{}

	pause := []ucx.UiNode{}
	if policy.DeferUntil.After(now) {
		pause = append(pause,
			ucx.Text("The weekly schedule is paused until "+autoTimestampLabel(policy.DeferUntil)+".").
				Sx(ucx.SxColor(ucx.ColorTextSecondary)),
			ucx.Button("autoMaintenanceClearPause", "Resume weekly schedule", ucx.ColorSecondaryMain).
				ButtonBusy("autoMaintenanceBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoMaintenanceActionAsync(app, "The weekly schedule resumed.", func() error {
					return maintenance.AutoDeferUntil(time.Time{})
				})
			}),
		)
	} else {
		for _, days := range []int{1, 3, 7} {
			label := fmt.Sprintf("Pause for %d days", days)
			if days == 1 {
				label = "Pause for 1 day"
			}
			delay := time.Duration(days) * 24 * time.Hour
			pause = append(pause, ucx.Button(fmt.Sprintf("autoMaintenancePause%d", days), label, ucx.ColorSecondaryMain).
				ButtonBusy("autoMaintenanceBusy").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				autoMaintenanceActionAsync(app, "The weekly schedule was paused.", func() error {
					return maintenance.AutoDeferUntil(time.Now().UTC().Add(delay))
				})
			}))
		}
	}
	nodes = append(nodes, ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
		Sx(ucx.SxFlexWrapWrap, ucx.SxAlignItemsCenter).Children(pause...))

	return nodes
}

func maintenanceActivityLabel(startedAt time.Time, now time.Time) (string, bool) {
	if startedAt.IsZero() {
		return "", false
	}
	return "Elapsed " + autoDurationShort(now.Sub(startedAt)), true
}

func maintenanceSortGroups(groupNames []string) {
	sort.Slice(groupNames, func(i, j int) bool {
		if groupNames[i] == shared.GroupControlPlane || groupNames[j] == shared.GroupControlPlane {
			return groupNames[i] == shared.GroupControlPlane
		}
		return groupNames[i] < groupNames[j]
	})
}

func maintenancePhaseLabel(phase string) string {
	switch strings.ToLower(phase) {
	case maintenance.AutoPhasePending:
		return "Waiting to start"
	case maintenance.AutoPhasePreflight:
		return "Checking the cluster"
	case maintenance.AutoPhaseBackup:
		return "Waiting for a backup"
	case maintenance.AutoPhaseRunning:
		return "In progress"
	case maintenance.AutoPhaseCompleted:
		return "Completed"
	case maintenance.AutoPhaseBlocked, "failed":
		return "Stopped"
	case "cancelled":
		return "Cancelled"
	}
	return phase
}

func autoMaintenanceNodeProgress(app *stackUiApp, status dashboardAutoStatus) []ucx.UiNode {
	record, found := app.readClusterRecord()
	if !found {
		return []ucx.UiNode{ucx.Warning("Could not read the nodes for this run.")}
	}
	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return []ucx.UiNode{ucx.Warning("Could not read node progress: " + err.Error())}
	}
	run := status.State.LastRun
	planned := map[string]maintenance.RollingUpgradeNode{}
	upgrade, err := maintenance.RollingUpgradeSnapshot()
	if err != nil {
		return []ucx.UiNode{ucx.Warning("Could not read Kubernetes upgrade progress: " + err.Error())}
	}
	if (run.K8sRunUid != "" && upgrade.AutoRunUid == run.K8sRunUid) || (run.Channel == maintenance.AutoChannelK8s && upgrade.AutoRunUid == run.Uid) {
		for _, node := range upgrade.Nodes {
			planned[node.Name] = node
		}
	}
	sort.SliceStable(record.Nodes, func(i, j int) bool {
		leftControlPlane := record.Nodes[i].Group == shared.GroupControlPlane
		rightControlPlane := record.Nodes[j].Group == shared.GroupControlPlane
		if leftControlPlane != rightControlPlane {
			return leftControlPlane
		}
		if record.Nodes[i].Group != record.Nodes[j].Group {
			return record.Nodes[i].Group < record.Nodes[j].Group
		}
		return record.Nodes[i].Hostname < record.Nodes[j].Hostname
	})
	rowsByGroup := map[string][]ucx.UiNode{}
	completedByGroup := map[string]int{}
	groupNames := []string{}
	for _, node := range record.Nodes {
		if _, present := rowsByGroup[node.Group]; !present {
			groupNames = append(groupNames, node.Group)
		}
		k8s := autoNodeK8sProgress(app, run, planned, snapshot, node.Hostname)
		osProgress := autoNodeOsProgress(run, status, snapshot, node.Hostname)

		channels := []autoNodeChannel{}
		names := []string{}
		if k8s.InRun {
			channels = append(channels, k8s)
			names = append(names, "Kubernetes")
		}
		if osProgress.InRun {
			channels = append(channels, osProgress)
			names = append(names, "Operating system")
		}

		nodeComplete := len(channels) > 0
		if len(channels) > 0 {
			for _, channel := range channels {
				if channel.Percent < 100 {
					nodeComplete = false
				}
			}
		}
		if nodeComplete {
			completedByGroup[node.Group]++
		}

		activeIndex := -1
		for i := range channels {
			if channels[i].Active {
				activeIndex = i
				break
			}
		}

		heading := []ucx.UiNode{
			ucx.Box().Sx(ucx.SxFlexGrow(1), ucx.SxMinWidth(0), ucx.SxTextOverflow("ellipsis"), ucx.SxOverflow("hidden"), ucx.SxWhiteSpace("nowrap")).
				Children(ucx.Text(node.Hostname).Sx(ucx.SxFontWeight("600"))),
		}

		failed := false
		for i, channel := range channels {
			if channel.Percent >= 100 || channel.Active {
				continue
			}
			style := []ucx.SxOption{ucx.SxColor(ucx.ColorTextSecondary), ucx.SxFontSize(12), ucx.SxWhiteSpace("nowrap")}
			if channel.Failed {
				failed = true
				style = append(style, ucx.SxColor(ucx.ColorErrorMain))
			}
			heading = append(heading, ucx.Text(names[i]+": "+channel.Action).Sx(style...))
		}

		switch {
		case nodeComplete:
			heading = append(heading, ucx.Text("Up to date").Sx(ucx.SxColor(ucx.ColorSuccessMain), ucx.SxWhiteSpace("nowrap")))
		case activeIndex >= 0:
			heading = append(heading, ucx.Spinner(14))
		case len(channels) == 0 && maintenance.AutoRunActive(run):
			heading = append(heading, ucx.Text("Waiting to start").Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWhiteSpace("nowrap")))
		case len(channels) == 0:
			heading = append(heading, ucx.Text("Not updated").Sx(ucx.SxColor(ucx.ColorTextSecondary), ucx.SxWhiteSpace("nowrap")))
		}

		children := []ucx.UiNode{ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
			Sx(ucx.SxAlignItemsCenter, ucx.SxMinWidth(0)).Children(heading...)}

		for _, channel := range channels {
			if channel.Failed && channel.Operation.Error != "" && !strings.Contains(run.Error, channel.Operation.Error) {
				children = append(children, ucx.Warning(channel.Operation.Error))
			}
		}

		if activeIndex >= 0 || failed {
			if node.JobId != "" {
				children = append(children, ucx.JobLogs("autoMaintenanceLog-"+node.JobId, node.JobId).
					WithLogHeight(260))
			}
		}

		rowsByGroup[node.Group] = append(rowsByGroup[node.Group], ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).
			Sx(ucx.SxPy(10), ucx.SxPl(16), ucx.SxMinWidth(0), ucx.SxBorderWidth(0), ucx.SxBorderTopWidth(1), ucx.SxBorderSolid, ucx.SxBorderColor(ucx.ColorBorderColor)).Children(children...))
	}
	result := []ucx.UiNode{}
	maintenanceSortGroups(groupNames)
	for _, name := range groupNames {
		label := fmt.Sprintf("%s: %d of %d completed", name, completedByGroup[name], len(rowsByGroup[name]))
		groupChildren := []ucx.UiNode{
			ucx.Text(label).Sx(ucx.SxFontWeight("600"), ucx.SxWhiteSpace("nowrap")),
		}
		groupChildren = append(groupChildren, rowsByGroup[name]...)
		result = append(result, ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 6}).Sx(ucx.SxMt(12), ucx.SxMinWidth(0)).Children(groupChildren...))
	}
	return result
}

type autoNodeChannel struct {
	InRun     bool
	Active    bool
	Failed    bool
	Percent   int64
	Action    string
	Operation maintenance.Operation
}

func autoNodeK8sProgress(
	app *stackUiApp,
	run maintenance.AutoRun,
	planned map[string]maintenance.RollingUpgradeNode,
	snapshot map[string]maintenance.Operation,
	nodeName string,
) autoNodeChannel {
	result := autoNodeChannel{}
	version := ""
	if app.clusterHealth != nil {
		version = app.clusterHealth.Versions[nodeName]
	}
	if run.K8sRelease != "" {
		result.InRun = true
		if version != "" && shared.SameRelease(version, run.K8sRelease) {
			result.Percent = 100
			result.Action = "completed"
			return result
		}
	}
	plan, present := planned[nodeName]
	if !present {
		if result.InRun {
			result.Action = "waiting to start"
		}
		return result
	}

	result.InRun = true
	operation, exists := snapshot[nodeName]
	phase := plan.Phase
	if exists && operation.Uid == plan.OperationUid {
		result.Operation = operation
		phase = operation.Phase
		result.Active = maintenance.PhaseActive(phase)
	}
	switch {
	case phase == maintenance.PhaseCompleted:
		result.Percent = 100
		result.Action = "completed"
	case phase == maintenance.PhaseBlocked || phase == maintenance.PhaseFailed:
		result.Failed = true
		result.Action = "stopped"
	case result.Active:
		result.Percent = maintenanceOperationPercent(operation, phase)
		result.Action = autoMaintenanceActionLabel(operation, phase)
	default:
		result.Action = "waiting to start"
	}
	return result
}

func autoNodeOsProgress(
	run maintenance.AutoRun,
	status dashboardAutoStatus,
	snapshot map[string]maintenance.Operation,
	nodeName string,
) autoNodeChannel {
	result := autoNodeChannel{InRun: run.Channel == maintenance.AutoChannelOs}

	operation, exists := snapshot[nodeName]
	owned := exists && run.Uid != "" && operation.AutoRunUid == run.Uid && maintenance.KindIsOsUpdate(operation.Kind)
	if !owned && exists && maintenance.KindIsOsUpdate(operation.Kind) && maintenance.PhaseActive(operation.Phase) {
		owned = true
	}
	if owned {
		result.InRun = true
		result.Operation = operation
		result.Active = maintenance.PhaseActive(operation.Phase)
		switch {
		case operation.Phase == maintenance.PhaseCompleted:
			result.Percent = 100
			result.Action = "completed"
		case operation.Phase == maintenance.PhaseBlocked || operation.Phase == maintenance.PhaseFailed:
			result.Failed = true
			result.Action = "stopped"
		default:
			result.Percent = maintenanceOperationPercent(operation, operation.Phase)
			result.Action = autoMaintenanceActionLabel(operation, operation.Phase)
		}
		return result
	}
	if !result.InRun {
		return result
	}

	for _, report := range status.State.OsReports {
		if report.NodeName != nodeName {
			continue
		}
		switch {
		case report.Error != "":
			result.Failed = true
			result.Action = "update check failed"
		case !report.CheckedAt.IsZero() && report.Pending == 0 && !report.RebootRequired:
			result.Percent = 100
			result.Action = "completed"
		case report.Pending > 0:
			result.Action = fmt.Sprintf("%d packages pending", report.Pending)
		case report.RebootRequired:
			result.Action = "restart required"
		}
		return result
	}

	result.Action = "waiting to start"
	return result
}

func maintenanceOperationPercent(operation maintenance.Operation, phase string) int64 {
	if phase == maintenance.PhaseCompleted {
		return 100
	}
	if operation.Progress.Percent <= 0 {
		return 0
	}
	return min(int64(operation.Progress.Percent), 99)
}

func autoMaintenanceActionLabel(operation maintenance.Operation, phase string) string {
	if stage := strings.TrimSpace(operation.Progress.Stage); stage != "" && maintenance.PhaseActive(phase) {
		return stage
	}
	if detail := strings.TrimSpace(operation.Progress.Detail); detail != "" && phase == maintenance.PhaseRunning {
		return "Working"
	}
	switch operation.LogStage {
	case "draining":
		return "Moving workloads off the node"
	case "submitting", "submitted":
		return "Preparing the update"
	case "executor-staging", "download-retry":
		return "Downloading updates"
	case "executor-applying":
		return "Installing updates"
	case "executor-rebooting":
		return "Restarting the node"
	case "executor-verifying", "waiting-ready":
		return "Checking node health"
	case "submit-retry-node", "submit-retry-address", "submit-retry-status", "submit-retry-traffic", "submit-retry-persist", "submit-retry-post":
		return "Waiting for the node"
	}
	return maintenancePhaseLabel(phase)
}

func autoDurationShort(delta time.Duration) string {
	switch {
	case delta < time.Minute:
		return fmt.Sprintf("%d s", int(delta/time.Second))
	case delta < time.Hour:
		return fmt.Sprintf("%d min %d s", int(delta/time.Minute), int(delta/time.Second)%60)
	default:
		return fmt.Sprintf("%d h %d min", int(delta/time.Hour), int(delta/time.Minute)%60)
	}
}
