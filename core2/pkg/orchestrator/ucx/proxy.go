package ucx

import (
	"context"
	"sync"
	"sync/atomic"

	ws "github.com/gorilla/websocket"
)

type ProxyRpcHandler func(ctx context.Context, payload map[string]Value) (status int, response map[string]Value)
type ProxyAuthHandler func(ctx context.Context, downstreamToken string) (allowed bool, upstreamToken string)
type ProxySysHelloHandler func(ctx context.Context, downstreamPayload string) string
type ProxyUpstreamSelector func(ctx context.Context, downstreamToken string, downstreamSysHello string) ProxyUpstreamSelection

type ProxyUpstreamSelection struct {
	Allowed          bool
	UpstreamUrl      string
	UpstreamToken    string
	UpstreamSysHello string
}

type Proxy struct {
	upstreamUrl string

	mu       sync.RWMutex
	handlers map[string]RpcHandler
	auth     ProxyAuthHandler
	sysHello ProxySysHelloHandler
	upstream ProxyUpstreamSelector

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

func (p *Proxy) RegisterAuthHandler(handler ProxyAuthHandler) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.auth = handler
}

func (p *Proxy) RegisterSysHelloHandler(handler ProxySysHelloHandler) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.sysHello = handler
}

func (p *Proxy) RegisterUpstreamSelector(selector ProxyUpstreamSelector) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.upstream = selector
}

func (p *Proxy) Run(ctx context.Context, downstream *ws.Conn) error {
	downstreamToken, ok := p.authenticateDownstream(downstream)
	if !ok {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		return nil
	}

	downstreamHelloFrame, ok := readInitialDownstreamSysHello(downstream)
	if !ok {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		return nil
	}

	downstreamHelloPayload := downstreamHelloFrame.SysHello.Payload
	selection := p.selectUpstream(ctx, downstreamToken, downstreamHelloPayload)
	if !selection.Allowed {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		return nil
	}

	upstream, _, err := ws.DefaultDialer.Dial(selection.UpstreamUrl, nil)
	if err != nil {
		return err
	}

	if !authenticateUpstream(upstream, selection.UpstreamToken) {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		_ = upstream.Close()
		return nil
	}

	downstreamHelloFrame.SysHello.Payload = selection.UpstreamSysHello
	encodedHello, err := FrameEncode(downstreamHelloFrame)
	if err != nil {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		_ = upstream.Close()
		return nil
	}

	if err := upstream.WriteMessage(ws.BinaryMessage, encodedHello); err != nil {
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		_ = upstream.Close()
		return nil
	}

	if err := downstream.WriteMessage(ws.TextMessage, []byte("OK")); err != nil {
		_ = downstream.Close()
		_ = upstream.Close()
		return nil
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

func (p *Proxy) authenticateDownstream(downstream *ws.Conn) (string, bool) {
	messageType, rawMessage, err := downstream.ReadMessage()
	if err != nil {
		return "", false
	}

	if messageType != ws.TextMessage {
		return "", false
	}

	return string(rawMessage), true
}

func authenticateUpstream(upstream *ws.Conn, token string) bool {
	if err := upstream.WriteMessage(ws.TextMessage, []byte(token)); err != nil {
		return false
	}

	messageType, rawMessage, err := upstream.ReadMessage()
	if err != nil {
		return false
	}

	if messageType != ws.TextMessage {
		return false
	}

	return string(rawMessage) == "OK"
}

func (p *Proxy) upstreamSysHelloPayload(ctx context.Context, downstreamPayload string) string {
	p.mu.RLock()
	handler := p.sysHello
	p.mu.RUnlock()

	if handler == nil {
		return downstreamPayload
	}

	return handler(ctx, downstreamPayload)
}

func (p *Proxy) selectUpstreamUrl(ctx context.Context, downstreamToken string, downstreamSysHello string) (string, bool) {
	_ = ctx
	_ = downstreamToken
	_ = downstreamSysHello

	p.mu.RLock()
	fallbackUrl := p.upstreamUrl
	p.mu.RUnlock()

	return fallbackUrl, true
}

func (p *Proxy) selectUpstream(ctx context.Context, downstreamToken string, downstreamSysHello string) ProxyUpstreamSelection {
	p.mu.RLock()
	selector := p.upstream
	p.mu.RUnlock()

	if selector != nil {
		selection := selector(ctx, downstreamToken, downstreamSysHello)
		if selection.UpstreamUrl == "" {
			selection.UpstreamUrl = p.upstreamUrl
		}
		if selection.UpstreamToken == "" {
			selection.UpstreamToken = downstreamToken
		}
		if selection.UpstreamSysHello == "" {
			selection.UpstreamSysHello = downstreamSysHello
		}
		return selection
	}

	allowed := true
	upstreamToken := downstreamToken

	p.mu.RLock()
	authHandler := p.auth
	p.mu.RUnlock()

	if authHandler != nil {
		allowed, upstreamToken = authHandler(ctx, downstreamToken)
	}

	if !allowed {
		return ProxyUpstreamSelection{Allowed: false}
	}

	upstreamUrl, ok := p.selectUpstreamUrl(ctx, downstreamToken, downstreamSysHello)
	if !ok {
		return ProxyUpstreamSelection{Allowed: false}
	}

	upstreamSysHello := p.upstreamSysHelloPayload(ctx, downstreamSysHello)

	return ProxyUpstreamSelection{
		Allowed:          true,
		UpstreamUrl:      upstreamUrl,
		UpstreamToken:    upstreamToken,
		UpstreamSysHello: upstreamSysHello,
	}
}

func readInitialDownstreamSysHello(downstream *ws.Conn) (Frame, bool) {
	for {
		messageType, rawMessage, err := downstream.ReadMessage()
		if err != nil {
			return Frame{}, false
		}

		if messageType != ws.BinaryMessage {
			return Frame{}, false
		}

		decoded, err := FrameDecode(rawMessage)
		if err != nil {
			continue
		}

		if decoded.Opcode != OpSysHello {
			return Frame{}, false
		}

		return decoded, true
	}
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
