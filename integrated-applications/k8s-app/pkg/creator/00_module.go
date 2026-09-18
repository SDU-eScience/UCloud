package creator

import (
	"fmt"
	"strconv"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

func App() ucx.Application {
	return &k8sApp{
		WorkerNodes:       1,
		ControlPlaneNodes: 1,
		Ports:             "8080",
	}
}

type k8sApp struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`

	Machine           accapi.ProductReference
	WorkerNodes       int
	ControlPlaneNodes int
	Ports             string
}

func (app *k8sApp) Mutex() *sync.Mutex {
	return &app.mu
}

func (app *k8sApp) Session() **ucx.Session {
	return &app.session
}

func (app *k8sApp) OnInit() {
	// Nothing to do
}

func (app *k8sApp) UserInterface() ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.MachineTypeSelector("machine", "Machine type", "machine", ucx.MachineCapabilityVm),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 32}).Sx(ucx.SxP(4)).Children(
			// NOTE: multiple control planes currently don't work
			// ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(ucx.InputNumber("controlPlaneNodes", "Number of control planes", "controlPlaneNodes", 1, 32)),
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(ucx.InputNumber("workerNodes", "Number of workers", "workerNodes", 1, 32)),
		),
		ucx.InputText("ports", "Comma-separated list of exposed ports", "8080", "ports"),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 32}).Sx(ucx.SxP(4)).Children(
			ucx.Text("Reserved API port: 6443"),
			ucx.Text("Reserved Headlamp port: 30500"),
		),
		ucx.Button("stack", "Create a stack", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ports, ok := parsePorts(app.Ports)
			if !ok {
				ucxsvc.UiSendFailure(app, "Could not decode ports or a reserved port was used! Found list: "+app.Ports)
				return
			}

			if app.Machine.Id == "" {
				ucxsvc.UiSendFailure(app, "Could not decide on a product!")
				return
			}

			stackId := fmt.Sprintf("K8s-%v", util.RandomTokenNoTs(4))

			_, ok = shared.ClusterCreate(app, stackId, shared.ClusterSpec{
				Machine:           app.Machine,
				WorkerNodes:       app.WorkerNodes,
				ControlPlaneNodes: app.ControlPlaneNodes,
				Ports:             ports,
			})
			if !ok {
				return
			}
		}),
	)
}

func (app *k8sApp) OnMessage(msg ucx.Frame) {}

func parsePorts(ports string) ([]int, bool) {
	result := []int{}
	for _, p := range strings.Split(ports, ",") {
		if p == "" {
			continue
		}

		port, err := strconv.Atoi(p)
		if err != nil {
			return nil, false
		}

		if port == shared.ApiPort || port == shared.HeadlampPort {
			return nil, false
		}

		result = append(result, port)
	}
	return result, true
}
