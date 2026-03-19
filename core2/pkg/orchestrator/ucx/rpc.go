package ucx

import (
	"context"
	"errors"
	"fmt"
)

type Rpc[Req any, Resp any] struct {
	CallName string
}

func (r *Rpc[Req, Resp]) Invoke(session *Session, req Req) (Resp, error) {
	return r.InvokeEx(context.Background(), session, req)
}

func (r *Rpc[Req, Resp]) InvokeEx(ctx context.Context, session *Session, req Req) (Resp, error) {
	return RpcInvoke(ctx, session, *r, req)
}

func (r *Rpc[Req, Resp]) Handler(session *Session, handler func(ctx context.Context, request Req) (Resp, error)) {
	RpcHandle(session, *r, handler)
}

func (r *Rpc[Req, Resp]) HandlerProxy(proxy *Proxy, handler func(ctx context.Context, request Req) (Resp, error)) {
	proxy.RegisterRpcHandler(r.CallName, func(ctx context.Context, payload map[string]Value) (int, map[string]Value) {
		var request Req
		if err := ModelToStruct(payload, &request); err != nil {
			return RpcStatusBadRequest, map[string]Value{"error": VString(err.Error())}
		}

		response, err := handler(ctx, request)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return RpcStatusCanceled, map[string]Value{"error": VString(err.Error())}
			}
			return RpcStatusInternal, map[string]Value{"error": VString(err.Error())}
		}

		encoded, err := StructToModel(response)
		if err != nil {
			return RpcStatusInternal, map[string]Value{"error": VString(err.Error())}
		}

		return RpcStatusOk, encoded
	})
}

const (
	RpcStatusOk         = 0
	RpcStatusBadRequest = 1
	RpcStatusNotFound   = 2
	RpcStatusInternal   = 3
	RpcStatusCanceled   = 4
)

type RpcHandler func(ctx context.Context, payload map[string]Value) (status int, response map[string]Value)

type rpcResponse struct {
	frame Frame
	err   error
}

type RpcCallError struct {
	Status  int
	Payload map[string]Value
}

func (e RpcCallError) Error() string {
	msg := ValueAsString(e.Payload["error"])
	if msg == "" {
		return fmt.Sprintf("ucx rpc failed with status %d", e.Status)
	}
	return fmt.Sprintf("ucx rpc failed with status %d: %s", e.Status, msg)
}

func (s *Session) RegisterRpcHandler(callName string, handler RpcHandler) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.rpcHandlers[callName] = handler
}

func RpcHandle[Req any, Resp any](session *Session, rpc Rpc[Req, Resp], handler func(ctx context.Context, request Req) (Resp, error)) {
	session.RegisterRpcHandler(rpc.CallName, func(ctx context.Context, payload map[string]Value) (int, map[string]Value) {
		var request Req
		if err := ModelToStruct(payload, &request); err != nil {
			return RpcStatusBadRequest, map[string]Value{"error": VString(err.Error())}
		}

		response, err := handler(ctx, request)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return RpcStatusCanceled, map[string]Value{"error": VString(err.Error())}
			}
			return RpcStatusInternal, map[string]Value{"error": VString(err.Error())}
		}

		encoded, err := StructToModel(response)
		if err != nil {
			return RpcStatusInternal, map[string]Value{"error": VString(err.Error())}
		}

		return RpcStatusOk, encoded
	})
}

func RpcInvoke[Req any, Resp any](ctx context.Context, session *Session, rpc Rpc[Req, Resp], request Req) (Resp, error) {
	requestPayload, err := StructToModel(request)
	if err != nil {
		var zero Resp
		return zero, err
	}

	responsePayload, err := session.InvokeRpc(ctx, rpc.CallName, requestPayload)
	if err != nil {
		var zero Resp
		return zero, err
	}

	var result Resp
	if err := ModelToStruct(responsePayload, &result); err != nil {
		var zero Resp
		return zero, err
	}

	return result, nil
}

func (s *Session) InvokeRpc(ctx context.Context, callName string, request map[string]Value) (map[string]Value, error) {
	seq := s.nextSeq()
	resultCh := make(chan rpcResponse, 1)

	s.mu.Lock()
	if s.rpcPendingDone {
		s.mu.Unlock()
		return nil, context.Canceled
	}
	s.rpcPending[seq] = resultCh
	s.mu.Unlock()

	frame := Frame{
		ReplyToSeq:     0,
		Opcode:         OpRpcRequest,
		RpcRequestName: callName,
		RpcPayload:     request,
	}

	go s.sendWithSeq(frame, seq)

	select {
	case <-ctx.Done():
		s.removePendingRpc(seq)
		return nil, ctx.Err()
	case <-s.ctx.Done():
		s.removePendingRpc(seq)
		return nil, s.ctx.Err()
	case response := <-resultCh:
		if response.err != nil {
			return nil, response.err
		}
		if response.frame.RpcStatus != RpcStatusOk {
			return nil, RpcCallError{Status: response.frame.RpcStatus, Payload: response.frame.RpcPayload}
		}
		if response.frame.RpcPayload == nil {
			return map[string]Value{}, nil
		}
		return response.frame.RpcPayload, nil
	}
}

func (s *Session) handleRpcFrame(frame Frame) bool {
	switch frame.Opcode {
	case OpRpcRequest:
		s.dispatchRpcRequest(frame)
		return true
	case OpRpcResponse:
		s.dispatchRpcResponse(frame)
		return true
	default:
		return false
	}
}

func (s *Session) dispatchRpcRequest(frame Frame) {
	s.mu.RLock()
	handler := s.rpcHandlers[frame.RpcRequestName]
	s.mu.RUnlock()

	if handler == nil {
		go s.Send(Frame{
			ReplyToSeq: frame.Seq,
			Opcode:     OpRpcResponse,
			RpcStatus:  RpcStatusNotFound,
			RpcPayload: map[string]Value{"error": VString("rpc handler not found: " + frame.RpcRequestName)},
		})
		return
	}

	go func() {
		status, payload := handler(s.ctx, frame.RpcPayload)
		s.Send(Frame{
			ReplyToSeq: frame.Seq,
			Opcode:     OpRpcResponse,
			RpcStatus:  status,
			RpcPayload: payload,
		})
	}()
}

func (s *Session) dispatchRpcResponse(frame Frame) {
	s.mu.Lock()
	responseCh, ok := s.rpcPending[frame.ReplyToSeq]
	if ok {
		delete(s.rpcPending, frame.ReplyToSeq)
	}
	s.mu.Unlock()

	if !ok {
		return
	}

	select {
	case responseCh <- rpcResponse{frame: frame}:
	default:
	}
}

func (s *Session) removePendingRpc(seq int64) {
	s.mu.Lock()
	delete(s.rpcPending, seq)
	s.mu.Unlock()
}

func (s *Session) failPendingRpc(err error) {
	s.mu.Lock()
	if s.rpcPendingDone {
		s.mu.Unlock()
		return
	}

	pending := s.rpcPending
	s.rpcPending = map[int64]chan rpcResponse{}
	s.rpcPendingDone = true
	s.mu.Unlock()

	for _, ch := range pending {
		select {
		case ch <- rpcResponse{err: err}:
		default:
		}
	}
}
