package main

import (
	"context"
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

	corev1 "k8s.io/api/core/v1"
	v1 "k8s.io/apimachinery/pkg/apis/meta/v1"

	"k8s.io/client-go/kubernetes"
	restclient "k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
)

func StackUi() ucx.Application {
	return &stackUiApp{
		Message: "Ready",
	}
}

type stackUiApp struct {
	mu              sync.Mutex    `ucx:"-"`
	session         *ucx.Session  `ucx:"-"`
	Stack           *ucxsvc.Stack `ucx:"-"`
	prevRoute       string
	baseAttachments []orcapi.AppParameterValue
	k8sClientConfig *restclient.Config    `ucx:"-"`
	k8sClient       *kubernetes.Clientset `ucx:"-"`

	Message   string
	Jobs      []orcapi.Job
	RoutePath string
	AddTarget string
	Machine   accapi.ProductReference
}

const stackGroupingLabel = "ucloud.dk/k8s-node-group"
const stackConfigurationFileName = "configuration.yaml"
const kubernetesConfigurationFileName = "kubeconfig"
const kubernetesTokenFileName = "kube-api-token"
const headlampTokenFileName = "headlamp-token"

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
	// app.Message = fmt.Sprintf("%#v", app.Stack.Mount())
	ucxsvc.UiSendSuccess(app, "Main control plane is ready!")

	config, _ := clientcmd.BuildConfigFromFlags("", "/home/ucloud/.kube/config")
	clientset, _ := kubernetes.NewForConfig(config)

	app.k8sClientConfig = config
	app.k8sClient = clientset
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

// returns set of all machine groups (in this case, worker and control-plane)
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

// returns { group->[jobs] } table
func (app *stackUiApp) jobsByGroup() map[string][]orcapi.Job {
	m := make(map[string][]orcapi.Job)
	for _, job := range app.Jobs {
		group := strings.TrimSpace(job.Specification.Labels[stackGroupingLabel])
		if group == "" {
			continue
		}
		m[group] = append(m[group], job)
	}
	return m
}

// create a VM and add it to the given group
func (app *stackUiApp) addMachineToGroup(group string) {
	session := *app.Session()
	jobsByGroup := app.jobsByGroup()
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

	if group == "worker" || group == "control-plane" {
		jobs := jobsByGroup[group]
		// copy labels and attachments from last created VM
		labels := jobs[len(jobs)-1].Specification.ResourceSpecification.Labels
		attachments := jobs[len(jobs)-1].Specification.Resources
		vmId := ucxsvc.VirtualMachineCreate(app.Stack, ucxsvc.VirtualMachineSpec{
			Labels:         labels,
			Product:        app.Machine,
			Image:          ucxsvc.VmImageUbuntu26_04,
			Hostname:       fmt.Sprintf("%s-%v", group, len(jobs)+1),
			Attachments:    attachments,
			SkipStackState: true,
		})
		ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created new %s VM %s!", group, vmId))
	} else {
		ucxsvc.UiSendFailure(app, "Unsupported group")
	}
	ucxsvc.RouterPushPage(app, "")
}

func (app *stackUiApp) refreshStackView() {
	session := *app.Session()
	if session == nil {
		return
	}

	_, _ = ucxapi.StackRefresh.Invoke(session, util.Empty{})
}

func (app *stackUiApp) UserInterface() ucx.UiNode {
	if app.AddTarget == "" {
		app.AddTarget = "worker"
	}
	children := []ucx.UiNode{
		ucx.Router("routePath"),
	}

	switch app.RoutePath {
	case "control":
		children = util.Combined(children, app.pageControl())
	case "cluster-info":
		children = util.Combined(children, app.pageClusterInfo())
	default:
		children = util.Combined(children, app.pageMain())
	}

	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).
		Sx(ucx.SxP(4)).
		Children(children...)
}

// Show cluster information (nodes, pods, services)
func (app *stackUiApp) pageClusterInfo() []ucx.UiNode {
	nodes, _ := app.k8sClient.CoreV1().Nodes().List(context.TODO(), v1.ListOptions{})
	pods, _ := app.k8sClient.CoreV1().Pods("").List(context.TODO(), v1.ListOptions{})
	svcs, _ := app.k8sClient.CoreV1().Services("").List(context.TODO(), v1.ListOptions{})

	// build node table
	nodeChildren := []ucx.UiNode{ucx.TableNode("nodes", []ucx.Option{
		{Key: "name", Value: "Name"},
		{Key: "ready", Value: "Ready"},
		{Key: "ip", Value: "IP"},
	})}

	for _, node := range nodes.Items {
		name := node.ObjectMeta.Name
		ip := ""
		ready := ""
		for _, addr := range node.Status.Addresses {
			if addr.Type == corev1.NodeInternalIP {
				ip = addr.Address
			}
		}
		for _, cond := range node.Status.Conditions {
			if cond.Type == corev1.NodeReady {
				ready = fmt.Sprintf("%v", cond.Status)
			}
		}

		nodeChildren = append(nodeChildren, ucx.TableNode("nodes", []ucx.Option{
			{Key: "name", Value: name},
			{Key: "ready", Value: ready},
			{Key: "ip", Value: fmt.Sprintf("%v", ip)},
		}))

	}

	// build pod table
	podChildren := []ucx.UiNode{ucx.TableNode("pods", []ucx.Option{
		{Key: "name", Value: "Name"},
		{Key: "namespace", Value: "Namespace"},
		{Key: "phase", Value: "Phase"},
		{Key: "node", Value: "Node"},
	})}

	for _, pod := range pods.Items {
		name := pod.ObjectMeta.Name
		namespace := pod.ObjectMeta.Namespace
		phase := pod.Status.Phase
		node := pod.Status.HostIP

		podChildren = append(podChildren, ucx.TableNode("pods", []ucx.Option{
			{Key: "name", Value: name},
			{Key: "namespace", Value: namespace},
			{Key: "phase", Value: fmt.Sprintf("%v", phase)},
			{Key: "node", Value: node},
		}))

	}

	// build service table
	svcChildren := []ucx.UiNode{ucx.TableNode("svc", []ucx.Option{
		{Key: "name", Value: "Name"},
		{Key: "namespace", Value: "Namespace"},
		{Key: "ports", Value: "Ports"},
	})}

	for _, svc := range svcs.Items {
		name := svc.ObjectMeta.Name
		namespace := svc.ObjectMeta.Namespace
		portList := []string{}

		for _, p := range svc.Spec.Ports {
			if p.NodePort != 0 {
				portList = append(portList, fmt.Sprintf("%d:%d/%s", p.Port, p.NodePort, p.Protocol))
			} else {
				portList = append(portList, fmt.Sprintf("%d/%s", p.Port, p.Protocol))
			}
		}

		svcChildren = append(svcChildren, ucx.TableNode("svc", []ucx.Option{
			{Key: "name", Value: name},
			{Key: "namespace", Value: namespace},
			{Key: "ports", Value: fmt.Sprintf("%v", strings.Join(portList, ", "))},
		}))

	}

	return []ucx.UiNode{ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Kubernetes cluster information"),
			ucx.Button("refresh", "Refresh", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucx.AppUpdateUi(app)
			}),
			ucx.Link("").Children(ucx.Text("Back to overview")),
		),
		ucx.Tabs().Children(
			ucx.Tab("Nodes", ucx.IconHeroServer).Children(nodeChildren...),
			ucx.Tab("Pods", ucx.IconHeroServer).Children(podChildren...),
			ucx.Tab("Services", ucx.IconHeroServer).Children(svcChildren...),
		),
	)}
}

// "Add new VM" page
func (app *stackUiApp) pageControl() []ucx.UiNode {
	groups := app.machineGroups()
	selectorOptions := make([]ucx.Option, 0, len(groups))
	for _, group := range groups {
		selectorOptions = append(selectorOptions, ucx.Option{Key: group, Value: group})
	}

	return []ucx.UiNode{ucx.KeyboardNavigationNode("keyboardNavigation").HorizontalSelector("[data-job-info-field]").SubmitForm("addNodeForm").Children(
		ucx.Surface().Children(
			ucx.Toolbar().Children(
				ucx.H2("Add new virtual machine to the stack"),
				ucx.Link("").Children(ucx.Text("Back to overview")),
			),
			ucx.Text("Select a product and submit to add a new node to the stack."),

			ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
				target := strings.TrimSpace(app.AddTarget)
				if target == "" {
					target = "worker"
				}
				app.addMachineToGroup(target)
				if target != "" {
					app.AddTarget = target
				}

				ucx.AppUpdateUi(app)
			}).Children(
				ucx.FieldGroupNode().Children(
					ucx.FieldRowNodeEx("machineRow", "Machine product", "").Children(
						// NOTE: multiple control planes currently don't work
						//ucx.Select("addTarget", "Target group", "addTarget", selectorOptions),
						ucx.MachineTypeSelector(
							"machine",
							"Machine product",
							"machine",
							ucx.MachineCapabilityDocker,
							ucx.MachineCapabilityVm,
						),
					),
				),
				ucx.SubmitButton("addToStack", "Add machine to stack", ucx.ColorSecondaryMain).ButtonSubmitShortcut(true),
			),
			ucx.TextBound("message").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		),
	)}
}

func (app *stackUiApp) pageMain() []ucx.UiNode {
	groups := app.machineGroups()
	var children []ucx.UiNode

	children = append(children,
		ucx.Surface().Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).Children(
				ucx.Flex(ucx.FlexProps{Gap: 8}).Children(
					ucx.Button("downloadKubernetesConfig", "Download Kubernetes configuration", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackDownloadFile(app.Stack, kubernetesConfigurationFileName)
					}),
					ucx.Button("copyKubernetesToken", "Copy Kubernetes authentication token", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackCopyFile(app.Stack, kubernetesTokenFileName)
					}),
					ucx.Button("copyHeadlampToken", "Copy Headlamp authentication token", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackCopyFile(app.Stack, headlampTokenFileName)
					}),
					ucx.Link("cluster-info").Children(ucx.Button("cluster-information", "Show cluster information", ucx.ColorPrimaryMain)),
				),
			),
		),
	)

	if len(groups) == 0 {
		children = append(children, ucx.Text(fmt.Sprintf("No machines found with label %s", stackGroupingLabel)))
	} else {
		for _, group := range groups {
			groupName := group
			children = append(children,
				ucx.Surface().Children(
					ucx.Toolbar().Children(
						ucx.H3(fmt.Sprintf("Machines in group: %s", groupName)),
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
	children = append(children, ucx.Flex(ucx.FlexProps{Gap: 8}).Children(ucx.Link("control").Children(
		ucx.Button("add-machine", "Add machine", ucx.ColorPrimaryMain),
	)))

	return children
}

func (app *stackUiApp) OnMessage(frame ucx.Frame) {
	switch frame.Opcode {
	case ucx.OpModelInput:
		app.RoutePath = strings.TrimSpace(app.RoutePath)
		app.AddTarget = strings.TrimSpace(app.AddTarget)
		if app.AddTarget == "" {
			app.AddTarget = "worker"
		}

		if app.prevRoute != app.RoutePath {
			app.prevRoute = app.RoutePath
			ucx.AppUpdateUi(app)
		}
	}
}
