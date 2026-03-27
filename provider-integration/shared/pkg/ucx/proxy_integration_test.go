package ucx

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	ws "github.com/gorilla/websocket"
)

func TestProxyReconnectRequiresFreshDownstreamAuth(t *testing.T) {
	upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

	var mu sync.Mutex
	upstreamTokens := make([]string, 0, 2)
	upstreamConnCount := 0

	upstreamServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()

		messageType, authRaw, err := conn.ReadMessage()
		if err != nil {
			return
		}
		if messageType != ws.TextMessage {
			_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
			return
		}

		messageType, sysHelloRaw, err := conn.ReadMessage()
		if err != nil {
			return
		}
		if messageType != ws.BinaryMessage {
			_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
			return
		}

		frame, err := FrameDecode(sysHelloRaw)
		if err != nil || frame.Opcode != OpSysHello {
			_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
			return
		}

		token := string(authRaw)
		mu.Lock()
		upstreamConnCount++
		connIndex := upstreamConnCount
		upstreamTokens = append(upstreamTokens, token)
		mu.Unlock()

		if connIndex == 1 && token != "token-1" {
			_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
			return
		}
		if connIndex == 2 && token != "token-2" {
			_ = conn.WriteMessage(ws.TextMessage, []byte("Forbidden"))
			return
		}

		_ = conn.WriteMessage(ws.TextMessage, []byte("OK"))

		mount := Frame{
			Seq:        1,
			ReplyToSeq: 0,
			Opcode:     OpUiMount,
			UiMount: UiMount{
				InterfaceId: "test",
				Root:        UiNode{Id: "root", Component: "box"},
				Model:       map[string]Value{},
			},
		}
		encodedMount, _ := FrameEncode(mount)
		_ = conn.WriteMessage(ws.BinaryMessage, encodedMount)

		if connIndex == 1 {
			_ = conn.Close()
			return
		}

		time.Sleep(200 * time.Millisecond)
	}))
	defer upstreamServer.Close()

	upstreamWS := httpToWs(upstreamServer.URL)

	proxy := NewProxy("ws://pending")
	proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ProxyUpstreamSelection {
		_ = ctx
		_ = downstreamSysHello
		return ProxyUpstreamSelection{
			Allowed:          true,
			UpstreamUrl:      upstreamWS,
			UpstreamToken:    downstreamToken,
			UpstreamSysHello: downstreamSysHello,
		}
	})

	proxyServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		_ = proxy.Run(context.Background(), conn)
	}))
	defer proxyServer.Close()

	proxyWS := httpToWs(proxyServer.URL)

	client1 := openAndHandshakeDownstream(t, proxyWS, "token-1", "hello")
	_ = client1.SetReadDeadline(time.Now().Add(5 * time.Second))
	for {
		_, _, err := client1.ReadMessage()
		if err != nil {
			break
		}
	}
	_ = client1.Close()

	client2 := openAndHandshakeDownstream(t, proxyWS, "token-2", "hello")
	_ = client2.SetReadDeadline(time.Now().Add(2 * time.Second))
	_, _, _ = client2.ReadMessage()
	_ = client2.Close()

	deadline := time.Now().Add(5 * time.Second)
	for {
		mu.Lock()
		count := len(upstreamTokens)
		mu.Unlock()
		if count >= 2 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for second upstream auth; got tokens so far: %v", upstreamTokens)
		}
		time.Sleep(20 * time.Millisecond)
	}

	mu.Lock()
	defer mu.Unlock()
	if len(upstreamTokens) < 2 {
		t.Fatalf("expected 2 upstream auth attempts, got %d", len(upstreamTokens))
	}
	if upstreamTokens[0] != "token-1" || upstreamTokens[1] != "token-2" {
		t.Fatalf("expected fresh token on reconnect, got %v", upstreamTokens)
	}
}

func openAndHandshakeDownstream(t *testing.T, url string, token string, helloPayload string) *ws.Conn {
	t.Helper()

	conn, _, err := ws.DefaultDialer.Dial(url, nil)
	if err != nil {
		t.Fatalf("failed to dial proxy: %v", err)
	}

	if err := conn.WriteMessage(ws.TextMessage, []byte(token)); err != nil {
		_ = conn.Close()
		t.Fatalf("failed to send downstream token: %v", err)
	}

	helloFrame := Frame{
		Seq:        1,
		ReplyToSeq: 0,
		Opcode:     OpSysHello,
		SysHello:   SysHello{Payload: helloPayload},
	}
	encodedHello, err := FrameEncode(helloFrame)
	if err != nil {
		_ = conn.Close()
		t.Fatalf("failed to encode downstream syshello: %v", err)
	}

	if err := conn.WriteMessage(ws.BinaryMessage, encodedHello); err != nil {
		_ = conn.Close()
		t.Fatalf("failed to send downstream syshello: %v", err)
	}

	if err := conn.SetReadDeadline(time.Now().Add(5 * time.Second)); err != nil {
		_ = conn.Close()
		t.Fatalf("failed to set read deadline: %v", err)
	}

	messageType, raw, err := conn.ReadMessage()
	if err != nil {
		_ = conn.Close()
		t.Fatalf("failed to read downstream auth response: %v", err)
	}
	if messageType != ws.TextMessage || string(raw) != "OK" {
		_ = conn.Close()
		t.Fatalf("unexpected downstream auth response type=%d body=%q", messageType, string(raw))
	}

	_ = conn.SetReadDeadline(time.Time{})
	return conn
}

func httpToWs(url string) string {
	return strings.Replace(strings.Replace(url, "http://", "ws://", 1), "https://", "wss://", 1)
}
