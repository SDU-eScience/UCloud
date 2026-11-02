package inference

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Inference audit records
// =====================================================================================================================
// This file creates audit records for public HTTP endpoints and playground WebSocket calls. Public endpoints use
// http.HandlerFunc, so the standard RPC audit hook does not observe them.
//
// Core inference functions mark one record for each logical call. HTTP middleware owns request timing and emits the
// record after the handler returns. Calls without middleware, such as playground calls, emit the record directly.
//
// The /v1/responses adapter suppresses the inner chat record and emits one inference.responses record. Background
// responses record submission. The response store persists completion when storage is enabled. Read-only HTTP calls can
// finish without a record. Rejected calls record the rejection reason instead of a normal request body.
//
// The normal audit log stores the result. It does not store the message content.

// Audit request state
// ---------------------------------------------------------------------------------------------------------------------
// This section defines the request context keys and the HTTP response state. inferenceAuditMiddleware creates
// inferenceAuditState. The remaining audit entry points update or emit that state.
//
// The response writer records the first status. A write or flush without an explicit status records HTTP 200.

type inferenceAuditStateKey struct{}
type inferenceAuditSourceKey struct{}
type inferenceAuditSuppressKey struct{}

type inferenceAuditState struct {
	Recorded     bool
	Rejected     bool
	DefaultName  string
	RequestName  util.Option[string]
	RequestBody  json.RawMessage
	ReceivedAt   time.Time
	TokenId      string
	Username     string
	ProjectId    string
	RequestSize  uint64
	rejectReason string
}

func inferenceAuditActivityKey(state *inferenceAuditState) string {
	if state.Username != "" {
		return state.Username
	}
	return state.ProjectId
}

type inferenceAuditResponseWriter struct {
	http.ResponseWriter
	status int
}

// HTTP audit lifecycle
// ---------------------------------------------------------------------------------------------------------------------
// inferenceAuditMiddleware wraps public HTTP handlers. Identity and record entry points add data during request
// processing. The middleware emits after the handler returns, which gives it the final status and elapsed time.
// inferenceAuditRecord emits directly when no middleware state exists, as required by playground WebSocket calls.
//
// Suppressed calls and calls without an audit consumer do not create records. A request records only once. Rejection
// does not replace an existing record, and a normal record does not replace a rejection. Missing bodies become {}.
// Content-Length supplies request size only when the handler did not set a size. Unknown result reasons map to HTTP 500.

func inferenceAuditMiddleware(requestName string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		state := &inferenceAuditState{DefaultName: requestName, ReceivedAt: time.Now()}
		r = r.WithContext(context.WithValue(r.Context(), inferenceAuditStateKey{}, state))
		response := &inferenceAuditResponseWriter{ResponseWriter: w}
		startedAt := state.ReceivedAt

		next.ServeHTTP(response, r)

		if !state.Recorded && !state.Rejected {
			// Normal for read-only calls.
			return
		}

		if state.Rejected {
			state.RequestName.Set(state.DefaultName)
			state.RequestBody = mustMarshal(map[string]string{"reason": state.rejectReason})
		}

		if state.RequestSize == 0 {
			if contentLength, err := strconv.ParseUint(r.Header.Get("Content-Length"), 10, 64); err == nil {
				state.RequestSize = contentLength
			}
		}

		inferenceAuditEmit(state, r, response.status, time.Since(startedAt))
	}
}

func (w *inferenceAuditResponseWriter) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
	}
	w.ResponseWriter.WriteHeader(status)
}

func (w *inferenceAuditResponseWriter) Write(payload []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	return w.ResponseWriter.Write(payload)
}

func (w *inferenceAuditResponseWriter) Flush() {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	if flusher, ok := w.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (w *inferenceAuditResponseWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

func inferenceAuditIdentity(ctx context.Context, owner apm.WalletOwner, username string, tokenId string) {
	state, ok := ctx.Value(inferenceAuditStateKey{}).(*inferenceAuditState)
	if !ok || state == nil {
		return
	}

	state.TokenId = tokenId
	if username != "" {
		state.Username = username
	} else if owner.Username != "" {
		state.Username = owner.Username
	}
	if owner.ProjectId != "" {
		state.ProjectId = owner.ProjectId
	}
}

func inferenceAuditReject(ctx context.Context, reason string) {
	state, ok := ctx.Value(inferenceAuditStateKey{}).(*inferenceAuditState)
	if !ok || state == nil || state.Recorded {
		return
	}
	state.Rejected = true
	state.rejectReason = reason
}

func inferenceAuditRecord(
	ctx context.Context,
	requestName string,
	owner apm.WalletOwner,
	username string,
	startedAt time.Time,
	body json.RawMessage,
	reason string,
) {
	if rpc.AuditConsumer == nil || inferenceAuditSuppressed(ctx) {
		return
	}

	state, ok := ctx.Value(inferenceAuditStateKey{}).(*inferenceAuditState)
	if ok && state != nil {
		if state.Recorded || state.Rejected {
			return
		}
		state.Recorded = true
		state.RequestName.Set(requestName)
		state.RequestBody = body
		return
	}

	state = &inferenceAuditState{
		Recorded:    true,
		ReceivedAt:  startedAt,
		RequestBody: body,
	}
	state.RequestName.Set(requestName)
	if username != "" {
		state.Username = username
	} else if owner.Username != "" {
		state.Username = owner.Username
	}
	if owner.ProjectId != "" {
		state.ProjectId = owner.ProjectId
	}
	inferenceAuditEmit(state, nil, inferenceAuditStatusFromReason(reason), time.Since(startedAt))
}

func inferenceAuditEmit(state *inferenceAuditState, r *http.Request, status int, duration time.Duration) {
	if rpc.AuditConsumer == nil {
		return
	}

	if status < 400 {
		inferenceActivityRecord(inferenceAuditActivityKey(state), state.ReceivedAt)
	}

	event := rpc.HttpCallLogEntry{
		JobId:             util.RandomTokenNoTs(8),
		RequestName:       state.RequestName.GetOrDefault(state.DefaultName),
		ResponseCode:      status,
		ResponseTime:      uint64(duration.Milliseconds()),
		ResponseTimeNanos: uint64(duration.Nanoseconds()),
		ReceivedAt:        state.ReceivedAt,
	}
	if len(state.RequestBody) > 0 {
		event.RequestJson.Set(state.RequestBody)
	} else {
		event.RequestJson.Set(json.RawMessage("{}"))
	}
	event.RequestSize = state.RequestSize

	if r != nil {
		event.JobId = util.OptStringIfNotEmpty(r.Header.Get("Job-Id")).GetOrDefault(event.JobId)
		event.UserAgent = util.OptStringIfNotEmpty(r.Header.Get("User-Agent"))
		event.RemoteOrigin = util.ClientIP(r).String()
	}

	if state.Username != "" {
		token := rpc.SecurityPrincipalToken{
			Principal: rpc.SecurityPrincipal{
				Username: state.Username,
				Role:     rpc.RoleUser.String(),
			},
		}
		if state.TokenId != "" {
			token.PublicSessionReference.Set(state.TokenId)
		}
		event.Token.Set(token)
	}
	if state.ProjectId != "" {
		event.Project.Set(state.ProjectId)
	}

	rpc.AuditConsumer(event)
}

func inferenceAuditStatusFromReason(reason string) int {
	switch reason {
	case "", "success":
		return http.StatusOK
	case "payment_required":
		return http.StatusPaymentRequired
	case "admission_rejected":
		return http.StatusTooManyRequests
	case "client_error":
		return http.StatusBadRequest
	case "upstream_error":
		return http.StatusBadGateway
	default:
		return http.StatusInternalServerError
	}
}

// Audit context values
// ---------------------------------------------------------------------------------------------------------------------
// These context helpers carry the logical source and the suppression flag across inference adapters. Request body
// builders include the source in audit JSON. The responses adapter uses suppression to prevent a second chat record.
//
// Missing source values return an empty string. Missing suppression values return false. The typed private keys prevent
// collisions with context values from other packages.

func inferenceAuditSource(ctx context.Context, source string) context.Context {
	return context.WithValue(ctx, inferenceAuditSourceKey{}, source)
}

func inferenceAuditSourceOf(ctx context.Context) string {
	source, _ := ctx.Value(inferenceAuditSourceKey{}).(string)
	return source
}

func inferenceAuditSuppress(ctx context.Context) context.Context {
	return context.WithValue(ctx, inferenceAuditSuppressKey{}, true)
}

func inferenceAuditSuppressed(ctx context.Context) bool {
	suppressed, _ := ctx.Value(inferenceAuditSuppressKey{}).(bool)
	return suppressed
}

// Audit request bodies
// ---------------------------------------------------------------------------------------------------------------------
// These builders create the JSON stored for chat and responses calls. They include model settings, result data, usage,
// source, and item counts. inferenceAuditChatBody reads chat requests. inferenceAuditOaiResponseBody reads the
// normalized responses input items.
//
// Optional JSON fields stay absent when their source values are empty. `mustMarshal` provides the common JSON encoding
// path. It returns an empty JSON object if encoding fails so audit creation still has a valid request body.

func inferenceAuditChatBody(
	model string,
	history InferenceChatRequest,
	usage *InferenceChatUsage,
	reason string,
	aborted bool,
	source string,
) json.RawMessage {
	type auditChatBody struct {
		Model               string               `json:"model"`
		Stream              bool                 `json:"stream"`
		Source              string               `json:"source,omitempty"`
		Reason              string               `json:"reason,omitempty"`
		Aborted             bool                 `json:"aborted,omitempty"`
		MessageCount        int                  `json:"messageCount"`
		Usage               *InferenceChatUsage  `json:"usage,omitempty"`
		Temperature         util.Option[float64] `json:"temperature,omitempty"`
		TopP                util.Option[float64] `json:"top_p,omitempty"`
		MaxCompletionTokens util.Option[int]     `json:"max_completion_tokens,omitempty"`
		ReasoningEffort     util.Option[string]  `json:"reasoning_effort,omitempty"`
		ToolCount           int                  `json:"toolCount"`
	}

	return mustMarshal(auditChatBody{
		Model:               model,
		Stream:              history.Stream,
		Source:              source,
		Reason:              reason,
		Aborted:             aborted,
		MessageCount:        len(history.Messages),
		Usage:               usage,
		Temperature:         history.Temperature,
		TopP:                history.TopP,
		MaxCompletionTokens: history.MaxCompletionTokens,
		ReasoningEffort:     history.ReasoningEffort,
		ToolCount:           len(history.Tools),
	})
}

func inferenceAuditResponseItemCount(input json.RawMessage) int {
	if len(input) == 0 || string(input) == "null" {
		return 0
	}

	var items []json.RawMessage
	if err := json.Unmarshal(input, &items); err == nil {
		return len(items)
	}
	return 1
}

func inferenceAuditOaiResponseBody(
	request OaiResponseCreateRequest,
	usage *InferenceChatUsage,
	reason string,
	source string,
) json.RawMessage {
	itemCount := inferenceAuditResponseItemCount(request.Input)

	type auditOaiBody struct {
		Model              string              `json:"model"`
		Background         bool                `json:"background,omitempty"`
		Stream             bool                `json:"stream"`
		Source             string              `json:"source,omitempty"`
		Reason             string              `json:"reason,omitempty"`
		ItemCount          int                 `json:"itemCount"`
		Conversation       string              `json:"conversation,omitempty"`
		PreviousResponseID string              `json:"previous_response_id,omitempty"`
		Usage              *InferenceChatUsage `json:"usage,omitempty"`
	}

	return mustMarshal(auditOaiBody{
		Model:              request.Model,
		Background:         request.Background,
		Stream:             request.Stream,
		Source:             source,
		Reason:             reason,
		ItemCount:          itemCount,
		Conversation:       request.Conversation,
		PreviousResponseID: request.PreviousResponseID,
		Usage:              usage,
	})
}

func mustMarshal(value any) json.RawMessage {
	encoded, err := json.Marshal(value)
	if err != nil {
		return json.RawMessage("{}")
	}
	return encoded
}
