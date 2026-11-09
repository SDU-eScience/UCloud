package ucx

import (
	"context"
	"errors"
	"fmt"
	"time"

	ws "github.com/gorilla/websocket"
)

const (
	clientConnectTimeout = 2 * time.Minute
	clientWriteTimeout   = 30 * time.Second
)

func Connect(ctx context.Context, url string, token string, sysHello string) (*Session, error) {
	conn, _, err := ws.DefaultDialer.DialContext(ctx, url, nil)
	if err != nil {
		return nil, fmt.Errorf("ucx: could not connect to %s: %w", url, err)
	}
	conn.SetReadLimit(maxFrameBytes)

	handshakeDone := false
	defer func() {
		if !handshakeDone {
			_ = conn.Close()
		}
	}()

	stopCloseOnCancel := context.AfterFunc(ctx, func() {
		_ = conn.Close()
	})
	defer stopCloseOnCancel()

	if err := writeClientMessage(conn, ws.TextMessage, []byte(token)); err != nil {
		return nil, fmt.Errorf("ucx: could not send the authentication token: %w", err)
	}

	hello := Frame{
		Seq:      1,
		Opcode:   OpSysHello,
		SysHello: SysHello{Payload: sysHello},
	}
	encodedHello, err := FrameEncode(hello)
	if err != nil {
		return nil, fmt.Errorf("ucx: could not encode the SysHello: %w", err)
	}

	if err := writeClientMessage(conn, ws.BinaryMessage, encodedHello); err != nil {
		return nil, fmt.Errorf("ucx: could not send the SysHello: %w", err)
	}

	deadline := time.Now().Add(clientConnectTimeout)
	if ctxDeadline, ok := ctx.Deadline(); ok && ctxDeadline.Before(deadline) {
		deadline = ctxDeadline
	}
	_ = conn.SetReadDeadline(deadline)

	messageType, message, err := conn.ReadMessage()
	_ = conn.SetReadDeadline(time.Time{})
	if err != nil {
		return nil, fmt.Errorf("ucx: the handshake failed: %w", err)
	}
	if messageType != ws.TextMessage {
		return nil, errors.New("ucx: the handshake response was not text")
	}

	switch string(message) {
	case "OK":
	case "Forbidden":
		return nil, errors.New("ucx: the connection was rejected")
	default:
		return nil, fmt.Errorf("ucx: unexpected handshake response %q", string(message))
	}

	stopCloseOnCancel()
	handshakeDone = true

	sessionCtx, cancelSession := context.WithCancel(ctx)
	outgoing := make(chan Frame, 256)
	incoming := make(chan Frame, 16)
	session := NewSessionWithContext(sessionCtx, outgoing, incoming)
	session.SetClientHints(parseClientHints(sysHello))

	session.mu.Lock()
	session.closeFn = func() {
		cancelSession()
		_ = conn.Close()
	}
	session.mu.Unlock()

	go func() {
		pumpFramesFromWebsocket(sessionCtx, conn, incoming)
		cancelSession()
	}()

	go func() {
		pumpFramesToWebsocket(sessionCtx, conn, outgoing)
		cancelSession()
	}()

	go func() {
		<-sessionCtx.Done()
		_ = conn.Close()
	}()

	return session, nil
}

func WaitForMount(ctx context.Context, session *Session) (UiMount, error) {
	if session == nil {
		return UiMount{}, errors.New("ucx: the session is nil")
	}

	for {
		select {
		case <-ctx.Done():
			return UiMount{}, ctx.Err()
		case <-session.Context().Done():
			return UiMount{}, session.Context().Err()
		case frame, ok := <-session.Incoming():
			if !ok {
				return UiMount{}, errors.New("ucx: the connection closed before the UI mount arrived")
			}
			if frame.Opcode == OpUiMount {
				return frame.UiMount, nil
			}
		}
	}
}

func writeClientMessage(conn *ws.Conn, messageType int, message []byte) error {
	_ = conn.SetWriteDeadline(time.Now().Add(clientWriteTimeout))
	return conn.WriteMessage(messageType, message)
}
