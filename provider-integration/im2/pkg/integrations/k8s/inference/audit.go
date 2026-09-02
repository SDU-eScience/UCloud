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
	inferenceAuditChainSeed = "ucloud-inference-v1"

	// LRU sizing. The capacity counts user entries and is divided evenly across the shards.
	inferenceAuditLruCapacity      = 32768
	inferenceAuditLruShards        = 16
	inferenceAuditLruShardMask     = inferenceAuditLruShards - 1
	inferenceAuditLruShardCapacity = inferenceAuditLruCapacity / inferenceAuditLruShards

	// How many of a request's most recent prefix hashes are remembered per user and how many messages can be
	// appended in one call before the chain misses and the full history is stored again.
	inferenceAuditChainHashLimit  = 64
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

// Prefix hash-chain cache
// -------------------------------------------------------------------------------------------------------------------
// The cache keeps, per user (see inferenceAuditChainKey), a recency ordered list of the prefix hashes of the most
// recent messages seen, capped at inferenceAuditChainHashLimit. Matching a request reduces to finding the
// request's deepest prefix hash in that list: everything up to it was stored before and only the messages after it
// are new to the audit trail. The cap covers the recent window of a few interleaved conversations.
//
// The cache is sharded by chain key to spread lock contention. Entries are immutable and replaced by copy, so the
// matching scan runs on the published hash list outside the lock and compares fixed size arrays without allocating.

var inferenceAuditChainLru = newInferenceAuditChainCache()

type inferenceAuditChainCache struct {
	shards [inferenceAuditLruShards]inferenceAuditChainShard
}

type inferenceAuditChainShard struct {
	mu      sync.Mutex
	entries *list.List
	index   map[string]*list.Element
}

type inferenceAuditChainEntry struct {
	key    string
	hashes [][32]byte
}

func newInferenceAuditChainCache() *inferenceAuditChainCache {
	cache := &inferenceAuditChainCache{}
	for i := range cache.shards {
		cache.shards[i].entries = list.New()
		cache.shards[i].index = make(map[string]*list.Element, inferenceAuditLruShardCapacity)
	}
	return cache
}

func inferenceAuditChainShardOf(chainKey string) *inferenceAuditChainShard {
	sum := sha256.Sum256([]byte(chainKey))
	shard := &inferenceAuditChainLru.shards[sum[0]&inferenceAuditLruShardMask]
	return shard
}

func inferenceAuditChainGet(chainKey string, prefixHashes [][32]byte) int {
	shard := inferenceAuditChainShardOf(chainKey)

	shard.mu.Lock()
	element, ok := shard.index[chainKey]
	if !ok {
		shard.mu.Unlock()
		return -1
	}
	shard.entries.MoveToFront(element)
	hashes := element.Value.(*inferenceAuditChainEntry).hashes
	shard.mu.Unlock()

	for i := len(prefixHashes) - 1; i >= 0; i-- {
		prefix := prefixHashes[i]
		for _, hash := range hashes {
			if hash == prefix {
				return i
			}
		}
	}
	return -1
}

func inferenceAuditChainSet(chainKey string, prefixHashes [][32]byte) {
	windowStart := max(len(prefixHashes)-inferenceAuditLruPrefixWindow, 0)
	window := prefixHashes[windowStart:]

	// Most recent hash first so that overflowing the limit drops the oldest hashes.
	entry := &inferenceAuditChainEntry{
		key:    chainKey,
		hashes: make([][32]byte, 0, inferenceAuditChainHashLimit),
	}
	for i := len(window) - 1; i >= 0; i-- {
		entry.hashes = append(entry.hashes, window[i])
	}

	shard := inferenceAuditChainShardOf(chainKey)
	shard.mu.Lock()
	defer shard.mu.Unlock()

	if element, ok := shard.index[chainKey]; ok {
		for _, hash := range element.Value.(*inferenceAuditChainEntry).hashes {
			if len(entry.hashes) >= inferenceAuditChainHashLimit {
				break
			}

			contained := false
			for _, existing := range entry.hashes {
				if existing == hash {
					contained = true
					break
				}
			}
			if !contained {
				entry.hashes = append(entry.hashes, hash)
			}
		}

		shard.entries.MoveToFront(element)
		element.Value = entry
	} else {
		for len(shard.index) >= inferenceAuditLruShardCapacity {
			oldest := shard.entries.Back()
			if oldest == nil {
				break
			}
			delete(shard.index, oldest.Value.(*inferenceAuditChainEntry).key)
			shard.entries.Remove(oldest)
		}
		shard.index[chainKey] = shard.entries.PushFront(entry)
	}
}

func inferenceAuditChainPrefixes(items []json.RawMessage) [][32]byte {
	prefixHashes := make([][32]byte, len(items))
	chain := sha256.Sum256([]byte(inferenceAuditChainSeed))
	for i, item := range items {
		itemHash := sha256.Sum256(item)
		chain = sha256.Sum256(append(chain[:], itemHash[:]...))
		prefixHashes[i] = chain
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
	delta.FullHash = hex.EncodeToString(prefixHashes[len(prefixHashes)-1][:])

	match := inferenceAuditChainGet(chainKey, prefixHashes)
	if match >= 0 {
		delta.PrevHash = hex.EncodeToString(prefixHashes[match][:])
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

func mustMarshal(value any) json.RawMessage {
	encoded, err := json.Marshal(value)
	if err != nil {
		return json.RawMessage("{}")
	}
	return encoded
}
