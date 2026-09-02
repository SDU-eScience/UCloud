package inference

import (
	"container/list"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Audit logging for the inference API
// =====================================================================================================================
// The public inference endpoints are plain http.HandlerFuncs (see 00_module.go) and are therefore not covered by the
// automatic audit hook of the RPC framework. The playground reaches the same core functions over a websocket, so the
// audit trail would be split (or duplicated) if the API handlers logged on their own.
//
// To produce exactly one entry per logical call, the audit records are produced by the core functions (InferenceChat,
// InferenceChatStreaming, ...) rather than by the handlers. When the API middleware attached a per-request state to
// the context, the record is handed to that state and the middleware emits the entry when the handler returns.
// Without a state (e.g. the playground) the record is emitted directly by the core function.
//
// The /v1/responses adapter builds on top of InferenceChat. It suppresses the audit hook of the inner chat call and
// emits a single inference.responses entry for the whole logical call instead (see inferenceAuditSuppress). Background
// responses are audited at submission time; their completion is already persisted by the response store.
//
// Logs are stored in the normal audit logs.
//
// To prevent repeating chat requests from the entire thread on every call (causing O(n^2) usage) each row only store
// the new messages of the request plus a hash chain over the previous messages. These are kept in an LRU and a full
// chain is re-created in case of a miss.

const (
	inferenceAuditChainSeed       = "ucloud-inference-v1"
	inferenceAuditLruCapacity     = 32768
	inferenceAuditLruPrefixWindow = 16
)

var metricInferenceAuditChainMisses = promauto.NewCounterVec(prometheus.CounterOpts{
	Namespace: "ucloud_im",
	Subsystem: "inference",
	Name:      "audit_chain_misses_total",
	Help:      "Inference audit rows stored without a deduplication prefix by endpoint.",
}, []string{"endpoint"})

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

func inferenceAuditChainKey(owner apm.WalletOwner, username string) string {
	if username != "" {
		return username
	}
	return owner.Reference()
}

type inferenceAuditResponseWriter struct {
	http.ResponseWriter
	status int
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

// Hash-chain
// -------------------------------------------------------------------------------------------------------------------

var inferenceAuditChainLru = &inferenceAuditChainCache{
	Entries: list.New(),
	Index:   make(map[string]*list.Element, inferenceAuditLruCapacity),
}

type inferenceAuditChainCache struct {
	Mu      sync.Mutex
	Entries *list.List
	Index   map[string]*list.Element
}

func inferenceAuditChainSet(chainKey string, prefixHashes [][]byte) {
	lru := inferenceAuditChainLru
	lru.Mu.Lock()
	defer lru.Mu.Unlock()

	stored := 0
	for i := len(prefixHashes) - 1; i >= 0 && stored < inferenceAuditLruPrefixWindow; i-- {
		if lru.insertLocked(chainKey, prefixHashes[i]) {
			stored++
		}
	}
}

func inferenceAuditChainGet(chainKey string, prefixHashes [][]byte) int {
	lru := inferenceAuditChainLru
	lru.Mu.Lock()
	defer lru.Mu.Unlock()

	for i := len(prefixHashes) - 1; i >= 0; i-- {
		key := chainKey + "\x1f" + hex.EncodeToString(prefixHashes[i])
		if element, ok := lru.Index[key]; ok {
			lru.Entries.MoveToFront(element)
			return i
		}
	}
	return -1
}

func (lru *inferenceAuditChainCache) insertLocked(chainKey string, hash []byte) bool {
	key := chainKey + "\x1f" + hex.EncodeToString(hash)
	if element, ok := lru.Index[key]; ok {
		lru.Entries.MoveToFront(element)
		return false
	}

	for len(lru.Index) >= inferenceAuditLruCapacity {
		oldest := lru.Entries.Back()
		if oldest == nil {
			break
		}
		delete(lru.Index, oldest.Value.(string))
		lru.Entries.Remove(oldest)
	}

	lru.Index[key] = lru.Entries.PushFront(key)
	return true
}

func inferenceAuditChainPrefixes(items []json.RawMessage) [][]byte {
	prefixHashes := make([][]byte, len(items))
	chain := sha256.Sum256([]byte(inferenceAuditChainSeed))
	for i, item := range items {
		itemHash := sha256.Sum256(item)
		chain = sha256.Sum256(append(chain[:], itemHash[:]...))
		prefixHashes[i] = append([]byte(nil), chain[:]...)
	}
	return prefixHashes
}

type inferenceAuditDelta struct {
	PrevHash string
	FullHash string
	NewItems []json.RawMessage
}

func inferenceAuditComputeDelta(
	endpoint string,
	chainKey string,
	items []json.RawMessage,
) inferenceAuditDelta {
	delta := inferenceAuditDelta{NewItems: []json.RawMessage{}}
	if len(items) == 0 {
		return delta
	}

	prefixHashes := inferenceAuditChainPrefixes(items)
	delta.FullHash = hex.EncodeToString(prefixHashes[len(prefixHashes)-1])

	match := inferenceAuditChainGet(chainKey, prefixHashes)
	if match >= 0 {
		delta.PrevHash = hex.EncodeToString(prefixHashes[match])
		delta.NewItems = append(delta.NewItems, items[match+1:]...)
	} else {
		metricInferenceAuditChainMisses.WithLabelValues(endpoint).Inc()
		delta.NewItems = append(delta.NewItems, items...)
	}

	inferenceAuditChainSet(chainKey, prefixHashes)
	return delta
}

// Audit request bodies
// -------------------------------------------------------------------------------------------------------------------

func inferenceAuditChatBody(
	chainKey string,
	model string,
	history InferenceChatRequest,
	usage *InferenceChatUsage,
	reason string,
	aborted bool,
	source string,
) json.RawMessage {
	items := make([]json.RawMessage, 0, len(history.Messages))
	for _, message := range history.Messages {
		items = append(items, mustMarshal(message))
	}

	delta := inferenceAuditComputeDelta("inference.chat", chainKey, items)

	type auditChatBody struct {
		Model               string               `json:"model"`
		Stream              bool                 `json:"stream"`
		Source              string               `json:"source,omitempty"`
		Reason              string               `json:"reason,omitempty"`
		Aborted             bool                 `json:"aborted,omitempty"`
		PrevHash            string               `json:"prevHash,omitempty"`
		FullHash            string               `json:"fullHash"`
		MessageCount        int                  `json:"messageCount"`
		NewMessages         []json.RawMessage    `json:"newMessages"`
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
		PrevHash:            delta.PrevHash,
		FullHash:            delta.FullHash,
		MessageCount:        len(history.Messages),
		NewMessages:         delta.NewItems,
		Usage:               usage,
		Temperature:         history.Temperature,
		TopP:                history.TopP,
		MaxCompletionTokens: history.MaxCompletionTokens,
		ReasoningEffort:     history.ReasoningEffort,
		ToolCount:           len(history.Tools),
	})
}

func inferenceAuditOaiResponseBody(
	chainKey string,
	request OaiResponseCreateRequest,
	usage *InferenceChatUsage,
	reason string,
	source string,
) json.RawMessage {
	items := inferenceResponseStoreInputItems(request.Input)
	delta := inferenceAuditComputeDelta("inference.responses", chainKey, items)

	type auditOaiBody struct {
		Model              string              `json:"model"`
		Background         bool                `json:"background,omitempty"`
		Stream             bool                `json:"stream"`
		Source             string              `json:"source,omitempty"`
		Reason             string              `json:"reason,omitempty"`
		PrevHash           string              `json:"prevHash,omitempty"`
		FullHash           string              `json:"fullHash"`
		ItemCount          int                 `json:"itemCount"`
		NewItems           []json.RawMessage   `json:"newItems"`
		Instructions       json.RawMessage     `json:"instructions,omitempty"`
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
		PrevHash:           delta.PrevHash,
		FullHash:           delta.FullHash,
		ItemCount:          len(items),
		NewItems:           delta.NewItems,
		Instructions:       request.Instructions,
		Conversation:       request.Conversation,
		PreviousResponseID: request.PreviousResponseID,
		Usage:              usage,
	})
}

func inferenceAuditTranscribeBody(
	request InferenceTranscriptionRequest,
	usage *InferenceTranscriptionUsage,
	reason string,
) json.RawMessage {
	fileDigest := sha256.Sum256(request.File.Data)

	type auditTranscribeBody struct {
		Model          string                               `json:"model"`
		Stream         bool                                 `json:"stream"`
		Reason         string                               `json:"reason,omitempty"`
		FileName       string                               `json:"fileName,omitempty"`
		ContentType    string                               `json:"contentType,omitempty"`
		FileBytes      int                                  `json:"fileBytes"`
		FileSha256     string                               `json:"fileSha256"`
		Language       util.Option[string]                  `json:"language,omitempty"`
		Prompt         util.Option[string]                  `json:"prompt,omitempty"`
		ResponseFormat InferenceTranscriptionResponseFormat `json:"responseFormat"`
		Usage          *InferenceTranscriptionUsage         `json:"usage,omitempty"`
	}

	return mustMarshal(auditTranscribeBody{
		Model:          request.Model,
		Stream:         request.Stream,
		Reason:         reason,
		FileName:       request.File.Name,
		ContentType:    request.File.ContentType,
		FileBytes:      len(request.File.Data),
		FileSha256:     hex.EncodeToString(fileDigest[:]),
		Language:       request.Language,
		Prompt:         request.Prompt,
		ResponseFormat: request.ResponseFormat,
		Usage:          usage,
	})
}

func inferenceAuditImageBody(
	request InferenceImageGenerationRequest,
	usage *InferenceImageGenerationUsage,
	reason string,
) json.RawMessage {
	type auditImageBody struct {
		Model   string                         `json:"model,omitempty"`
		Stream  bool                           `json:"stream"`
		Reason  string                         `json:"reason,omitempty"`
		Prompt  string                         `json:"prompt"`
		Size    util.Option[string]            `json:"size,omitempty"`
		Quality util.Option[string]            `json:"quality,omitempty"`
		N       util.Option[int]               `json:"n,omitempty"`
		Usage   *InferenceImageGenerationUsage `json:"usage,omitempty"`
	}

	return mustMarshal(auditImageBody{
		Model:   request.Model.GetOrDefault(""),
		Stream:  request.Stream.GetOrDefault(false),
		Reason:  reason,
		Prompt:  request.Prompt,
		Size:    request.Size,
		Quality: request.Quality,
		N:       request.N,
		Usage:   usage,
	})
}

func mustMarshal(value any) json.RawMessage {
	encoded, err := json.Marshal(value)
	if err != nil {
		return json.RawMessage("{}")
	}
	return encoded
}
