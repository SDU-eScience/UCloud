package demo

import (
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"maps"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
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
					"Submitted job '%s' with %d CPU and %d todo item(s). Notify=%t",
					app.JobName,
					app.CPU,
					len(app.Todos),
					app.Notify,
				)
				app.LastActionMessage = "Submit accepted"
			}
		}),
		ucx.TextBound("validationMessage"),
		ucx.TextBound("lastActionMessage"),
		ucx.TextBound("submissionMessage").Sx(ucx.SxColor(ucx.ColorSuccessMain)),

		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).Children(
			ucx.Button("fnFrontend", "Frontend RPC", ucx.ColorSuccessMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxsvc.Frontend.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello frontend! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
			ucx.Button("fnCore", "Core RPC", ucx.ColorWarningMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxsvc.Core.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello core! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
			ucx.Button("fnIm", "IM RPC", ucx.ColorErrorMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				resp, err := ucxsvc.IM.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello IM! %v", time.Now())})
				if err == nil {
					app.FnText = resp.Message
				}
			}),
		),
		ucx.TextBound("fnText"),

		ucx.Button("stack", "Create a stack", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			stackId := app.JobName

			available, _ := ucxsvc.StackAvailable.Invoke(session, fndapi.FindByStringId{Id: stackId})
			if !available {
				log.Warn("Sending failure!")
				ucxsvc.UiSendFailure(session, "Kubernetes name is not available!")
				return
			}

			stackResp, err := ucxsvc.StackCreate.Invoke(session, ucxsvc.StackCreateRequest{
				StackId:   stackId,
				StackType: "Kubernetes",
			})

			if err != nil {
				ucxsvc.UiSendFailure(session, fmt.Sprintf("Could not create stack: %s", err))
				return
			}

			_, err = ucxsvc.StackDataWrite.Invoke(session, ucxsvc.StackDataWriteRequest{
				InstanceId: stackResp.InstanceId,
				Path:       "join-token.txt",
				Data:       util.SecureToken(),
				Perm:       0660,
			})
			if err != nil {
				ucxsvc.UiSendFailure(session, fmt.Sprintf("Could not write join token: %s", err))
				return
			}

			_, err = ucxsvc.StackDataWrite.Invoke(session, ucxsvc.StackDataWriteRequest{
				InstanceId: stackResp.InstanceId,
				Path:       "init.sh",
				Data:       "cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt",
				Perm:       0770,
			})
			if err != nil {
				ucxsvc.UiSendFailure(session, fmt.Sprintf("Could not write init script: %s", err))
				return
			}

			log.Info("Stack has been created: %#v", stackResp)

			products, err := ucxsvc.JobsRetrieveProducts.Invoke(session, util.Empty{})
			if err != nil || len(products) == 0 {
				ucxsvc.UiSendFailure(session, fmt.Sprintf("Could not retrieve products: %s", err))
				return
			}

			log.Info("Found products: %#v", products)

			selectedProduct := util.OptNone[accapi.ProductReference]()
			for _, supp := range products {
				if supp.Support.VirtualMachine.Enabled && supp.Product.Cpu == 1 {
					selectedProduct.Set(supp.Product.ToReference())
				}
			}

			if !selectedProduct.Present {
				ucxsvc.UiSendFailure(session, "Could not decide on a product!")
				return
			}

			linkProducts, _ := ucxsvc.PublicLinksRetrieveProducts.Invoke(session, util.Empty{})
			if len(linkProducts) == 0 {
				ucxsvc.UiSendFailure(session, "Could not decide on a product!")
				return
			}

			links, err := ucxsvc.PublicLinksCreate.Invoke(session, []orcapi.IngressSpecification{
				{
					Domain: fmt.Sprintf("%s%s%s", linkProducts[0].Support.Prefix, stackId, linkProducts[0].Support.Suffix),
					ResourceSpecification: orcapi.ResourceSpecification{
						Product: linkProducts[0].Product.ToReference(),
						Labels:  stackResp.Labels,
					},
				},
			})

			if len(links) == 0 || err != nil {
				ucxsvc.UiSendFailure(session, "Could not create a link!")
				return
			}

			linkAttachment := orcapi.AppParameterValueIngress(links[0].Id)

			networkProducts, _ := ucxsvc.PrivateNetworksRetrieveProducts.Invoke(session, util.Empty{})
			if len(networkProducts) == 0 {
				ucxsvc.UiSendFailure(session, "Could not decide on a product!")
				return
			}

			networks, err := ucxsvc.PrivateNetworksCreate.Invoke(session, []orcapi.PrivateNetworkSpecification{
				{
					Name:      stackId,
					Subdomain: stackId,
					ResourceSpecification: orcapi.ResourceSpecification{
						Product: networkProducts[0].Product.ToReference(),
						Labels:  stackResp.Labels,
					},
				},
			})

			if len(networks) == 0 || err != nil {
				ucxsvc.UiSendFailure(session, "Could not create a network!")
				return
			}

			networkAttachment := orcapi.AppParameterValuePrivateNetwork(networks[0].Id)

			jobLabels := maps.Clone(stackResp.Labels)
			jobLabels["ucloud.dk/initscript"] = "/etc/ucloud-stack/init.sh"

			for i := 1; i <= 3; i++ {
				attachments := []orcapi.AppParameterValue{
					stackResp.Mount,
					networkAttachment,
				}

				if i == 1 {
					attachments = append(attachments, linkAttachment)
				}

				_, serr := ucxsvc.JobsCreate.Invoke(session, []orcapi.JobSpecification{
					{
						ResourceSpecification: orcapi.ResourceSpecification{
							Product: selectedProduct.Value,
							Labels:  jobLabels,
						},
						Application: orcapi.NameAndVersion{
							Name:    "ubuntu-vm2",
							Version: "24.04b",
						},
						Replicas:  1,
						Hostname:  util.OptValue(fmt.Sprintf("controlplane-%v", i)),
						Name:      fmt.Sprintf("controlplane-%v", i),
						Resources: attachments,
					},
				})
				err = util.MergeError(err, serr)
			}

			if err != nil {
				ucxsvc.UiSendFailure(session, fmt.Sprintf("Failed to start cluster: %s", err))
				return
			}

			ucxsvc.StackConfirmAndOpen(session, stackId)
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
