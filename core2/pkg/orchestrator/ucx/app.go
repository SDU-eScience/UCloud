package ucx

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
)

type AppHandler func(ctx context.Context, session *Session)

type Session struct {
	rawIncoming <-chan Frame
	incoming    chan Frame
	outgoing    chan<- Frame
	serverSeq   int64
	ctx         context.Context

	mu             sync.RWMutex
	rpcHandlers    map[string]RpcHandler
	rpcPending     map[int64]chan rpcResponse
	rpcPendingDone bool
	startOnce      sync.Once
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

func (s *Session) SendUiMount(replyTo int64, uiMount UiMount) {
	s.Send(Frame{
		ReplyToSeq: replyTo,
		Opcode:     OpUiMount,
		UiMount:    uiMount,
	})
}

func (s *Session) SendModelDiff(replyTo int64, version int64, before map[string]Value, after map[string]Value) {
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
		ReplyToSeq: replyTo,
		Opcode:     OpModelPatch,
		ModelPatch: ModelPatch{
			Version: version,
			Changes: changes,
		},
	})
}

func RunAppRaw(ctx context.Context, handler AppHandler) {
	incoming := make(chan Frame, 16)
	outgoing := make(chan Frame, 16)
	session := NewSessionWithContext(ctx, incoming, outgoing)
	handler(ctx, session)
}

func RunAppWebSocket(conn *ws.Conn, ctx context.Context, handler AppHandler) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

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
