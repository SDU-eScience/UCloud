package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
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
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func App() ucx.Application {
	return &stackUiApp{}
}

type stackUiApp struct {
	mu        sync.Mutex    `ucx:"-"`
	session   *ucx.Session  `ucx:"-"`
	Stack     *ucxsvc.Stack `ucx:"-"`
	prevRoute string
	k8sClient *kubernetes.Clientset `ucx:"-"`

	Jobs      []orcapi.Job
	RoutePath string
	Machine   accapi.ProductReference

	Nodes []nodeRow
	Pods  []podRow
	Svcs  []svcRow
}

type nodeRow struct {
	Name  string
	Ready string
	Ip    string
}

type podRow struct {
	Name      string
	Namespace string
	Phase     string
	Node      string
}

type svcRow struct {
	Name      string
	Namespace string
	Ports     string
}

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

	app.Stack = stack
	ucxsvc.UiSendSuccess(app, "Main control plane is ready!")

	config, _ := clientcmd.BuildConfigFromFlags("", "/home/ucloud/.kube/config")
	clientset, _ := kubernetes.NewForConfig(config)

	app.k8sClient = clientset
}

func (app *stackUiApp) loadStackJobs() {
	session := *app.Session()
	if session == nil {
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
		return
	}

	app.Jobs = result.Items
}

// create a VM and add it to the given group
func (app *stackUiApp) addMachineToGroup(group string) {
	trimmedGroup := strings.TrimSpace(group)
	if trimmedGroup == "" {
		ucxsvc.UiSendFailure(app, "Please provide a node group")
		return
	}

	if app.Machine.Id == "" {
		ucxsvc.UiSendFailure(app, "Select a machine product before adding a node")
		return
	}

	existingJobs := shared.ClusterNodesByGroup(app.Jobs)[group]
	vmId, ok := shared.ClusterAddNode(app, app.Stack, shared.ClusterNodeSpec{
		Group:   group,
		Index:   len(existingJobs) + 1,
		Machine: app.Machine,
	}, existingJobs)
	if !ok {
		return
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Created new %s VM %s!", group, vmId))
	ucxsvc.RouterPushPage(app, "")
}

func (app *stackUiApp) UserInterface() ucx.UiNode {
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

	app.Nodes = nil
	for _, node := range nodes.Items {
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

		app.Nodes = append(app.Nodes, nodeRow{
			Name:  node.ObjectMeta.Name,
			Ready: ready,
			Ip:    ip,
		})
	}

	app.Pods = nil
	for _, pod := range pods.Items {
		app.Pods = append(app.Pods, podRow{
			Name:      pod.ObjectMeta.Name,
			Namespace: pod.ObjectMeta.Namespace,
			Phase:     fmt.Sprintf("%v", pod.Status.Phase),
			Node:      pod.Status.HostIP,
		})
	}

	app.Svcs = nil
	for _, svc := range svcs.Items {
		portList := []string{}
		for _, p := range svc.Spec.Ports {
			if p.NodePort != 0 {
				portList = append(portList, fmt.Sprintf("%d:%d/%s", p.Port, p.NodePort, p.Protocol))
			} else {
				portList = append(portList, fmt.Sprintf("%d/%s", p.Port, p.Protocol))
			}
		}

		app.Svcs = append(app.Svcs, svcRow{
			Name:      svc.ObjectMeta.Name,
			Namespace: svc.ObjectMeta.Namespace,
			Ports:     strings.Join(portList, ", "),
		})
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
			ucx.Tab("Nodes", ucx.IconHeroServer).Children(
				ucx.TableNode("nodes", []ucx.Option{
					{Key: "name", Value: "Name"},
					{Key: "ready", Value: "Ready"},
					{Key: "ip", Value: "IP"},
				}),
			),
			ucx.Tab("Pods", ucx.IconHeroServer).Children(
				ucx.TableNode("pods", []ucx.Option{
					{Key: "name", Value: "Name"},
					{Key: "namespace", Value: "Namespace"},
					{Key: "phase", Value: "Phase"},
					{Key: "node", Value: "Node"},
				}),
			),
			ucx.Tab("Services", ucx.IconHeroServer).Children(
				ucx.TableNode("svc", []ucx.Option{
					{Key: "name", Value: "Name"},
					{Key: "namespace", Value: "Namespace"},
					{Key: "ports", Value: "Ports"},
				}),
			),
		),
	)}
}

// "Add new VM" page
func (app *stackUiApp) pageControl() []ucx.UiNode {
	return []ucx.UiNode{ucx.Surface().Children(
		ucx.Toolbar().Children(
			ucx.H2("Add new virtual machine to the stack"),
			ucx.Link("").Children(ucx.Text("Back to overview")),
		),
		ucx.Text("Select a product and submit to add a new node to the stack."),

		ucx.Form("addNodeForm").On(ucx.UiEventSubmit, func(ev ucx.UiEvent) {
			app.addMachineToGroup("worker")
			ucx.AppUpdateUi(app)
		}).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
				// NOTE: multiple control planes currently don't work
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
	)}
}

func (app *stackUiApp) pageMain() []ucx.UiNode {
	groups := shared.ClusterNodeGroups(app.Jobs)
	var children []ucx.UiNode

	children = append(children,
		ucx.StackResources(),
		ucx.Surface().Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 32}).Children(
				ucx.Flex(ucx.FlexProps{Gap: 8}).Children(
					ucx.Button("downloadKubernetesConfig", "Download Kubernetes configuration", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackDownloadFile(app.Stack, shared.KubernetesConfigurationFileName)
					}),
					ucx.Button("copyKubernetesToken", "Copy Kubernetes authentication token", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackCopyFile(app.Stack, shared.KubernetesTokenFileName)
					}),
					ucx.Button("copyHeadlampToken", "Copy Headlamp authentication token", ucx.ColorSecondaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.StackCopyFile(app.Stack, shared.HeadlampTokenFileName)
					}),
					ucx.Link("cluster-info").Children(ucx.Button("cluster-information", "Show cluster information", ucx.ColorPrimaryMain)),
				),
			),
		),
	)

	if len(groups) == 0 {
		children = append(children, ucx.Text(fmt.Sprintf("No machines found with label %s", shared.StackGroupingLabel)))
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
							Label: shared.StackGroupingLabel,
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

		if app.prevRoute != app.RoutePath {
			app.prevRoute = app.RoutePath
			ucx.AppUpdateUi(app)
		}
	}
}
