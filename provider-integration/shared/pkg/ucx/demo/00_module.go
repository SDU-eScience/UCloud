package demo

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func Demo() ucx.Application {
	if os.Getenv("UCLOUD_JOB_ID") != "" {
		return StackUi()
	}

	return &demoApp{
		Title:      "UCX Create Prototype",
		JobName:    fmt.Sprintf("K8s-%s", util.RandomTokenNoTs(4)),
		CPU:        4,
		Notify:     true,
		RpcMessage: "hello from server",
		RpcStatus:  "",
		TodoDraft:  "",
		Todos:      []todoItem{},
		Errors:     map[string]string{},
		NextTodoId: 1,
	}
}

type todoItem struct {
	Id   string
	Text string
}

type demoApp struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`
	Title   string       `ucx:"-"`

	JobName           string
	CPU               int64
	Notify            bool
	RpcMessage        string
	RpcStatus         string
	TodoDraft         string
	Todos             []todoItem
	Errors            map[string]string
	SubmissionMessage string
	LastActionMessage string
	ValidationMessage string
	TodoHeader        string
	FnText            string
	Machine           accapi.ProductReference
	NextTodoId        int64 `ucx:"-"`
}

func (app *demoApp) Mutex() *sync.Mutex {
	return &app.mu
}

func (app *demoApp) Session() **ucx.Session {
	return &app.session
}

func (app *demoApp) OnInit() {
	// Nothing to do
}

const useVms = false

func (app *demoApp) UserInterface() ucx.UiNode {
	session := app.session

	machineCap := ucx.MachineCapabilityVm
	if !useVms {
		machineCap = ucx.MachineCapabilityDocker
	}

	return ucx.Flex(ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxAlignItemsCenter).Children(
			ucx.Icon(ucx.IconHeroCake, ucx.ColorPrimaryMain, 20),
			ucx.H2(fmt.Sprintf("%s (%v)", app.Title, os.Getenv("UCLOUD_JOB_ID"))).Sx(ucx.SxColor(ucx.ColorPrimaryMain)),
		),
		ucx.Text("UI layout is mounted once and state streams in").Sx(ucx.SxColor(ucx.ColorTextSecondary)),

		ucx.InputText("jobName", "Job name", "Name your job", "jobName"),
		ucx.TextBound("errors.jobName").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.InputNumber("cpu", "CPU", "cpu", 1, 128),
		ucx.TextBound("errors.cpu").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.Checkbox("notify", "Notify me when the job starts", "notify", true),

		ucx.MachineTypeSelector("machine", "Machine type", "machine", machineCap),

		ucx.Flex(
			ucx.FlexProps{
				Direction: "column",
				Gap:       6,
			},
		).Children(
			ucx.HeadingBound("todoHeader", 4),
			ucx.Flex(ucx.FlexProps{Gap: 8}).Children(
				ucx.InputText("todoDraft", "New todo", "Add task", "todoDraft"),
				ucx.ButtonEx("addTodo", "Add", ucx.ColorSecondaryMain, ucx.IconHeroPlus, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					draft := strings.TrimSpace(app.TodoDraft)
					if draft != "" {
						app.Todos = append(app.Todos, todoItem{
							Id:   strconv.FormatInt(app.NextTodoId, 10),
							Text: draft,
						})
						app.NextTodoId++
						app.TodoDraft = ""
					}
					app.Errors = validateState(app)
					app.SubmissionMessage = ""
				}),
			),
		),

		ucx.List(
			"todos",
			"No items yet.",
		).Sx(
			ucx.SxMt(4),
		).Children(
			ucx.Flex(ucx.FlexProps{Gap: 8}).Sx(
				ucx.SxAlignItemsCenter,
				ucx.SxJustifySpaceBetween,
			).Children(
				ucx.TextBoundEx("todoItemText", "./text"),
				ucx.ButtonEx("removeTodo", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
					id := strings.TrimSpace(ucx.ValueAsString(ev.Value))
					if id == "" {
						return
					}
					newTodos := make([]todoItem, 0, len(app.Todos))
					for _, it := range app.Todos {
						if it.Id != id {
							newTodos = append(newTodos, it)
						}
					}
					app.Todos = newTodos
					app.Errors = validateState(app)
					app.SubmissionMessage = ""
				}),
			),
		),

		ucx.TextBound("errors.todos").Sx(ucx.SxColor(ucx.ColorErrorMain)),
		ucx.Button("submitForm", "Submit", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			app.Errors = validateState(app)
			if len(app.Errors) > 0 {
				app.SubmissionMessage = ""
				app.LastActionMessage = "Submit rejected by validation"
			} else {
				app.SubmissionMessage = fmt.Sprintf(
					"Submitted job '%s' with %d CPU and %d todo item(s). Notify=%t. Machine = %#v",
					app.JobName,
					app.CPU,
					len(app.Todos),
					app.Notify,
					app.Machine,
				)
				app.LastActionMessage = "Submit accepted"
			}
		}),
		ucx.TextBound("validationMessage"),
		ucx.TextBound("lastActionMessage"),
		ucx.TextBound("submissionMessage").Sx(ucx.SxColor(ucx.ColorSuccessMain)),

		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).Children(
			ucx.Button("fnFrontend", "Frontend RPC", ucx.ColorSuccessMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxapi.Frontend.Invoke(session, ucxapi.Message{fmt.Sprintf("Hello frontend! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
			ucx.Button("fnCore", "Core RPC", ucx.ColorWarningMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxapi.Core.Invoke(session, ucxapi.Message{fmt.Sprintf("Hello core! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
			ucx.Button("fnIm", "IM RPC", ucx.ColorErrorMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxapi.IM.Invoke(session, ucxapi.Message{fmt.Sprintf("Hello IM! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
		),
		ucx.TextBound("fnText"),

		ucx.Button("stack", "Create a stack", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			stackId := app.JobName
			stack, ok := ucxsvc.StackCreate(app, stackId, "Kubernetes")

			if !ok {
				return
			}

			ucxsvc.StackWriteFile(stack, "join-token.txt", util.SecureToken())
			ucxsvc.StackWriteFileEx(stack, "demo-runner.sh", `
set -euo pipefail
BIN="/opt/ucloud/ucx-demo"
PID=""
LAST_MTIME=""
stop_bin() {
  if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
    kill "${PID}" || true
    wait "${PID}" || true
  fi
  PID=""
}

start_bin() {
  echo "Restarting application..."
  "${BIN}" 43201 &
  PID="$!"
}

trap 'stop_bin; exit 0' INT TERM

while true; do
  MTIME="$(stat -c %Y "${BIN}" 2>/dev/null || echo missing)"
  if [ "${MTIME}" != "${LAST_MTIME}" ]; then
    stop_bin
    start_bin
    LAST_MTIME="${MTIME}"
  elif [ -n "${PID}" ] && ! kill -0 "${PID}" 2>/dev/null; then
    start_bin
  fi
  sleep 1
done
`, 0755)
			initLabels := ucxsvc.StackWriteInitScript(stack, `
            	sudo mkdir -p /var/lib/ucloud || true     
				cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
				bash /etc/ucloud-stack/demo-runner.sh &
			`)

			selectedProduct := app.Machine
			if selectedProduct.Id == "" {
				ucxsvc.UiSendFailure(app, "Could not decide on a product!")
				return
			}

			linkAttachment := ucxsvc.PublicLinkCreate(stack, stackId, util.OptValue(8080))
			networkAttachment := ucxsvc.PrivateNetworkCreate(stack, stackId)

			for i := 1; i <= 3; i++ {
				nodeGroup := "worker"
				if i == 1 {
					nodeGroup = "control-plane"
				}

				attachments := []orcapi.AppParameterValue{
					networkAttachment,
				}

				if i == 1 {
					attachments = append(attachments, linkAttachment)
				}

				if useVms {
					ucxsvc.VirtualMachineCreate(stack, ucxsvc.VirtualMachineSpec{
						Labels: util.MapMerge(initLabels, map[string]string{
							"ucloud.dk/serviceForwardsTcp": "[8080]",
							stackGroupingLabel:             nodeGroup,
						}),
						Product:     selectedProduct,
						Image:       ucxsvc.VmImageUbuntu24_04,
						Hostname:    fmt.Sprintf("controlplane-%v", i),
						Attachments: attachments,
					})
				} else {
					attachments = append(attachments, stack.Mount())
					extraLabels := map[string]string{stackGroupingLabel: nodeGroup}
					if i == 1 {
						extraLabels["ucloud.dk/ucxport"] = "43201"
					}

					ucxsvc.JobCreate(stack, orcapi.JobSpecification{
						ResourceSpecification: orcapi.ResourceSpecification{
							Labels:  util.MapMerge(initLabels, extraLabels),
							Product: selectedProduct,
						},
						Application: orcapi.NameAndVersion{
							Name:    "test-app",
							Version: "4",
						},
						Name:           fmt.Sprintf("controlplane-%v", i),
						Hostname:       util.OptValue(fmt.Sprintf("controlplane-%v", i)),
						Replicas:       1,
						Resources:      attachments,
						TimeAllocation: util.OptValue(orcapi.SimpleDuration{Hours: 4}),
					})
				}
			}

			ucxsvc.StackConfirmAndOpen(stack)
		}),
	)
}

func (app *demoApp) OnMessage(msg ucx.Frame) {
	switch msg.Opcode {
	case ucx.OpModelInput:
		app.JobName = strings.TrimSpace(app.JobName)
		app.NextTodoId = int64(len(app.Todos) + 1)

		app.Errors = validateState(app)
		app.SubmissionMessage = ""
		ucx.AppUpdateModel(app)
	}
}

func validateState(state *demoApp) map[string]string {
	errors := map[string]string{}
	if len(strings.TrimSpace(state.JobName)) < 3 {
		errors["jobName"] = "Job name must be at least 3 characters"
	}
	if state.CPU < 1 || state.CPU > 128 {
		errors["cpu"] = "CPU must be between 1 and 128"
	}
	if len(state.Todos) == 0 {
		errors["todos"] = "Add at least one todo item"
	}
	return errors
}
