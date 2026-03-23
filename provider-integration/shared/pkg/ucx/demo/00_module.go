package demo

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func RunDemoSession(info rpc.RequestInfo) (util.Empty, *util.HttpError) {
	conn := info.WebSocket

	defer util.SilentClose(conn)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	state := &demoState{
		InterfaceId: "job-create-demo",
		Title:       "UCX Create Prototype",
		JobName:     "",
		CPU:         4,
		Notify:      true,
		RpcMessage:  "hello from server",
		RpcStatus:   "",
		TodoDraft:   "",
		Todos:       []todoItem{},
		Errors:      map[string]string{},
		NextTodoId:  1,
	}

	stateMu := &state.Mu

	ucx.RunAppWebSocket(
		conn,
		ctx,
		func(ctx context.Context, token string) bool {
			return true
		},
		func(ctx context.Context, session *ucx.Session) {
			state.Session = session
			for {
				select {
				case <-ctx.Done():
					return
				case frame, ok := <-session.Incoming():
					if !ok {
						return
					}

					handleIncomingFrame(ctx, stateMu, state, frame, session)
				}
			}
		},
	)
	return util.Empty{}, nil
}

type todoItem struct {
	Id   string
	Text string
}

type demoState struct {
	InterfaceId       string `ucx:"-"`
	Title             string `ucx:"-"`
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
	NextTodoId        int64        `ucx:"-"`
	Mu                sync.Mutex   `ucx:"-"`
	Session           *ucx.Session `ucx:"-"`
}

func (s *demoState) UpdateModel() {
	s.Session.SendModel(s.modelMap())
}

func (s *demoState) uiMount() ucx.UiMount {
	return ucx.UiMount{
		InterfaceId: s.InterfaceId,
		Root:        s.uiTree(),
		Model:       s.modelMap(),
	}
}

func (s *demoState) uiTree() ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxAlignItemsCenter).Children(
			ucx.Icon(ucx.IconHeroCake, ucx.ColorPrimaryMain, 20),
			ucx.H2(s.Title).Sx(ucx.SxColor(ucx.ColorPrimaryMain)),
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
				ucx.ButtonEx("addTodo", "Add", ucx.ColorSecondaryMain, ucx.IconHeroPlus, "", "").On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
					draft := strings.TrimSpace(s.TodoDraft)
					if draft != "" {
						s.Todos = append(s.Todos, todoItem{
							Id:   strconv.FormatInt(s.NextTodoId, 10),
							Text: draft,
						})
						s.NextTodoId++
						s.TodoDraft = ""
					}
					s.Errors = validateState(s)
					s.SubmissionMessage = ""
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
				ucx.ButtonEx("removeTodo", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id").On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
					s.Mu.Lock()
					defer s.Mu.Unlock()

					id := strings.TrimSpace(ucx.ValueAsString(ev.Value))
					if id == "" {
						return
					}
					newTodos := make([]todoItem, 0, len(s.Todos))
					for _, it := range s.Todos {
						if it.Id != id {
							newTodos = append(newTodos, it)
						}
					}
					s.Todos = newTodos
					s.Errors = validateState(s)
					s.SubmissionMessage = ""
					s.UpdateModel()
				}),
			),
		),

		ucx.TextBound("errors.todos").Sx(ucx.SxColor(ucx.ColorErrorMain)),
		ucx.Button("submitForm", "Submit", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
			s.Mu.Lock()
			defer s.Mu.Unlock()

			s.Errors = validateState(s)
			if len(s.Errors) > 0 {
				s.SubmissionMessage = ""
				s.LastActionMessage = "Submit rejected by validation"
			} else {
				s.SubmissionMessage = fmt.Sprintf(
					"Submitted job '%s' with %d CPU and %d todo item(s). Notify=%t",
					s.JobName,
					s.CPU,
					len(s.Todos),
					s.Notify,
				)
				s.LastActionMessage = "Submit accepted"
			}

			s.UpdateModel()
		}),
		ucx.TextBound("validationMessage"),
		ucx.TextBound("lastActionMessage"),
		ucx.TextBound("submissionMessage").Sx(ucx.SxColor(ucx.ColorSuccessMain)),

		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).Children(
			ucx.Button("fnFrontend", "Frontend RPC", ucx.ColorSuccessMain).On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
				resp, err := ucxsvc.Frontend.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello frontend! %v", time.Now())})
				if err == nil {
					s.Mu.Lock()
					s.FnText = resp.Message
					s.Mu.Unlock()
					s.UpdateModel()
				}
			}),
			ucx.Button("fnCore", "Core RPC", ucx.ColorWarningMain).On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
				resp, err := ucxsvc.Core.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello core! %v", time.Now())})
				if err == nil {
					s.Mu.Lock()
					s.FnText = resp.Message
					s.Mu.Unlock()
					s.UpdateModel()
				}
			}),
			ucx.Button("fnIm", "IM RPC", ucx.ColorErrorMain).On(ucx.UiEventClick, func(session *ucx.Session, ev ucx.UiEvent) {
				resp, err := ucxsvc.IM.Invoke(session, ucxsvc.Message{fmt.Sprintf("Hello IM! %v", time.Now())})
				if err == nil {
					s.Mu.Lock()
					s.FnText = resp.Message
					s.Mu.Unlock()
					s.UpdateModel()
				}
			}),
		),
		ucx.TextBound("fnText"),
	)
}

func (s *demoState) modelMap() map[string]ucx.Value {
	validationMessage := "Fill out the form and add at least one todo item, then submit."
	if len(s.Errors) == 0 {
		validationMessage = "No validation errors."
	}
	s.ValidationMessage = validationMessage
	s.TodoHeader = fmt.Sprintf("Todo List (%d)", len(s.Todos))

	result, err := ucx.ValueMarshal(*s)
	if err != nil {
		return map[string]ucx.Value{}
	}

	return result
}

func handleIncomingFrame(ctx context.Context, mu *sync.Mutex, state *demoState, incoming ucx.Frame, session *ucx.Session) {
	switch incoming.Opcode {
	case ucx.OpSysHello:
		session.SendUiMount(state.uiMount())

	case ucx.OpModelInput:
		handleModelInput(state, incoming.ModelInput)

	case ucx.OpUiEvent:
		if session.DispatchUiEvent(incoming.UiEvent) {
			state.UpdateModel()
			return
		}
	}
}

func handleModelInput(state *demoState, input ucx.ModelInput) {
	state.Mu.Lock()
	defer state.Mu.Unlock()

	if err := ucx.ApplyModelInput(state, input); err != nil {
		return
	}

	state.JobName = strings.TrimSpace(state.JobName)
	state.NextTodoId = int64(len(state.Todos) + 1)

	state.Errors = validateState(state)
	state.SubmissionMessage = ""
	state.UpdateModel()
}

func validateState(state *demoState) map[string]string {
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
