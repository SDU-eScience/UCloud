package ucx

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
	"ucloud.dk/shared/pkg/log"
)

type AppHandler func(ctx context.Context, session *Session)
type SessionAuthHandler func(ctx context.Context, token string) bool

type Session struct {
	rawIncoming <-chan Frame
	incoming    chan Frame
	outgoing    chan<- Frame
	serverSeq   int64
	ctx         context.Context
	sentModel   map[string]Value

	mu             sync.RWMutex
	rpcHandlers    map[string]RpcHandler
	rpcPending     map[int64]chan rpcResponse
	rpcPendingDone bool
	startOnce      sync.Once
	modelMu        sync.Mutex
	uiHandlerMu    sync.RWMutex
	uiHandlers     map[string]map[string]UiEventHandler
	app            Application
}

func NewSession(outgoing chan<- Frame, incoming <-chan Frame) *Session {
	return NewSessionWithContext(context.Background(), outgoing, incoming)
}

func NewSessionWithContext(ctx context.Context, outgoing chan<- Frame, incoming <-chan Frame) *Session {
	s := &Session{
		rawIncoming: incoming,
		incoming:    make(chan Frame, 16),
		outgoing:    outgoing,
		serverSeq:   1,
		ctx:         ctx,
		rpcHandlers: map[string]RpcHandler{},
		rpcPending:  map[int64]chan rpcResponse{},
		uiHandlers:  map[string]map[string]UiEventHandler{},
	}
	s.Start()
	return s
}

func (s *Session) Start() {
	s.startOnce.Do(func() {
		go s.pumpIncoming()
	})
}

func (s *Session) Incoming() <-chan Frame {
	return s.incoming
}

func (s *Session) nextSeq() int64 {
	return atomic.AddInt64(&s.serverSeq, 1) - 1
}

func (s *Session) Send(frame Frame) {
	frame.Seq = s.nextSeq()
	s.outgoing <- frame
}

func (s *Session) sendWithSeq(frame Frame, seq int64) {
	frame.Seq = seq
	s.outgoing <- frame
}

func (s *Session) pumpIncoming() {
	defer close(s.incoming)
	defer s.failPendingRpc(context.Canceled)

	for {
		select {
		case <-s.ctx.Done():
			s.failPendingRpc(s.ctx.Err())
			return
		case frame, ok := <-s.rawIncoming:
			if !ok {
				s.failPendingRpc(errors.New("ucx: incoming transport closed"))
				return
			}

			handled := s.handleRpcFrame(frame)
			if handled {
				continue
			}

			select {
			case <-s.ctx.Done():
				s.failPendingRpc(s.ctx.Err())
				return
			case s.incoming <- frame:
			}
		}
	}
}

func (s *Session) SendUiMount(uiMount UiMount) {
	uiMount.Root = NormalizeUiTree(uiMount.Root)
	s.rebuildUiHandlers(uiMount.Root)

	s.modelMu.Lock()
	defer s.modelMu.Unlock()

	s.sentModel = cloneModel(uiMount.Model)

	s.Send(Frame{
		ReplyToSeq: 0,
		Opcode:     OpUiMount,
		UiMount:    uiMount,
	})
}

func (s *Session) DispatchUiEvent(ev UiEvent) bool {
	s.uiHandlerMu.RLock()
	perNode := s.uiHandlers[ev.NodeId]
	if perNode == nil {
		s.uiHandlerMu.RUnlock()
		return false
	}

	handler := perNode[ev.Event]
	s.uiHandlerMu.RUnlock()

	if handler == nil {
		return false
	}

	handler(s, ev)
	return true
}

func (s *Session) rebuildUiHandlers(root UiNode) {
	next := map[string]map[string]UiEventHandler{}
	collectUiHandlers(root, next)

	s.uiHandlerMu.Lock()
	s.uiHandlers = next
	s.uiHandlerMu.Unlock()
}

func collectUiHandlers(node UiNode, target map[string]map[string]UiEventHandler) {
	if len(node.handlers) > 0 {
		if _, exists := target[node.Id]; !exists {
			target[node.Id] = map[string]UiEventHandler{}
		}

		for event, handler := range node.handlers {
			if _, exists := target[node.Id][event]; exists {
				panic("ucx: duplicate event handler registration for node '" + node.Id + "' and event '" + event + "'")
			}
			target[node.Id][event] = handler
		}
	}

	for _, child := range node.ChildNodes {
		collectUiHandlers(child, target)
	}
}

func (s *Session) SendModel(newModel map[string]Value) {
	s.modelMu.Lock()
	defer s.modelMu.Unlock()

	previous := s.sentModel
	next := cloneModel(newModel)
	s.sendModelDiff(previous, next)
	s.sentModel = next
}

func (s *Session) sendModelDiff(before map[string]Value, after map[string]Value) {
	changes := map[string]Value{}
	for key, afterVal := range after {
		beforeVal, ok := before[key]
		if !ok || !ValuesEqual(beforeVal, afterVal) {
			changes[key] = afterVal
		}
	}

	for key := range before {
		if _, ok := after[key]; !ok {
			changes[key] = VNull()
		}
	}

	if len(changes) == 0 {
		return
	}

	s.Send(Frame{
		ReplyToSeq: 0,
		Opcode:     OpModelPatch,
		ModelPatch: ModelPatch{
			Changes: changes,
		},
	})
}

func cloneModel(input map[string]Value) map[string]Value {
	if input == nil {
		return map[string]Value{}
	}

	result := make(map[string]Value, len(input))
	for key, value := range input {
		result[key] = cloneValue(value)
	}

	return result
}

func cloneValue(v Value) Value {
	clone := v

	if v.List != nil {
		clone.List = make([]Value, len(v.List))
		for i, item := range v.List {
			clone.List[i] = cloneValue(item)
		}
	}

	if v.Object != nil {
		clone.Object = make(map[string]Value, len(v.Object))
		for key, item := range v.Object {
			clone.Object[key] = cloneValue(item)
		}
	}

	return clone
}

func RunAppRaw(ctx context.Context, handler AppHandler) {
	incoming := make(chan Frame, 16)
	outgoing := make(chan Frame, 16)
	session := NewSessionWithContext(ctx, incoming, outgoing)
	handler(ctx, session)
}

func RunAppWebSocket(conn *ws.Conn, ctx context.Context, authHandler SessionAuthHandler, handler AppHandler) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	if !performServerAuthHandshake(ctx, conn, authHandler) {
		_ = conn.Close()
		return
	}

	toWebsocket := make(chan Frame, 16)
	fromWebsocket := make(chan Frame, 16)

	done := make(chan struct{}, 3)

	go func() {
		defer close(fromWebsocket)
		defer func() { done <- struct{}{} }()

		pumpFramesFromWebsocket(ctx, conn, fromWebsocket)
		cancel()
	}()

	go func() {
		defer func() { done <- struct{}{} }()

		pumpFramesToWebsocket(ctx, conn, toWebsocket)
		cancel()
	}()

	go func() {
		defer close(toWebsocket)
		defer func() { done <- struct{}{} }()
		session := NewSessionWithContext(ctx, toWebsocket, fromWebsocket)
		handler(ctx, session)
		cancel()
	}()

	go func() {
		<-ctx.Done()
		_ = conn.Close()
	}()

	<-done
	<-done
	<-done
}

func RunAppWebSocketApplication(conn *ws.Conn, ctx context.Context, app Application) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	if err := conn.WriteMessage(ws.TextMessage, []byte("OK")); err != nil {
		return
	}

	toWebsocket := make(chan Frame, 16)
	fromWebsocket := make(chan Frame, 16)

	done := make(chan struct{}, 3)

	go func() {
		defer close(fromWebsocket)
		defer func() { done <- struct{}{} }()

		pumpFramesFromWebsocket(ctx, conn, fromWebsocket)
		cancel()
	}()

	go func() {
		defer func() { done <- struct{}{} }()

		pumpFramesToWebsocket(ctx, conn, toWebsocket)
		cancel()
	}()

	go func() {
		defer close(toWebsocket)
		defer func() { done <- struct{}{} }()

		session := NewSessionWithContext(ctx, toWebsocket, fromWebsocket)
		stateMu := app.Mutex()
		stateMu.Lock()
		sessionPtr := app.Session()
		*sessionPtr = session
		session.app = app
		app.OnInit()
		stateMu.Unlock()

		for {
			select {
			case <-ctx.Done():
				return
			case frame, ok := <-session.Incoming():
				if !ok {
					return
				}

				if frame.Opcode == OpSysHello {
					stateMu.Lock()
					if appWithSysHello, ok := app.(SysHelloAwareApplication); ok {
						appWithSysHello.OnSysHello(frame.SysHello.Payload)
					}
					ui := app.UserInterface()
					model, err := ValueMarshal(app)
					if err != nil {
						log.Warn("Failed to serialize model %#v: %s", app, err)
					} else {
						session.SendUiMount(UiMount{
							InterfaceId: "-",
							Root:        ui,
							Model:       model,
						})
					}
					stateMu.Unlock()
				} else if frame.Opcode == OpUiEvent {
					(func() {
						stateMu.Lock()
						defer stateMu.Unlock()

						if session.DispatchUiEvent(frame.UiEvent) {
							AppUpdateModel(app)
						} else {
							app.OnMessage(frame)
						}
					})()
				} else if frame.Opcode == OpModelInput {
					(func() {
						stateMu.Lock()
						defer stateMu.Unlock()

						if err := ApplyModelInput(app, frame.ModelInput); err != nil {
							return
						}

						app.OnMessage(frame)
						AppUpdateModel(app)
					})()
				}
			}
		}
	}()

	go func() {
		<-ctx.Done()
		_ = conn.Close()
	}()

	<-done
	<-done
	<-done
}

func performServerAuthHandshake(ctx context.Context, conn *ws.Conn, authHandler SessionAuthHandler) bool {
	messageType, rawMessage, err := conn.ReadMessage()
	if err != nil {
		return false
	}

	if messageType != ws.TextMessage {
		_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		return false
	}

	token := string(rawMessage)
	allowed := true
	if authHandler != nil {
		allowed = authHandler(ctx, token)
	}

	if !allowed {
		_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		return false
	}

	if err := conn.WriteMessage(ws.TextMessage, []byte("OK")); err != nil {
		return false
	}

	return true
}

func pumpFramesFromWebsocket(ctx context.Context, conn *ws.Conn, outgoing chan<- Frame) {
	for {
		messageType, rawMessage, err := conn.ReadMessage()
		if err != nil {
			return
		}

		if messageType != ws.BinaryMessage {
			continue
		}

		decoded, err := FrameDecode(rawMessage)
		if err != nil {
			continue
		}

		select {
		case <-ctx.Done():
			return
		case outgoing <- decoded:
		}
	}
}

func pumpFramesToWebsocket(ctx context.Context, conn *ws.Conn, incoming <-chan Frame) {
	canWrite := true

	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-incoming:
			if !ok {
				return
			}

			if !canWrite {
				continue
			}

			encoded, err := FrameEncode(msg)
			if err != nil {
				continue
			}

			err = conn.WriteMessage(ws.BinaryMessage, encoded)
			if err != nil {
				canWrite = false
				_ = conn.Close()
			}
		}
	}
}
