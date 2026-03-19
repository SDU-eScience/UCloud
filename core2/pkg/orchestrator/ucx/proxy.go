package ucx

import (
	"context"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
)

type ProxyRpcHandler func(ctx context.Context, payload map[string]Value) (status int, response map[string]Value)

type Proxy struct {
	upstreamUrl string

	mu       sync.RWMutex
	handlers map[string]RpcHandler

	nextSyntheticSeq atomic.Int64
}

func NewProxy(upstreamUrl string) *Proxy {
	return &Proxy{
		upstreamUrl: upstreamUrl,
		handlers:    map[string]RpcHandler{},
	}
}

func (p *Proxy) RegisterRpcHandler(callName string, handler RpcHandler) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.handlers[callName] = handler
}

func (p *Proxy) Run(ctx context.Context, downstream *ws.Conn) error {
	upstream, _, err := ws.DefaultDialer.Dial(p.upstreamUrl, nil)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	defer func() { _ = downstream.Close() }()
	defer func() { _ = upstream.Close() }()

	downstreamIncoming := make(chan Frame, 16)
	downstreamOutgoing := make(chan Frame, 16)
	upstreamIncoming := make(chan Frame, 16)
	upstreamOutgoing := make(chan Frame, 16)

	go proxyReadFrames(ctx, cancel, downstream, downstreamIncoming)
	go proxyWriteFrames(ctx, cancel, downstream, downstreamOutgoing)
	go proxyReadFrames(ctx, cancel, upstream, upstreamIncoming)
	go proxyWriteFrames(ctx, cancel, upstream, upstreamOutgoing)

	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case frame, ok := <-downstreamIncoming:
				if !ok {
					cancel()
					return
				}

				select {
				case <-ctx.Done():
					return
				case upstreamOutgoing <- frame:
				}
			}
		}
	}()

	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case frame, ok := <-upstreamIncoming:
				if !ok {
					cancel()
					return
				}

				if p.handleProxyRpc(ctx, frame, upstreamOutgoing) {
					continue
				}

				select {
				case <-ctx.Done():
					return
				case downstreamOutgoing <- frame:
				}
			}
		}
	}()

	<-ctx.Done()
	return nil
}

func (p *Proxy) handleProxyRpc(ctx context.Context, frame Frame, upstreamOutgoing chan<- Frame) bool {
	if frame.Opcode != OpRpcRequest {
		return false
	}

	p.mu.RLock()
	handler := p.handlers[frame.RpcRequestName]
	p.mu.RUnlock()

	if handler == nil {
		return false
	}

	go func() {
		status, payload := handler(ctx, frame.RpcPayload)

		resp := Frame{
			Seq:        p.syntheticSeq(),
			ReplyToSeq: frame.Seq,
			Opcode:     OpRpcResponse,
			RpcStatus:  status,
			RpcPayload: payload,
		}

		select {
		case <-ctx.Done():
			return
		case upstreamOutgoing <- resp:
		}
	}()

	return true
}

func (p *Proxy) syntheticSeq() int64 {
	return p.nextSyntheticSeq.Add(-1)
}

func proxyReadFrames(ctx context.Context, cancel context.CancelFunc, conn *ws.Conn, outgoing chan<- Frame) {
	defer close(outgoing)

	for {
		messageType, rawMessage, err := conn.ReadMessage()
		if err != nil {
			cancel()
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

func proxyWriteFrames(ctx context.Context, cancel context.CancelFunc, conn *ws.Conn, incoming <-chan Frame) {
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-incoming:
			if !ok {
				cancel()
				return
			}

			encoded, err := FrameEncode(msg)
			if err != nil {
				continue
			}

			err = conn.WriteMessage(ws.BinaryMessage, encoded)
			if err != nil {
				cancel()
				return
			}
		}
	}
}
