package dashboard

import (
	"fmt"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func groupFromRoute(routePath string) string {
	rest := strings.TrimPrefix(routePath, "control")
	rest = strings.TrimPrefix(rest, "/")
	return strings.TrimSpace(rest)
}

func (app *stackUiApp) pageAddMachine() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if recordOk && record.MachineProvider != "" {
		app.ClusterProvider = record.MachineProvider
	}

	group := strings.TrimSpace(app.TargetGroup)

	shellEscape := appShellProps{EscapePath: "browse/nodes"}

	if group == "" {
		return app.messagePage(nil, "browse/nodes", ucx.Text("Select a node group to add nodes."))
	}

	shellEscape.Bottom = []ucx.UiNode{shellBottomNode(app, "nodes", "Add nodes", "nodes/"+group)}

	if !recordOk {
		return app.messagePage(shellEscape.Bottom, "browse/nodes", ucx.Text("Could not read the cluster record."))
	}

	if record.Phase != shared.ClusterRecordPhaseCreated || len(record.PendingCleanup) > 0 {
		return app.messagePage(shellEscape.Bottom, "browse/nodes", scaleoutBlockedContent(record, "New nodes")...)
	}

	var pool *shared.ClusterPoolRecord
	for i := range record.Pools {
		if record.Pools[i].Name == group {
			pool = &record.Pools[i]
			break
		}
	}

	if pool == nil {
		return app.messagePage(shellEscape.Bottom, "browse/nodes", ucx.Text("Unknown node group: "+group))
	}
	groupLabel := "Worker pool"
	if group == shared.GroupControlPlane {
		groupLabel = "Control-plane nodes"
	}

	if app.TargetDiskGb <= 0 {
		app.TargetDiskGb = pool.DiskGb
	}
	if app.TargetCount <= 0 {
		app.TargetCount = 1
	}

	children := []ucx.UiNode{
		ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
			if app.AddBusy {
				return
			}

			groupValue := group
			machine := pool.Machine
			stack := app.Stack
			diskGb := app.TargetDiskGb
			count := app.TargetCount

			app.runAsyncAction("addBusy", &app.AddBusy, func() error {
				app.addMachinesToGroup(stack, groupValue, machine, diskGb, count)
				app.provisioningKickNow()
				return nil
			}, nil)
		}).Children(
			ucx.FieldGroupNode().Children(
				ucx.FieldRowNodeEx("poolNameRow", groupLabel, "").
					Children(ucx.Text(group)),
				ucx.FieldRowNodeEx("machineRow", "Machine type", "").
					FieldRowDescription("All nodes in this group use the same machine type.").
					Children(ucx.Text(pool.Machine.Id)),
				ucx.FieldRowNodeEx("diskRow", "Disk size (GB)", "targetDiskGb").
					FieldRowDescription(fmt.Sprintf("The disk size for each new node. This group uses %d GB by default.", pool.DiskGb)).
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetDisk", "", "targetDiskGb", 10, 1024).WithAutoFocus(),
					),
				ucx.FieldRowNodeEx("countRow", "Node count", "targetCount").
					FieldRowDescription("The number of nodes to add to this group.").
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetCount", "", "targetCount", 1, 250),
					),
			),
			ucx.SubmitButton("addToPool", "Add nodes", ucx.ColorSuccessMain).
				ButtonBusy("addBusy").
				ButtonSubmitShortcut(true).
				Sx(ucx.SxJustifyEnd),
		),
	}

	shellEscape.Content = []ucx.UiNode{shellContentBox(800,
		ucx.KeyboardNavigationNode("keyboardNavigation").
			HorizontalSelector("[data-job-info-field]").
			SubmitForm("addNodeForm").
			SubmitDisabled(app.AddBusy).
			Children(children...),
	)}

	return []ucx.UiNode{app.appShell(shellEscape)}
}

func (app *stackUiApp) pageAddPool() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if recordOk && record.MachineProvider != "" {
		app.ClusterProvider = record.MachineProvider
	}

	shellEscape := appShellProps{EscapePath: "browse/nodes"}

	shellEscape.Bottom = []ucx.UiNode{shellBottomNode(app, "nodes", "Add worker pool")}

	if !recordOk {
		return app.messagePage(shellEscape.Bottom, "browse/nodes", ucx.Text("Could not read the cluster record."))
	}

	if record.Phase != shared.ClusterRecordPhaseCreated || len(record.PendingCleanup) > 0 {
		return app.messagePage(shellEscape.Bottom, "browse/nodes", scaleoutBlockedContent(record, "New worker pools")...)
	}

	if app.TargetDiskGb <= 0 {
		app.TargetDiskGb = 50
	}
	if app.TargetCount <= 0 {
		app.TargetCount = 1
	}

	form := ucx.Form("addPoolForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
		if app.AddBusy {
			return
		}

		name := app.NewPoolName
		machine := app.Machine
		provider := app.ClusterProvider
		stack := app.Stack
		diskGb := app.TargetDiskGb
		count := app.TargetCount

		app.runAsyncAction("addBusy", &app.AddBusy, func() error {
			app.addPool(provider, stack, name, machine, diskGb, count)
			app.provisioningKickNow()
			return nil
		}, nil)
	}).Children(
		ucx.FieldGroupNode().Children(
			ucx.FieldRowNodeEx("poolNameRow", "Worker pool name", "newPoolName").
				FieldRowDescription("A name for this worker pool. Must contain only a-z, 0-9 and dashes.").
				FieldRowRequired(true).
				Children(
					ucx.InputText("poolName", "", "workers", "newPoolName").WithAutoFocus(),
				),
			ucx.FieldRowNodeEx("machineRow", "Machine type", "").
				FieldRowDescription("The machine type used for the nodes in this pool.").
				Children(
					ucx.MachineTypeSelector("poolMachine", "", "machine", ucx.MachineCapabilityVm).
						MachineSelectorProviderBindPath("clusterProvider").MachineSelectorProviderOnly(true),
				),
			ucx.FieldRowNodeEx("diskRow", "Disk size (GB)", "targetDiskGb").
				FieldRowDescription("The disk size for each node in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolDisk", "", "targetDiskGb", 10, 1024),
				),
			ucx.FieldRowNodeEx("countRow", "Node count", "targetCount").
				FieldRowDescription("The number of worker nodes in this pool.").
				FieldRowRequired(true).
				Children(
					ucx.InputNumber("poolCount", "", "targetCount", 1, 250),
				),
		),
		ucx.SubmitButton("addPool", "Add worker pool", ucx.ColorSuccessMain).
			ButtonBusy("addBusy").
			ButtonSubmitShortcut(true).
			Sx(ucx.SxJustifyEnd),
	)

	shellEscape.Content = []ucx.UiNode{shellContentBox(800,
		ucx.KeyboardNavigationNode("keyboardNavigation").
			HorizontalSelector("[data-job-info-field]").
			SubmitForm("addPoolForm").
			SubmitDisabled(app.AddBusy).
			Children(form),
	)}

	return []ucx.UiNode{app.appShell(shellEscape)}
}

func (app *stackUiApp) addMachinesToGroup(stack *ucxsvc.Stack, group string, machine accapi.ProductReference, diskGb int, count int) {
	if count < 1 {
		count = 1
	}

	_, ok := shared.ClusterAddNodesBatch(app, stack, group, machine, diskGb, count)
	if !ok {
		return
	}

	message := fmt.Sprintf("Added %s to worker pool %s.", dashboardNodeCount(count), group)
	if group == shared.GroupControlPlane {
		message = fmt.Sprintf("Added %s to the control plane.", dashboardNodeCount(count))
	}
	ucxsvc.UiSendSuccess(app, message)
	ucxsvc.RouterPushPage(app, "browse/nodes")
}

func (app *stackUiApp) addPool(provider string, stack *ucxsvc.Stack, name string, machine accapi.ProductReference, diskGb int, count int) {
	trimmedName := strings.TrimSpace(name)
	if trimmedName == "" {
		ucxsvc.UiSendFailure(app, "Please provide a worker pool name")
		return
	}

	if provider != "" && machine.Provider != provider {
		ucxsvc.UiSendFailure(app, "The machine type must come from the provider that runs the cluster: "+provider)
		return
	}

	ok := shared.ClusterAddPool(app, stack, shared.ClusterPoolSpec{
		Name:    trimmedName,
		Machine: machine,
		Nodes:   count,
		DiskGb:  diskGb,
	})
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created worker pool %s with %s.", trimmedName, dashboardNodeCount(count)))
	ucxsvc.RouterPushPage(app, "browse/nodes")
}

func scaleoutBlockedContent(record shared.ClusterRecord, action string) []ucx.UiNode {
	content := []ucx.UiNode{}
	if record.Phase == shared.ClusterRecordPhaseCleanup || len(record.PendingCleanup) > 0 {
		content = append(content, ucx.Text(
			"The cluster is removing the resources of a failed scale-out. "+action+
				" cannot be added until the cleanup finishes.",
		))
	} else {
		content = append(content, ucx.Text(
			action+" cannot be added while the cluster is in state "+record.Phase+
				". The cluster must be in the created state.",
		))
	}
	if record.FailureReason != "" {
		content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
	}
	for _, pending := range record.PendingCleanup {
		content = append(content, ucx.Text("Removing "+pendingCleanupText(pending)))
	}
	return content
}

func dashboardNodeCount(count int) string {
	if count == 1 {
		return "1 node"
	}
	return fmt.Sprintf("%d nodes", count)
}
