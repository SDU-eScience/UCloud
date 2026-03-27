package demo

import (
	"fmt"
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

func (app *demoApp) UserInterface() ucx.UiNode {
	session := app.session
	return ucx.Flex(ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxAlignItemsCenter).Children(
			ucx.Icon(ucx.IconHeroCake, ucx.ColorPrimaryMain, 20),
			ucx.H2(app.Title).Sx(ucx.SxColor(ucx.ColorPrimaryMain)),
		),
		ucx.Text("UI layout is mounted once and state streams in").Sx(ucx.SxColor(ucx.ColorTextSecondary)),

		ucx.InputText("jobName", "Job name", "Name your job", "jobName"),
		ucx.TextBound("errors.jobName").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.InputNumber("cpu", "CPU", "cpu", 1, 128),
		ucx.TextBound("errors.cpu").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.Checkbox("notify", "Notify me when the job starts", "notify", true),

		ucx.MachineTypeSelector("machine", "Machine type", "machine", ucx.MachineCapabilityVm),

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
			stack, ok := ucxsvc.StackCreate(app, stackId)

			if !ok {
				return
			}

			ucxsvc.StackWriteFile(stack, "join-token.txt", util.SecureToken())
			initLabels := ucxsvc.StackWriteInitScript(stack, `
				cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
			`)

			products, err := ucxapi.JobsRetrieveProducts.Invoke(session, util.Empty{})
			if err != nil || len(products) == 0 {
				ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not retrieve products: %s", err))
				return
			}

			selectedProduct := util.OptNone[accapi.ProductReference]()
			for _, supp := range products {
				if supp.Support.VirtualMachine.Enabled && supp.Product.Cpu == 1 {
					selectedProduct.Set(supp.Product.ToReference())
				}
			}

			if !selectedProduct.Present {
				ucxsvc.UiSendFailure(app, "Could not decide on a product!")
				return
			}

			linkAttachment := ucxsvc.PublicLinkCreate(stack, stackId)
			networkAttachment := ucxsvc.PrivateNetworkCreate(stack, stackId)

			for i := 1; i <= 3; i++ {
				attachments := []orcapi.AppParameterValue{
					networkAttachment,
				}

				if i == 1 {
					attachments = append(attachments, linkAttachment)
				}

				ucxsvc.VirtualMachineCreate(stack, ucxsvc.VirtualMachineSpec{
					Labels:      initLabels,
					Product:     selectedProduct.Value,
					Image:       ucxsvc.VmImageUbuntu24_04,
					Hostname:    fmt.Sprintf("controlplane-%v", i),
					Attachments: attachments,
				})
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
