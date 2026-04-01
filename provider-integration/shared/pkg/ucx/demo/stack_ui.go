package demo

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func StackUi() ucx.Application {
	return &stackUiApp{
		Message:  "Ready",
		NewGroup: "worker",
	}
}

type stackUiApp struct {
	mu              sync.Mutex    `ucx:"-"`
	session         *ucx.Session  `ucx:"-"`
	Stack           *ucxsvc.Stack `ucx:"-"`
	prevRoute       string
	baseAttachments []orcapi.AppParameterValue

	Message   string
	Jobs      []orcapi.Job
	RoutePath string
	NewGroup  string
	AddTarget string
	Machine   accapi.ProductReference
}

const stackGroupingLabel = "ucloud.dk/k8s-node-group"
const stackConfigurationFileName = "configuration.yaml"

func (app *stackUiApp) Mutex() *sync.Mutex     { return &app.mu }
func (app *stackUiApp) Session() **ucx.Session { return &app.session }
func (app *stackUiApp) OnInit() {
	app.loadStackJobs()
}

func (app *stackUiApp) OnSysHello(payload string) {
	var request orcapi.AppUcxConnectJobProviderRequest
	if err := json.Unmarshal([]byte(payload), &request); err != nil {
		return
	}

	stack, ok := ucxsvc.StackFromJob(app, request.Job)
	if !ok {
		return
	}

	for _, resc := range request.Job.Specification.Resources {
		if resc.Type == orcapi.AppParameterValueTypeFile && resc.MountPath != "" {
			continue
		}
		if resc.Type == orcapi.AppParameterValueTypeIngress {
			continue
		}

		app.baseAttachments = append(app.baseAttachments, resc)
	}

	app.Stack = stack
	app.Message = fmt.Sprintf("%#v", app.Stack.Mount())
}

func (app *stackUiApp) loadStackJobs() {
	session := *app.Session()
	if session == nil {
		app.Message = "Unable to load stack jobs"
		return
	}

	result, err := ucxapi.JobsBrowse.Invoke(
		session,
		orcapi.JobsBrowseRequest{
			ItemsPerPage: 250,
			JobFlags: orcapi.JobFlags{
				IncludeParameters: true,
			},
		},
	)
	if err != nil {
		app.Message = fmt.Sprintf("Failed to load stack jobs: %v", err)
		return
	}

	app.Jobs = result.Items
	app.Message = fmt.Sprintf("Found %v jobs in stack", len(result.Items))
}

func (app *stackUiApp) machineGroups() []string {
	groupSet := map[string]struct{}{}
	for _, job := range app.Jobs {
		group := strings.TrimSpace(job.Specification.Labels[stackGroupingLabel])
		if group == "" {
			continue
		}
		groupSet[group] = struct{}{}
	}

	groups := make([]string, 0, len(groupSet))
	for group := range groupSet {
		groups = append(groups, group)
	}

	sort.Strings(groups)
	return groups
}

func (app *stackUiApp) addMachineToGroup(group string) {
	session := *app.Session()
	if session == nil {
		app.Message = "Unable to create machine"
		return
	}

	trimmedGroup := strings.TrimSpace(group)
	if trimmedGroup == "" {
		app.Message = "Please provide a node group"
		return
	}

	if app.Machine.Id == "" {
		app.Message = "Select a machine product before adding a node"
		return
	}

	initLabels := ucxsvc.StackWriteInitScript(app.Stack, `
		sudo mkdir -p /var/lib/ucloud || true     
		cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
		bash /etc/ucloud-stack/demo-runner.sh &
	`)
	extraLabels := map[string]string{stackGroupingLabel: trimmedGroup}

	name := fmt.Sprintf("%v-%v", trimmedGroup, util.RandomTokenNoTs(2))

	ucxsvc.JobCreate(app.Stack, orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Labels:  util.MapMerge(initLabels, extraLabels),
			Product: app.Machine,
		},
		Application: orcapi.NameAndVersion{
			Name:    "test-app",
			Version: "4",
		},
		Name:           name,
		Hostname:       util.OptValue(name),
		Replicas:       1,
		Resources:      util.Combined(app.baseAttachments, []orcapi.AppParameterValue{app.Stack.Mount()}),
		TimeAllocation: util.OptValue(orcapi.SimpleDuration{Hours: 4}),
	})

	app.loadStackJobs()
	app.refreshStackView()
	app.Message = fmt.Sprintf("Created machine %s in group %s. %#v", name, trimmedGroup, extraLabels)
	ucxsvc.RouterPushPage(app, "")
}

func (app *stackUiApp) refreshStackView() {
	session := *app.Session()
	if session == nil {
		return
	}

	_, _ = ucxapi.StackRefresh.Invoke(session, util.Empty{})
}

func (app *stackUiApp) writeStackConfigurationFile() {
	if app.Stack == nil || !app.Stack.Ok {
		app.Message = "Stack is not ready"
		return
	}

	groupLines := []string{}
	for _, group := range app.machineGroups() {
		groupLines = append(groupLines, fmt.Sprintf("  - %s", group))
	}
	if len(groupLines) == 0 {
		groupLines = append(groupLines, "  - worker")
	}

	content := fmt.Sprintf("stack:\n  id: %s\n  mountPath: %s\nnodeGroups:\n%s\n", app.Stack.InstanceId, app.Stack.MountPath, strings.Join(groupLines, "\n"))
	ucxsvc.StackWriteFile(app.Stack, stackConfigurationFileName, content)
}

func (app *stackUiApp) UserInterface() ucx.UiNode {
	if app.AddTarget == "" {
		app.AddTarget = app.NewGroup
	}

	children := []ucx.UiNode{
		ucx.Router("routePath"),
	}

	switch app.RoutePath {
	case "control":
		children = util.Combined(children, app.pageControl())
	default:
		children = util.Combined(children, app.pageMain())
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).
		Sx(ucx.SxP(4)).
		Children(children...)
}

func (app *stackUiApp) pageControl() []ucx.UiNode {
	groups := app.machineGroups()
	selectorOptions := make([]ucx.Option, 0, len(groups))
	for _, group := range groups {
		selectorOptions = append(selectorOptions, ucx.Option{Key: group, Value: group})
	}

	return []ucx.UiNode{ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Stack control plane"),
			ucx.Link("").Children(ucx.Text("Back to overview")),
		),
		ucx.Text("Select a product and submit to add a new node to the stack."),

		ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
			target := strings.TrimSpace(app.AddTarget)
			if target == "" {
				target = strings.TrimSpace(app.NewGroup)
			}
			app.addMachineToGroup(target)
			if target != "" {
				app.AddTarget = target
			}

			if strings.TrimSpace(app.NewGroup) != "" {
				app.NewGroup = strings.TrimSpace(app.NewGroup)
			}

			ucx.AppUpdateUi(app)
		}).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
				func() ucx.UiNode {
					if len(selectorOptions) == 0 {
						return ucx.InputText("newGroup", "Target group", "worker", "newGroup")
					}
					return ucx.Select("addTarget", "Target group", "addTarget", selectorOptions)
				}(),
				ucx.InputText("newGroup", "Create or override group", "worker", "newGroup"),
				ucx.MachineTypeSelector(
					"machine",
					"Machine product",
					"machine",
					ucx.MachineCapabilityDocker,
					ucx.MachineCapabilityVm,
				),
				ucx.SubmitButton("addToStack", "Add machine to stack", ucx.ColorSecondaryMain),
			),
		),
		ucx.TextBound("message").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
	)}
}

func (app *stackUiApp) pageMain() []ucx.UiNode {
	groups := app.machineGroups()
	var children []ucx.UiNode

	children = append(children,
		ucx.StackResources(),

		ucx.Surface().Children(
			ucx.Flex(ucx.FlexProps{Gap: 8}).Children(
				ucx.Button("copyConfig", "Copy configuration", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					app.writeStackConfigurationFile()
					ucxsvc.StackCopyFile(app.Stack, stackConfigurationFileName)
				}),
				ucx.Button("downloadConfig", "Download configuration", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					app.writeStackConfigurationFile()
					ucxsvc.StackDownloadFile(app.Stack, stackConfigurationFileName)
				}),
			),
		),
	)

	if len(groups) == 0 {
		children = append(children, ucx.Text(fmt.Sprintf("No machines found with label %s", stackGroupingLabel)))
	} else {
		for idx, group := range groups {
			groupName := group
			children = append(children,
				ucx.Surface().Children(
					ucx.Toolbar().Children(
						ucx.H3(fmt.Sprintf("Machines in group: %s", groupName)),
						ucx.Link("control").Children(
							ucx.Button(fmt.Sprintf("add-group-%d", idx), "Add machine", ucx.ColorPrimaryMain),
						),
					),
					ucx.StackMachines(ucx.StackMachinesProps{
						Plain: true,
						LabelFilter: util.OptValue(ucx.StackMachinesLabelFilter{
							Label: stackGroupingLabel,
							Value: groupName,
						}),
					}),
				),
			)
		}
	}

	return children
}

func (app *stackUiApp) OnMessage(frame ucx.Frame) {
	switch frame.Opcode {
	case ucx.OpModelInput:
		app.RoutePath = strings.TrimSpace(app.RoutePath)
		app.NewGroup = strings.TrimSpace(app.NewGroup)
		app.AddTarget = strings.TrimSpace(app.AddTarget)
		if app.AddTarget == "" {
			app.AddTarget = app.NewGroup
		}

		if app.prevRoute != app.RoutePath {
			app.prevRoute = app.RoutePath
			ucx.AppUpdateUi(app)
		}
	}
}
