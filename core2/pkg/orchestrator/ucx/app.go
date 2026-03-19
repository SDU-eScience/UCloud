package ucx

import (
	"context"

	ws "github.com/gorilla/websocket"
)

type AppHandler func(ctx context.Context, incoming chan Frame, outgoing <-chan Frame)

func RunAppRaw(ctx context.Context, handler AppHandler) {
	incoming := make(chan Frame, 16)
	outgoing := make(chan Frame, 16)
	handler(ctx, incoming, outgoing)
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
		handler(ctx, toWebsocket, fromWebsocket)
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

func SendUiMount(outgoing chan<- Frame, serverSeq *int64, replyTo int64, uiMount UiMount) {
	mount := Frame{
		Seq:        *serverSeq,
		ReplyToSeq: replyTo,
		Opcode:     OpUiMount,
		UiMount:    uiMount,
	}
	*serverSeq = *serverSeq + 1
	outgoing <- mount
}

func SendModelDiff(outgoing chan<- Frame, serverSeq *int64, replyTo int64, version int64, before map[string]Value, after map[string]Value) {
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

	patch := Frame{
		Seq:        *serverSeq,
		ReplyToSeq: replyTo,
		Opcode:     OpModelPatch,
		ModelPatch: ModelPatch{
			Version: version,
			Changes: changes,
		},
	}
	*serverSeq = *serverSeq + 1
	outgoing <- patch
}
