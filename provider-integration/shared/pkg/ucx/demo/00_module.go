package demo

import (
	"context"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const (
	proxyAuthToken    = "ucx-demo-proxy-token"
	upstreamAuthToken = "ucx-demo-upstream-token"
	proxySysHello     = "ucx-demo-proxy-syshello"
)

func Init() {
	upstreamServer := &rpc.Server{
		Mux: http.NewServeMux(),
	}

	streamCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "ucxCreateDemo",
		Convention:  rpc.ConventionWebSocket,
		Roles:       rpc.RolesPublic,
	}

	streamCall.HandlerEx(upstreamServer, func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		return runDemoSession(info)
	})

	go func() {
		s := &http.Server{
			Addr:    fmt.Sprintf(":%v", 32912),
			Handler: upstreamServer.Mux,
		}
		_ = s.ListenAndServe()
	}()

	streamCall.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		proxyServer := ucx.NewProxy("ws://127.0.0.1:32912/api/ucxCreateDemo")
		proxyServer.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
			if downstreamToken != proxyAuthToken {
				return ucx.ProxyUpstreamSelection{Allowed: false}
			}

			return ucx.ProxyUpstreamSelection{
				Allowed:          true,
				UpstreamUrl:      "ws://127.0.0.1:32912/api/ucxCreateDemo",
				UpstreamToken:    upstreamAuthToken,
				UpstreamSysHello: proxySysHello,
			}
		})

		PingRpc.HandlerProxy(proxyServer, func(ctx context.Context, request PingRpcRequest) (PingRpcResponse, error) {
			return PingRpcResponse{
				Ok:   true,
				From: "proxy",
				Echo: fmt.Sprintf("Hello from proxy. You said: %s", request.Message),
			}, nil
		})

		err := proxyServer.Run(ctx, info.WebSocket)
		if err != nil {
			return util.Empty{}, util.HttpErr(http.StatusBadGateway, "%s", err.Error())
		}

		return util.Empty{}, nil
	})
}

func runDemoSession(info rpc.RequestInfo) (util.Empty, *util.HttpError) {
	conn := info.WebSocket

	defer util.SilentClose(conn)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	state := newDemoState()
	stateMu := sync.Mutex{}
	hasMounted := false

	ucx.RunAppWebSocket(conn, ctx, func(ctx context.Context, token string) bool {
		return token == upstreamAuthToken
	}, func(ctx context.Context, session *ucx.Session) {
		for {
			select {
			case <-ctx.Done():
				return
			case frame, ok := <-session.Incoming():
				if !ok {
					return
				}

				if !hasMounted && frame.Opcode == ucx.OpSysHello {
					stateMu.Lock()
					mount := state.uiMount()
					stateMu.Unlock()
					session.SendUiMount(mount)
					hasMounted = true
					continue
				}

				if !hasMounted {
					continue
				}

				handleIncomingFrame(ctx, &stateMu, state, frame, session)
			}
		}
	})
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
	NextTodoId        int64 `ucx:"-"`
}

func newDemoState() *demoState {
	return &demoState{
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
}

func (s *demoState) uiMount() ucx.UiMount {
	return ucx.UiMount{
		InterfaceId: s.InterfaceId,
		Root:        s.uiTree(),
		Model:       s.modelMap(),
	}
}

func (s *demoState) uiTree() ucx.UiNode {
	return ucx.Flex("root", ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.Flex("titleRow", ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxAlignItemsCenter).Children(
			ucx.Icon("titleIcon", ucx.IconHeroCake, ucx.ColorPrimaryMain, 20),
			ucx.H2("title", s.Title).Sx(ucx.SxColor(ucx.ColorPrimaryMain)),
		),
		ucx.Text("subtitle", "UI layout is mounted once and state streams in").Sx(ucx.SxColor(ucx.ColorTextSecondary)),

		ucx.InputText("jobName", "Job name", "Name your job", "jobName"),
		ucx.TextBound("jobName:error", "errors.jobName").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.InputNumber("cpu", "CPU", "cpu", 1, 128),
		ucx.TextBound("cpu:error", "errors.cpu").Sx(ucx.SxColor(ucx.ColorErrorMain)),

		ucx.Checkbox("notify", "Notify me when the job starts", "notify", true),

		ucx.Flex("rpcSection", ucx.FlexProps{Direction: "column", Gap: 6}).Children(
			ucx.Heading("rpcTitle", "Client RPC", 4),
			ucx.Flex("rpcInputRow", ucx.FlexProps{Gap: 8}).Children(
				ucx.InputText("rpcMessage", "Message", "Enter RPC message", "rpcMessage"),
				ucx.Button("rpcPing", "Ping client", ucx.ColorSecondaryMain),
			),
			ucx.TextBound("rpcStatus", "rpcStatus").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
		),

		ucx.Flex(
			"todoSection",
			ucx.FlexProps{
				Direction: "column",
				Gap:       6,
			},
		).Children(
			ucx.HeadingBound("todoHeader", "todoHeader", 4),
			ucx.Flex(
				"todoInputRow",
				ucx.FlexProps{Gap: 8},
			).Children(
				ucx.InputText("todoDraft", "New todo", "Add task", "todoDraft"),
				ucx.ButtonEx("addTodo", "Add", ucx.ColorSecondaryMain, ucx.IconHeroPlus, "", ""),
			),
		),

		ucx.List(
			"todoList",
			"todos",
			"No items yet.",
		).Sx(
			ucx.SxMt(4),
		).Children(
			ucx.Flex(
				"todoItemRow",
				ucx.FlexProps{Gap: 8},
			).Sx(
				ucx.SxAlignItemsCenter,
				ucx.SxJustifySpaceBetween,
			).Children(
				ucx.TextBound("todoItemText", "./text"),
				ucx.ButtonEx("removeTodo", "Remove", ucx.ColorErrorMain, ucx.IconHeroTrash, "", "./id"),
			),
		),

		ucx.TextBound("todos:error", "errors.todos").Sx(ucx.SxColor(ucx.ColorErrorMain)),
		ucx.Button("submitForm", "Submit", ucx.ColorPrimaryMain),
		ucx.TextBound("validationMessage", "validationMessage"),
		ucx.TextBound("lastActionMessage", "lastActionMessage"),
		ucx.TextBound("submissionMessage", "submissionMessage").Sx(ucx.SxColor(ucx.ColorSuccessMain)),
	)
}

func (s *demoState) modelMap() map[string]ucx.Value {
	validationMessage := "Fill out the form and add at least one todo item, then submit."
	if len(s.Errors) == 0 {
		validationMessage = "No validation errors."
	}
	s.ValidationMessage = validationMessage
	s.TodoHeader = fmt.Sprintf("Todo List (%d)", len(s.Todos))

	result, err := ucx.StructToModel(*s)
	if err != nil {
		return map[string]ucx.Value{}
	}

	return result
}

func handleIncomingFrame(ctx context.Context, mu *sync.Mutex, state *demoState, incoming ucx.Frame, session *ucx.Session) {
	mu.Lock()
	defer mu.Unlock()

	switch incoming.Opcode {
	case ucx.OpSysHello:
		session.SendUiMount(state.uiMount())

	case ucx.OpModelInput:
		before := state.modelMap()
		handleModelInput(state, incoming.ModelInput)
		after := state.modelMap()
		session.SendModelDiff(before, after)

	case ucx.OpUiEvent:
		if incoming.UiEvent.NodeId == "rpcPing" && incoming.UiEvent.Event == "click" {
			before := state.modelMap()
			message := strings.TrimSpace(state.RpcMessage)
			if message == "" {
				message = "hello from server"
			}

			state.RpcStatus = "Calling client RPC..."
			after := state.modelMap()
			session.SendModelDiff(before, after)

			go invokeClientPing(ctx, mu, state, session, message)
			return
		}

		before := state.modelMap()
		handleUiEvent(state, incoming.UiEvent)
		after := state.modelMap()
		session.SendModelDiff(before, after)
	}
}

type PingRpcRequest struct {
	Message string
}

type PingRpcResponse struct {
	Ok   bool
	From string
	Echo string
}

var PingRpc = ucx.Rpc[PingRpcRequest, PingRpcResponse]{CallName: "client.ping"}

func invokeClientPing(ctx context.Context, mu *sync.Mutex, state *demoState, session *ucx.Session, message string) {
	payload, err := PingRpc.Invoke(session, PingRpcRequest{Message: message})

	log.Info("Got response from UI: %v %v", payload, err)

	mu.Lock()
	defer mu.Unlock()

	before := state.modelMap()
	if err != nil {
		state.RpcStatus = fmt.Sprintf("RPC failed: %v", err)
	} else {
		ok := payload.Ok
		from := strings.TrimSpace(payload.From)
		echo := strings.TrimSpace(payload.Echo)
		if from == "" {
			from = "unknown"
		}
		if echo == "" {
			echo = message
		}
		state.RpcStatus = fmt.Sprintf("RPC result from %s (ok=%t): %s", from, ok, echo)
	}
	after := state.modelMap()
	session.SendModelDiff(before, after)
}

func handleModelInput(state *demoState, input ucx.ModelInput) {
	switch input.Path {
	case "jobName":
		state.JobName = strings.TrimSpace(ucx.ValueAsString(input.Value))
	case "cpu":
		state.CPU = ucx.ValueAsS64(input.Value)
	case "notify":
		state.Notify = ucx.ValueAsBool(input.Value)
	case "rpcMessage":
		state.RpcMessage = ucx.ValueAsString(input.Value)
	case "todoDraft":
		state.TodoDraft = ucx.ValueAsString(input.Value)
	case "todos":
		state.Todos = todosFromValue(input.Value)
		state.NextTodoId = int64(len(state.Todos) + 1)
	}

	state.Errors = validateState(state)
	state.SubmissionMessage = ""
}

func handleUiEvent(state *demoState, ev ucx.UiEvent) {
	switch {
	case ev.NodeId == "addTodo" && ev.Event == "click":
		draft := strings.TrimSpace(state.TodoDraft)
		if draft != "" {
			state.Todos = append(state.Todos, todoItem{
				Id:   strconv.FormatInt(state.NextTodoId, 10),
				Text: draft,
			})
			state.NextTodoId++
			state.TodoDraft = ""
		}
		state.Errors = validateState(state)
		state.SubmissionMessage = ""

	case ev.NodeId == "removeTodo" && ev.Event == "click":
		id := strings.TrimSpace(ucx.ValueAsString(ev.Value))
		if id == "" {
			break
		}
		newTodos := make([]todoItem, 0, len(state.Todos))
		for _, it := range state.Todos {
			if it.Id != id {
				newTodos = append(newTodos, it)
			}
		}
		state.Todos = newTodos
		state.Errors = validateState(state)
		state.SubmissionMessage = ""

	case ev.NodeId == "submitForm" && ev.Event == "click":
		state.Errors = validateState(state)
		if len(state.Errors) > 0 {
			state.SubmissionMessage = ""
			state.LastActionMessage = "Submit rejected by validation"
		} else {
			state.SubmissionMessage = fmt.Sprintf(
				"Submitted job '%s' with %d CPU and %d todo item(s). Notify=%t",
				state.JobName,
				state.CPU,
				len(state.Todos),
				state.Notify,
			)
			state.LastActionMessage = "Submit accepted"
		}
	}
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

func todosFromValue(v ucx.Value) []todoItem {
	if v.Kind != ucx.ValueList {
		return []todoItem{}
	}

	out := make([]todoItem, 0, len(v.List))
	for idx, item := range v.List {
		if item.Kind != ucx.ValueObject {
			continue
		}

		text := strings.TrimSpace(ucx.ValueAsString(item.Object["text"]))
		if text == "" {
			continue
		}

		id := strings.TrimSpace(ucx.ValueAsString(item.Object["id"]))
		if id == "" {
			id = strconv.Itoa(idx + 1)
		}

		out = append(out, todoItem{Id: id, Text: text})
	}

	return out
}
