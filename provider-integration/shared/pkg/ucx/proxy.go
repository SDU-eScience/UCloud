package ucx

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	ws "github.com/gorilla/websocket"
	"ucloud.dk/shared/pkg/log"
)

type ProxyRpcHandler func(ctx context.Context, payload map[string]Value) (status int, response map[string]Value)
type ProxyAuthHandler func(ctx context.Context, downstreamToken string) (allowed bool, upstreamToken string)
type ProxySysHelloHandler func(ctx context.Context, downstreamPayload string) string
type ProxyUpstreamSelector func(ctx context.Context, downstreamToken string, downstreamSysHello string) ProxyUpstreamSelection

type ProxyUpstreamSelection struct {
	Allowed               bool
	UpstreamUrl           string
	UpstreamToken         string
	UpstreamSysHello      string
	UpstreamTokenInBearer bool
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
		log.Warn("UCX proxy: downstream authentication failed")
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		return nil
	}

	downstreamHelloFrame, ok := readInitialDownstreamSysHello(downstream)
	if !ok {
		log.Warn("UCX proxy: missing/invalid initial downstream SysHello frame")
		_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
		_ = downstream.Close()
		return nil
	}

	downstreamHelloPayload := downstreamHelloFrame.SysHello.Payload
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	defer func() { _ = downstream.Close() }()

	downstreamIncoming := make(chan Frame, 32)
	downstreamOutgoing := make(chan Frame, 32)

	go func() {
		defer close(downstreamIncoming)
		for {
			messageType, rawMessage, err := downstream.ReadMessage()
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
			case downstreamIncoming <- decoded:
			}
		}
	}()

	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-downstreamOutgoing:
				if !ok {
					return
				}

				encoded, err := FrameEncode(msg)
				if err != nil {
					continue
				}

				if err := downstream.WriteMessage(ws.BinaryMessage, encoded); err != nil {
					cancel()
					return
				}
			}
		}
	}()

	connectedUpstream := (*ws.Conn)(nil)
	upstreamIncoming := (<-chan Frame)(nil)
	upstreamOutgoing := (chan Frame)(nil)
	upstreamClosed := (<-chan struct{})(nil)
	reconnectTimer := time.After(0)
	acknowledgedDownstream := false

	pendingDownstream := []Frame{}
	latestModel := map[string]Value{}
	hasMount := false
	rehydrateSnapshot := map[string]Value(nil)
	awaitingMountForRehydrate := false

	for {
		select {
		case <-ctx.Done():
			if upstreamOutgoing != nil {
				close(upstreamOutgoing)
			}
			if connectedUpstream != nil {
				_ = connectedUpstream.Close()
			}
			close(downstreamOutgoing)
			return nil

		case frame, ok := <-downstreamIncoming:
			if !ok {
				cancel()
				continue
			}

			if upstreamOutgoing == nil {
				pendingDownstream = append(pendingDownstream, frame)
				continue
			}

			select {
			case <-ctx.Done():
			case upstreamOutgoing <- frame:
			}

		case <-upstreamClosed:
			log.Warn("UCX proxy: upstream closed, forcing downstream reconnect for fresh auth")
			if upstreamOutgoing != nil {
				close(upstreamOutgoing)
			}
			if connectedUpstream != nil {
				_ = connectedUpstream.Close()
			}

			connectedUpstream = nil
			upstreamIncoming = nil
			upstreamOutgoing = nil
			upstreamClosed = nil
			cancel()
			continue

		case frame, ok := <-upstreamIncoming:
			if !ok {
				continue
			}

			switch frame.Opcode {
			case OpUiMount:
				hasMount = true
				latestModel = cloneModel(frame.UiMount.Model)

			case OpModelPatch:
				if !hasMount {
					hasMount = true
					latestModel = map[string]Value{}
				}

				for key, value := range frame.ModelPatch.Changes {
					if value.Kind == ValueNull {
						delete(latestModel, key)
					} else {
						latestModel[key] = cloneValue(value)
					}
				}
			}

			if p.handleProxyRpc(ctx, frame, upstreamOutgoing) {
				continue
			}

			select {
			case <-ctx.Done():
			case downstreamOutgoing <- frame:
			}

			if frame.Opcode == OpUiMount && awaitingMountForRehydrate && upstreamOutgoing != nil {
				sendRehydrateModel(ctx, p, upstreamOutgoing, rehydrateSnapshot, frame.UiMount.Root)
				awaitingMountForRehydrate = false
				rehydrateSnapshot = nil
			}

		case <-reconnectTimer:
			selection := p.selectUpstream(ctx, downstreamToken, downstreamHelloPayload)
			if !selection.Allowed {
				log.Warn("UCX proxy: upstream selector rejected connection")
				_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
				cancel()
				continue
			}

			header := http.Header(nil)
			if selection.UpstreamTokenInBearer {
				header = http.Header{"Authorization": []string{fmt.Sprintf("Bearer %s", selection.UpstreamToken)}}
			}

			upstream, _, err := ws.DefaultDialer.Dial(selection.UpstreamUrl, header)
			if err != nil {
				log.Warn("UCX proxy: failed to dial upstream %q: %v", selection.UpstreamUrl, err)
				reconnectTimer = time.After(1 * time.Second)
				continue
			}

			downstreamHelloFrame.SysHello.Payload = selection.UpstreamSysHello
			encodedHello, err := FrameEncode(downstreamHelloFrame)
			if err != nil {
				log.Warn("UCX proxy: failed to encode upstream SysHello: %v", err)
				_ = upstream.Close()
				reconnectTimer = time.After(1 * time.Second)
				continue
			}

			authResult := authenticateUpstream(upstream, selection.UpstreamToken, encodedHello)
			if authResult == upstreamAuthForbidden {
				log.Warn("UCX proxy: upstream authentication returned Forbidden")
				_ = downstream.WriteMessage(ws.TextMessage, []byte("Forbidden"))
				_ = upstream.Close()
				cancel()
				continue
			}
			if authResult != upstreamAuthOK {
				log.Warn("UCX proxy: upstream authentication failed, forcing downstream reconnect for fresh auth")
				_ = upstream.Close()
				cancel()
				continue
			}

			if !acknowledgedDownstream {
				if err := downstream.WriteMessage(ws.TextMessage, []byte("OK")); err != nil {
					log.Warn("UCX proxy: failed to send downstream authentication OK: %v", err)
					_ = upstream.Close()
					cancel()
					continue
				}
				acknowledgedDownstream = true
			}

			connectedUpstream = upstream
			upstreamIncoming, upstreamOutgoing, upstreamClosed = startUpstreamPumps(ctx, upstream)

			for _, pending := range pendingDownstream {
				select {
				case <-ctx.Done():
				case upstreamOutgoing <- pending:
				}
			}
			pendingDownstream = pendingDownstream[:0]

			reconnectTimer = nil
		}
	}
}

func (p *Proxy) authenticateDownstream(downstream *ws.Conn) (string, bool) {
	messageType, rawMessage, err := downstream.ReadMessage()
	if err != nil {
		log.Warn("UCX proxy: failed to read downstream auth frame: %v", err)
		return "", false
	}

	if messageType != ws.TextMessage {
		log.Warn("UCX proxy: downstream auth frame was not text")
		return "", false
	}

	return string(rawMessage), true
}

type upstreamAuthResult int

const (
	upstreamAuthOK upstreamAuthResult = iota
	upstreamAuthForbidden
	upstreamAuthFailed
)

func authenticateUpstream(upstream *ws.Conn, token string, encodedSysHello []byte) upstreamAuthResult {
	if err := upstream.WriteMessage(ws.TextMessage, []byte(token)); err != nil {
		log.Warn("UCX proxy: failed writing upstream auth token: %v", err)
		return upstreamAuthFailed
	}

	if err := upstream.WriteMessage(ws.BinaryMessage, encodedSysHello); err != nil {
		log.Warn("UCX proxy: failed writing upstream SysHello frame: %v", err)
		return upstreamAuthFailed
	}

	messageType, rawMessage, err := upstream.ReadMessage()
	if err != nil {
		log.Warn("UCX proxy: failed reading upstream auth response: %v", err)
		return upstreamAuthFailed
	}

	if messageType != ws.TextMessage {
		log.Warn("UCX proxy: upstream auth response was not text")
		return upstreamAuthFailed
	}

	message := string(rawMessage)
	if message == "OK" {
		return upstreamAuthOK
	}
	if message == "Forbidden" {
		return upstreamAuthForbidden
	}

	log.Warn("UCX proxy: unexpected upstream auth response %q", message)

	return upstreamAuthFailed
}

func startUpstreamPumps(ctx context.Context, conn *ws.Conn) (<-chan Frame, chan Frame, <-chan struct{}) {
	incoming := make(chan Frame, 32)
	outgoing := make(chan Frame, 32)
	closed := make(chan struct{})

	var closeOnce sync.Once
	signalClosed := func() {
		closeOnce.Do(func() {
			_ = conn.Close()
			close(closed)
		})
	}

	go func() {
		defer close(incoming)
		for {
			messageType, rawMessage, err := conn.ReadMessage()
			if err != nil {
				signalClosed()
				return
			}

			if messageType != ws.BinaryMessage {
				continue
			}

			decoded, err := FrameDecode(rawMessage)
			if err != nil {
				log.Warn("UCX proxy: failed to decode upstream frame: %v", err)
				continue
			}

			select {
			case <-ctx.Done():
				return
			case <-closed:
				return
			case incoming <- decoded:
			}
		}
	}()

	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case <-closed:
				return
			case msg, ok := <-outgoing:
				if !ok {
					return
				}

				encoded, err := FrameEncode(msg)
				if err != nil {
					log.Warn("UCX proxy: failed to encode upstream frame: %v", err)
					continue
				}

				if err := conn.WriteMessage(ws.BinaryMessage, encoded); err != nil {
					signalClosed()
					return
				}
			}
		}
	}()

	return incoming, outgoing, closed
}

func sendRehydrateModel(ctx context.Context, p *Proxy, upstreamOutgoing chan<- Frame, snapshot map[string]Value, root UiNode) {
	if len(snapshot) == 0 {
		return
	}

	bindPaths := collectInputBindPaths(root)
	for _, path := range bindPaths {
		value, ok := modelValueAtPath(snapshot, path)
		if !ok {
			continue
		}

		eventId := p.syntheticSeq()
		frame := Frame{
			Seq:        p.syntheticSeq(),
			ReplyToSeq: 0,
			Opcode:     OpModelInput,
			ModelInput: ModelInput{
				EventId: eventId,
				NodeId:  fmt.Sprintf("rehydrate:%s", path),
				Path:    path,
				Value:   cloneValue(value),
			},
		}

		select {
		case <-ctx.Done():
			return
		case upstreamOutgoing <- frame:
		}
	}
}

func collectInputBindPaths(root UiNode) []string {
	rehydratable := map[string]bool{
		"input_text":               true,
		"input_number":             true,
		"input_slider":             true,
		"inference_image_composer": true,
		"checkbox":                 true,
		"textarea":                 true,
		"select":                   true,
		"toggle":                   true,
		"radio_group":              true,
		"list":                     true,
	}

	seen := map[string]bool{}
	result := []string{}

	var walk func(node UiNode)
	walk = func(node UiNode) {
		if node.BindPath != "" && rehydratable[node.Component] && !strings.HasPrefix(node.BindPath, "./") {
			if !seen[node.BindPath] {
				seen[node.BindPath] = true
				result = append(result, node.BindPath)
			}
		}

		for _, child := range node.ChildNodes {
			walk(child)
		}
	}

	walk(root)
	return result
}

func modelValueAtPath(model map[string]Value, path string) (Value, bool) {
	if path == "" {
		return Value{}, false
	}

	if direct, ok := model[path]; ok {
		return direct, true
	}

	parts := strings.Split(path, ".")
	if len(parts) == 0 {
		return Value{}, false
	}

	current, ok := model[parts[0]]
	if !ok {
		return Value{}, false
	}

	for i := 1; i < len(parts); i++ {
		if current.Kind != ValueObject || current.Object == nil {
			return Value{}, false
		}

		next, ok := current.Object[parts[i]]
		if !ok {
			return Value{}, false
		}
		current = next
	}

	return current, true
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
			log.Warn("UCX proxy: failed to read downstream SysHello frame: %v", err)
			return Frame{}, false
		}

		if messageType != ws.BinaryMessage {
			log.Warn("UCX proxy: expected binary SysHello frame from downstream")
			return Frame{}, false
		}

		decoded, err := FrameDecode(rawMessage)
		if err != nil {
			log.Warn("UCX proxy: failed to decode downstream frame while waiting for SysHello: %v", err)
			continue
		}

		if decoded.Opcode != OpSysHello {
			log.Warn("UCX proxy: expected SysHello opcode, got %d", decoded.Opcode)
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
