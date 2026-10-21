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
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("No node pool was selected."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	shellEscape.Bottom = []ucx.UiNode{shellBottomNode(app, "nodes", "Add machine", "nodes/"+group)}

	if !recordOk {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Could not read the cluster record."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if record.Phase != "created" {
		content := []ucx.UiNode{ucx.Text(
			"New nodes cannot be added while the cluster is in state " + record.Phase +
				". The cluster must be in the created state.",
		)}
		if record.FailureReason != "" {
			content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		}
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, content...)}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	var pool *shared.ClusterPoolRecord
	for i := range record.Pools {
		if record.Pools[i].Name == group {
			pool = &record.Pools[i]
			break
		}
	}

	if pool == nil {
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Unknown node pool: "+group))}
		return []ucx.UiNode{app.appShell(shellEscape)}
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
				ucx.FieldRowNodeEx("poolNameRow", "Pool name", "").
					Children(ucx.Text(group)),
				ucx.FieldRowNodeEx("machineRow", "Machine type", "").
					FieldRowDescription("All machines in a pool use the same machine type.").
					Children(ucx.Text(pool.Machine.Id)),
				ucx.FieldRowNodeEx("diskRow", "Disk size (GB)", "targetDiskGb").
					FieldRowDescription(fmt.Sprintf("The disk size for each new node. This pool uses %d GB by default.", pool.DiskGb)).
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetDisk", "", "targetDiskGb", 10, 1024).WithAutoFocus(),
					),
				ucx.FieldRowNodeEx("countRow", "Node count", "targetCount").
					FieldRowDescription("The number of nodes to add to this pool.").
					FieldRowRequired(true).
					Children(
						ucx.InputNumber("targetCount", "", "targetCount", 1, 250),
					),
			),
			ucx.SubmitButton("addToPool", "Add machine to pool", ucx.ColorSuccessMain).
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
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, ucx.Text("Could not read the cluster record."))}
		return []ucx.UiNode{app.appShell(shellEscape)}
	}

	if record.Phase != "created" {
		content := []ucx.UiNode{ucx.Text(
			"New pools cannot be added while the cluster is in state " + record.Phase +
				". The cluster must be in the created state.",
		)}
		if record.FailureReason != "" {
			content = append(content, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		}
		shellEscape.Content = []ucx.UiNode{shellContentBox(800, content...)}
		return []ucx.UiNode{app.appShell(shellEscape)}
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
			ucx.FieldRowNodeEx("poolNameRow", "Pool name", "newPoolName").
				FieldRowDescription("The Kubernetes node group name for this pool. Must contain only a-z, 0-9 and dashes.").
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

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created %d new %s VM(s).", count, group))
	ucxsvc.RouterPushPage(app, "browse/nodes")
}

func (app *stackUiApp) addPool(provider string, stack *ucxsvc.Stack, name string, machine accapi.ProductReference, diskGb int, count int) {
	trimmedName := strings.TrimSpace(name)
	if trimmedName == "" {
		ucxsvc.UiSendFailure(app, "Please provide a pool name")
		return
	}

	if provider != "" && machine.Provider != provider {
		ucxsvc.UiSendFailure(app, "The machine must come from the provider that runs the cluster: "+provider)
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

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created pool %s with %d node(s)!", trimmedName, count))
	ucxsvc.RouterPushPage(app, "browse/nodes")
}
