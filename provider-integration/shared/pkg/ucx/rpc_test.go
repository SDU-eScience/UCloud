package ucx

import (
	"context"
	"testing"
	"time"
)

func TestSessionRoutesRpcWithoutBlockingIncomingFlow(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	outgoing := make(chan Frame, 16)
	incoming := make(chan Frame, 16)
	session := NewSessionWithContext(ctx, outgoing, incoming)

	session.RegisterRpcHandler("sleepy", func(ctx context.Context, payload map[string]Value) (int, map[string]Value) {
		time.Sleep(40 * time.Millisecond)
		return RpcStatusOk, map[string]Value{"ok": VBool(true)}
	})

	incoming <- Frame{
		Seq:            100,
		ReplyToSeq:     0,
		Opcode:         OpRpcRequest,
		RpcRequestName: "sleepy",
		RpcPayload:     map[string]Value{},
	}

	incoming <- Frame{
		Seq:        101,
		ReplyToSeq: 0,
		Opcode:     OpUiEvent,
		UiEvent: UiEvent{
			NodeId: "buttonA",
			Event:  "click",
			Value:  VNull(),
		},
	}

	select {
	case msg := <-session.Incoming():
		if msg.Opcode != OpUiEvent {
			t.Fatalf("expected ui event, got opcode %d", msg.Opcode)
		}
	case <-time.After(20 * time.Millisecond):
		t.Fatal("expected non-rpc frame to pass through quickly")
	}

	select {
	case response := <-outgoing:
		if response.Opcode != OpRpcResponse {
			t.Fatalf("expected rpc response, got opcode %d", response.Opcode)
		}
		if response.ReplyToSeq != 100 {
			t.Fatalf("expected replyToSeq 100, got %d", response.ReplyToSeq)
		}
	case <-time.After(200 * time.Millisecond):
		t.Fatal("expected rpc response to be sent")
	}
}

func TestInvokeRpcMatchesResponseAsynchronously(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	outgoing := make(chan Frame, 16)
	incoming := make(chan Frame, 16)
	session := NewSessionWithContext(ctx, outgoing, incoming)

	go func() {
		request := <-outgoing
		if request.Opcode != OpRpcRequest {
			return
		}

		time.Sleep(10 * time.Millisecond)
		incoming <- Frame{
			Seq:        700,
			ReplyToSeq: request.Seq,
			Opcode:     OpRpcResponse,
			RpcStatus:  RpcStatusOk,
			RpcPayload: map[string]Value{"result": VS64(42)},
		}
	}()

	result, err := session.InvokeRpc(ctx, "math.answer", map[string]Value{})
	if err != nil {
		t.Fatalf("invoke rpc failed: %v", err)
	}

	if ValueAsS64(result["result"]) != 42 {
		t.Fatalf("unexpected rpc result: %#v", result)
	}
}

func TestRpcInvokeSupportsBareArrayPayloads(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	outgoing := make(chan Frame, 16)
	incoming := make(chan Frame, 16)
	session := NewSessionWithContext(ctx, outgoing, incoming)

	rpc := Rpc[[]string, []string]{CallName: "echo.list"}

	go func() {
		request := <-outgoing
		if request.Opcode != OpRpcRequest {
			return
		}

		payload, ok := request.RpcPayload[""]
		if !ok {
			incoming <- Frame{
				Seq:        701,
				ReplyToSeq: request.Seq,
				Opcode:     OpRpcResponse,
				RpcStatus:  RpcStatusBadRequest,
				RpcPayload: map[string]Value{"error": VString("missing root payload")},
			}
			return
		}

		incoming <- Frame{
			Seq:        701,
			ReplyToSeq: request.Seq,
			Opcode:     OpRpcResponse,
			RpcStatus:  RpcStatusOk,
			RpcPayload: map[string]Value{"": payload},
		}
	}()

	result, err := RpcInvoke(ctx, session, rpc, []string{"a", "b"})
	if err != nil {
		t.Fatalf("invoke rpc failed: %v", err)
	}

	if len(result) != 2 || result[0] != "a" || result[1] != "b" {
		t.Fatalf("unexpected rpc result: %#v", result)
	}
}
