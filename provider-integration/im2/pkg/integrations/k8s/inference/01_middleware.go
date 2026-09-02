package inference

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const inferenceChatCaptureUpstreamOutput = false
const inferenceChatReplayUpstreamOutputPath = ""

const (
	inferenceMaxUpstreamJSONBytes  = 1024 * 1024 * 8
	inferenceMaxSSEEventBytes      = 1024 * 1024 * 8
	inferenceResponseHeaderTimeout = 2 * time.Minute
	inferenceStreamIdleTimeout     = 2 * time.Minute
)

var inferenceHTTPClient = &http.Client{
	Transport: &http.Transport{
		Proxy:                 nil,
		DialContext:           (&net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
		TLSHandshakeTimeout:   10 * time.Second,
		ResponseHeaderTimeout: inferenceResponseHeaderTimeout,
		IdleConnTimeout:       90 * time.Second,
	},
}

var inferenceMissingUsageWarnings = struct {
	sync.Mutex
	Last map[string]time.Time
}{Last: map[string]time.Time{}}

func inferenceWarnMissingUsage(kind string, model string) {
	key := kind + "\n" + model
	now := time.Now()
	inferenceMissingUsageWarnings.Lock()
	last := inferenceMissingUsageWarnings.Last[key]
	if now.Sub(last) >= time.Minute {
		inferenceMissingUsageWarnings.Last[key] = now
		inferenceMissingUsageWarnings.Unlock()
		log.Warn("Inference upstream omitted usage: kind=%s model=%s", kind, model)
		return
	}
	inferenceMissingUsageWarnings.Unlock()
}

func inferenceSend[T any](ctx context.Context, ch chan<- T, value T) bool {
	select {
	case ch <- value:
		return true
	case <-ctx.Done():
		return false
	}
}

func inferenceStreamContext(parent context.Context) (context.Context, context.CancelFunc, func()) {
	ctx, cancel := context.WithCancel(parent)
	timer := time.AfterFunc(inferenceStreamIdleTimeout, cancel)
	touch := func() {
		timer.Reset(inferenceStreamIdleTimeout)
	}
	return ctx, func() {
		timer.Stop()
		cancel()
	}, touch
}

func inferenceReadSSE(ctx context.Context, body io.Reader, touch func(), handle func([]byte) bool) error {
	scanner := bufio.NewScanner(body)
	scanner.Buffer(make([]byte, 64<<10), inferenceMaxSSEEventBytes)
	var event bytes.Buffer
	flush := func() bool {
		if event.Len() == 0 {
			return true
		}
		data := append([]byte(nil), event.Bytes()...)
		event.Reset()
		return handle(data)
	}
	for scanner.Scan() {
		touch()
		line := bytes.TrimSuffix(scanner.Bytes(), []byte{'\r'})
		if len(line) == 0 {
			if !flush() {
				return ctx.Err()
			}
			continue
		}
		if event.Len()+len(line)+1 > inferenceMaxSSEEventBytes {
			return fmt.Errorf("SSE event exceeds limit")
		}
		event.Write(line)
		event.WriteByte('\n')
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	if !flush() {
		return ctx.Err()
	}
	return nil
}

// Models
// =====================================================================================================================

type OaiInferenceModel struct {
	Id                     string                 `json:"id"`
	Object                 string                 `json:"object"`
	OwnedBy                string                 `json:"owned_by,omitempty"`
	Capabilities           []InferenceCapability  `json:"capabilities,omitempty"`
	ContextWindow          *int                   `json:"context_window,omitempty"`
	ReasoningEfforts       []InferenceModelOption `json:"reasoning_efforts,omitempty"`
	DefaultReasoningEffort string                 `json:"default_reasoning_effort,omitempty"`
}

type OaiInferenceModelsResponse struct {
	Object string                `json:"object"`
	Data   []OaiInferenceModel   `json:"data"`
	Models []CodexInferenceModel `json:"models,omitempty"`
}

type CodexInferenceModel struct {
	Slug                           string                  `json:"slug"`
	DisplayName                    string                  `json:"display_name"`
	Description                    *string                 `json:"description"`
	DefaultReasoningLevel          *string                 `json:"default_reasoning_level"`
	SupportedReasoningLevels       []CodexReasoningEffort  `json:"supported_reasoning_levels"`
	ShellType                      string                  `json:"shell_type"`
	Visibility                     string                  `json:"visibility"`
	SupportedInApi                 bool                    `json:"supported_in_api"`
	Priority                       int                     `json:"priority"`
	AdditionalSpeedTiers           []string                `json:"additional_speed_tiers"`
	ServiceTiers                   []CodexModelServiceTier `json:"service_tiers"`
	DefaultServiceTier             *string                 `json:"default_service_tier"`
	AvailabilityNux                any                     `json:"availability_nux"`
	Upgrade                        any                     `json:"upgrade"`
	BaseInstructions               string                  `json:"base_instructions"`
	IncludeSkillsUsageInstructions bool                    `json:"include_skills_usage_instructions"`
	SupportsReasoningSummaries     bool                    `json:"supports_reasoning_summaries"`
	DefaultReasoningSummary        string                  `json:"default_reasoning_summary"`
	SupportVerbosity               bool                    `json:"support_verbosity"`
	DefaultVerbosity               *string                 `json:"default_verbosity"`
	ApplyPatchToolType             string                  `json:"apply_patch_tool_type"`
	WebSearchToolType              string                  `json:"web_search_tool_type"`
	TruncationPolicy               CodexTruncationPolicy   `json:"truncation_policy"`
	SupportsParallelToolCalls      bool                    `json:"supports_parallel_tool_calls"`
	SupportsImageDetailOriginal    bool                    `json:"supports_image_detail_original"`
	ContextWindow                  *int                    `json:"context_window,omitempty"`
	MaxContextWindow               *int                    `json:"max_context_window,omitempty"`
	AutoCompactTokenLimit          *int                    `json:"auto_compact_token_limit"`
	CompHash                       *string                 `json:"comp_hash,omitempty"`
	EffectiveContextWindowPercent  int                     `json:"effective_context_window_percent"`
	ExperimentalSupportedTools     []string                `json:"experimental_supported_tools"`
	InputModalities                []string                `json:"input_modalities"`
	SupportsSearchTool             bool                    `json:"supports_search_tool"`
	UseResponsesLite               bool                    `json:"use_responses_lite"`
	AutoReviewModelOverride        *string                 `json:"auto_review_model_override,omitempty"`
}

type CodexReasoningEffort struct {
	Effort      string `json:"effort"`
	Description string `json:"description"`
}

type CodexModelServiceTier struct {
	Id          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
}

type CodexTruncationPolicy struct {
	Mode  string `json:"mode"`
	Limit int    `json:"limit"`
}

func OaiInferenceModels(owner apm.WalletOwner) (OaiInferenceModelsResponse, *util.HttpError) {
	models := InferenceModelListForOwner(owner)
	resp := OaiInferenceModelsResponse{
		Object: "list",
		Data:   make([]OaiInferenceModel, 0, len(models)),
		Models: make([]CodexInferenceModel, 0, len(models)),
	}
	for idx, model := range models {
		resp.Data = append(resp.Data, inferenceOaiModelFromCatalog(model))
		resp.Models = append(resp.Models, inferenceCodexModelFromCatalog(model, idx))
	}
	return resp, nil
}

func OaiInferenceModelByID(owner apm.WalletOwner, id string) (OaiInferenceModel, *util.HttpError) {
	if strings.TrimSpace(id) == "" {
		return OaiInferenceModel{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	model, ok := InferenceCatalogModelByName(id)
	if !ok {
		return OaiInferenceModel{}, util.HttpErr(http.StatusNotFound, "model not found")
	}
	if !inferenceModelAvailableToOwner(model, owner) {
		return OaiInferenceModel{}, util.HttpErr(http.StatusForbidden, "forbidden")
	}
	return inferenceOaiModelFromCatalog(model), nil
}

func inferenceOaiModelFromCatalog(model InferenceModel) OaiInferenceModel {
	return OaiInferenceModel{
		Id:                     model.Name,
		Object:                 "model",
		OwnedBy:                "ucloud",
		Capabilities:           model.Capabilities,
		ContextWindow:          model.ContextWindow,
		ReasoningEfforts:       model.ReasoningEfforts,
		DefaultReasoningEffort: model.DefaultReasoningEffort,
	}
}

func inferenceCodexModelFromCatalog(model InferenceModel, priority int) CodexInferenceModel {
	displayName := strings.TrimSpace(model.Title)
	if displayName == "" {
		displayName = model.Name
	}

	var description *string
	if model.Page != nil {
		desc := strings.TrimSpace(model.Page.ShortDescription)
		if desc != "" {
			description = &desc
		}
	}

	inputModalities := []string{"text"}
	if slices.Contains(model.Capabilities, InferenceVision) {
		inputModalities = append(inputModalities, "image")
	}
	if slices.Contains(model.Capabilities, InferenceVideoVision) {
		inputModalities = append(inputModalities, "video")
	}
	if slices.Contains(model.Capabilities, InferenceAudio) {
		inputModalities = append(inputModalities, "audio")
	}

	var defaultReasoningLevel *string
	if model.DefaultReasoningEffort != "" {
		value := model.DefaultReasoningEffort
		defaultReasoningLevel = &value
	}
	supportedReasoningLevels := make([]CodexReasoningEffort, 0, len(model.ReasoningEfforts))
	for _, effort := range model.ReasoningEfforts {
		supportedReasoningLevels = append(supportedReasoningLevels, CodexReasoningEffort{
			Effort:      effort.Value,
			Description: effort.Name,
		})
	}

	return CodexInferenceModel{
		Slug:                           model.Name,
		DisplayName:                    displayName,
		Description:                    description,
		DefaultReasoningLevel:          defaultReasoningLevel,
		SupportedReasoningLevels:       supportedReasoningLevels,
		ShellType:                      "shell_command",
		Visibility:                     "list",
		SupportedInApi:                 true,
		Priority:                       priority,
		AdditionalSpeedTiers:           []string{},
		ServiceTiers:                   []CodexModelServiceTier{},
		DefaultServiceTier:             nil,
		AvailabilityNux:                nil,
		Upgrade:                        nil,
		BaseInstructions:               "",
		IncludeSkillsUsageInstructions: false,
		SupportsReasoningSummaries:     false,
		DefaultReasoningSummary:        "auto",
		SupportVerbosity:               false,
		DefaultVerbosity:               nil,
		ApplyPatchToolType:             "freeform",
		WebSearchToolType:              "text",
		TruncationPolicy: CodexTruncationPolicy{
			Mode:  "bytes",
			Limit: 10000,
		},
		SupportsParallelToolCalls:     false,
		SupportsImageDetailOriginal:   slices.Contains(model.Capabilities, InferenceVision),
		ContextWindow:                 model.ContextWindow,
		MaxContextWindow:              model.ContextWindow,
		AutoCompactTokenLimit:         nil,
		EffectiveContextWindowPercent: 95,
		ExperimentalSupportedTools:    []string{},
		InputModalities:               inputModalities,
		SupportsSearchTool:            false,
		UseResponsesLite:              false,
	}
}

// Chat completions
// =====================================================================================================================

type InferenceChatRequest struct {
	Model               string                                  `json:"model"`
	Messages            []InferenceChatMessage                  `json:"messages"`
	FrequencyPenalty    util.Option[float64]                    `json:"frequency_penalty,omitempty"`
	LogitBias           map[string]float64                      `json:"logit_bias,omitempty"`
	Logprobs            util.Option[bool]                       `json:"logprobs,omitempty"`
	MaxCompletionTokens util.Option[int]                        `json:"max_completion_tokens,omitempty"`
	Metadata            map[string]string                       `json:"metadata,omitempty"`
	N                   util.Option[int]                        `json:"n,omitempty"`
	ParallelToolCalls   util.Option[bool]                       `json:"parallel_tool_calls,omitempty"`
	Prediction          any                                     `json:"prediction,omitempty"`
	PresencePenalty     util.Option[float64]                    `json:"presence_penalty,omitempty"`
	ReasoningEffort     util.Option[string]                     `json:"reasoning_effort,omitempty"`
	ResponseFormat      any                                     `json:"response_format,omitempty"`
	Stream              bool                                    `json:"stream,omitempty"`
	StreamOptions       util.Option[InferenceChatStreamOptions] `json:"stream_options,omitempty"`
	Temperature         util.Option[float64]                    `json:"temperature,omitempty"`
	ToolChoice          any                                     `json:"tool_choice,omitempty"`
	Tools               []InferenceChatTool                     `json:"tools,omitempty"`
	TopLogprobs         util.Option[int]                        `json:"top_logprobs,omitempty"`
	TopP                util.Option[float64]                    `json:"top_p,omitempty"`
}

type InferenceChatMessage struct {
	Role       string                      `json:"role"`
	Content    InferenceChatMessageContent `json:"content"`
	Reasoning  InferenceChatMessageContent `json:"reasoning"`
	Name       string                      `json:"name,omitempty"`
	ToolCalls  []InferenceChatToolCall     `json:"tool_calls,omitempty"`
	ToolCallID string                      `json:"tool_call_id,omitempty"`
}

func (m *InferenceChatMessage) UnmarshalJSON(data []byte) error {
	type inferenceChatMessageJSON InferenceChatMessage
	var decoded struct {
		inferenceChatMessageJSON
		ReasoningContent InferenceChatMessageContent `json:"reasoning_content"`
	}
	if err := json.Unmarshal(data, &decoded); err != nil {
		return err
	}
	*m = InferenceChatMessage(decoded.inferenceChatMessageJSON)
	if m.Reasoning.String() == "" && decoded.ReasoningContent.String() != "" {
		m.Reasoning = decoded.ReasoningContent
	}
	return nil
}

type InferenceChatMessageContent struct {
	Text  string
	Parts []InferenceChatContentPart
	raw   json.RawMessage
}

type InferenceChatContentPart struct {
	Type     string            `json:"type"`
	Text     string            `json:"text,omitempty"`
	ImageUrl *InferenceChatUrl `json:"image_url,omitempty"`
	VideoUrl *InferenceChatUrl `json:"video_url,omitempty"`
	AudioUrl *InferenceChatUrl `json:"audio_url,omitempty"`
}

type InferenceChatUrl struct {
	Url string
}

func (u *InferenceChatUrl) UnmarshalJSON(data []byte) error {
	var object struct {
		Url string `json:"url"`
	}
	if err := json.Unmarshal(data, &object); err == nil {
		u.Url = object.Url
		return nil
	}
	return json.Unmarshal(data, &u.Url)
}

func (u InferenceChatUrl) MarshalJSON() ([]byte, error) {
	return json.Marshal(struct {
		Url string `json:"url"`
	}{Url: u.Url})
}

func inferenceChatTextContent(text string) InferenceChatMessageContent {
	return InferenceChatMessageContent{Text: text}
}

func (c InferenceChatMessageContent) String() string {
	if len(c.Parts) == 0 {
		return c.Text
	}

	var builder strings.Builder
	for _, part := range c.Parts {
		if part.Type == "" || part.Type == "text" {
			builder.WriteString(part.Text)
		}
	}
	return builder.String()
}

func (c *InferenceChatMessageContent) UnmarshalJSON(data []byte) error {
	c.Text = ""
	c.Parts = nil
	c.raw = append(c.raw[:0], data...)

	if len(data) == 0 || bytes.Equal(data, []byte("null")) {
		return nil
	}

	var text string
	if err := json.Unmarshal(data, &text); err == nil {
		c.Text = text
		return nil
	}

	var parts []InferenceChatContentPart
	if err := json.Unmarshal(data, &parts); err != nil {
		return err
	}
	c.Parts = parts
	return nil
}

func (c InferenceChatMessageContent) MarshalJSON() ([]byte, error) {
	if len(c.raw) > 0 {
		return c.raw, nil
	}
	if c.Parts != nil {
		return json.Marshal(c.Parts)
	}
	return json.Marshal(c.Text)
}

type InferenceChatStreamOptions struct {
	IncludeUsage bool `json:"include_usage,omitempty"`
}

type InferenceChatUsage struct {
	PromptTokens        int                                    `json:"prompt_tokens"`
	CompletionTokens    int                                    `json:"completion_tokens"`
	TotalTokens         int                                    `json:"total_tokens"`
	PromptTokensDetails util.Option[InferenceChatTokenDetails] `json:"prompt_tokens_details,omitempty"`
}

func (u InferenceChatUsage) MarshalJSON() ([]byte, error) {
	type inferenceChatUsageJSON struct {
		PromptTokens        int                        `json:"prompt_tokens"`
		CompletionTokens    int                        `json:"completion_tokens"`
		TotalTokens         int                        `json:"total_tokens"`
		PromptTokensDetails *InferenceChatTokenDetails `json:"prompt_tokens_details,omitempty"`
	}

	result := inferenceChatUsageJSON{
		PromptTokens:     u.PromptTokens,
		CompletionTokens: u.CompletionTokens,
		TotalTokens:      u.TotalTokens,
	}
	if u.PromptTokensDetails.Present {
		result.PromptTokensDetails = &u.PromptTokensDetails.Value
	}
	return json.Marshal(result)
}

type InferenceChatTokenDetails struct {
	CachedTokens int `json:"cached_tokens"`
}

type InferenceChatTool struct {
	Type     string                    `json:"type"`
	Function InferenceChatToolFunction `json:"function,omitempty"`
}

type InferenceChatToolFunction struct {
	Name        string            `json:"name"`
	Description string            `json:"description,omitempty"`
	Parameters  any               `json:"parameters,omitempty"`
	Strict      util.Option[bool] `json:"strict,omitempty"`
}

type InferenceChatToolCall struct {
	Id       string                        `json:"id"`
	Type     string                        `json:"type"`
	Function InferenceChatToolCallFunction `json:"function"`
}

type InferenceChatToolCallFunction struct {
	Name      string `json:"name"`
	Arguments string `json:"arguments"`
}

type InferenceChatResponse struct {
	Id      string                `json:"id"`
	Object  string                `json:"object"`
	Created int64                 `json:"created"`
	Model   string                `json:"model"`
	Choices []InferenceChatChoice `json:"choices"`
	Usage   InferenceChatUsage    `json:"usage"`
}

func (r InferenceChatResponse) MarshalJSON() ([]byte, error) {
	type inferenceChatResponseJSON InferenceChatResponse
	if r.Choices == nil {
		r.Choices = []InferenceChatChoice{}
	}
	return json.Marshal(inferenceChatResponseJSON(r))
}

type InferenceChatChoice struct {
	Index        int                  `json:"index"`
	Message      InferenceChatMessage `json:"message"`
	FinishReason string               `json:"finish_reason"`
}

type InferenceChatStreamingResponse struct {
	Id      string                         `json:"id"`
	Object  string                         `json:"object"`
	Created int64                          `json:"created"`
	Model   string                         `json:"model"`
	Choices []InferenceChatStreamingChoice `json:"choices"`
	Usage   InferenceChatUsage             `json:"usage"`
}

func (r InferenceChatStreamingResponse) MarshalJSON() ([]byte, error) {
	type inferenceChatStreamingResponseJSON InferenceChatStreamingResponse
	if r.Choices == nil {
		r.Choices = []InferenceChatStreamingChoice{}
	}
	return json.Marshal(inferenceChatStreamingResponseJSON(r))
}

type InferenceChatStreamingChoice struct {
	Index        int                `json:"index"`
	Delta        InferenceChatDelta `json:"delta"`
	FinishReason string             `json:"finish_reason,omitempty"`
}

type InferenceChatDelta struct {
	Role      string                           `json:"role,omitempty"`
	Content   string                           `json:"content,omitempty"`
	Reasoning string                           `json:"reasoning,omitempty"`
	ToolCalls []InferenceChatStreamingToolCall `json:"tool_calls,omitempty"`
}

func (d *InferenceChatDelta) UnmarshalJSON(data []byte) error {
	var decoded struct {
		Role             string                           `json:"role"`
		Content          json.RawMessage                  `json:"content"`
		Reasoning        json.RawMessage                  `json:"reasoning"`
		ReasoningContent json.RawMessage                  `json:"reasoning_content"`
		ToolCalls        []InferenceChatStreamingToolCall `json:"tool_calls"`
	}
	if err := json.Unmarshal(data, &decoded); err != nil {
		return err
	}

	content, err := inferenceChatDeltaText(decoded.Content)
	if err != nil {
		return err
	}
	reasoning, err := inferenceChatDeltaText(decoded.Reasoning)
	if err != nil {
		return err
	}
	reasoningContent, err := inferenceChatDeltaText(decoded.ReasoningContent)
	if err != nil {
		return err
	}
	if reasoning == "" {
		reasoning = reasoningContent
	}

	d.Role = decoded.Role
	d.Content = content
	d.Reasoning = reasoning
	d.ToolCalls = decoded.ToolCalls
	return nil
}

func inferenceChatDeltaText(data json.RawMessage) (string, error) {
	if len(data) == 0 || bytes.Equal(data, []byte("null")) {
		return "", nil
	}

	var text string
	if err := json.Unmarshal(data, &text); err == nil {
		return text, nil
	}

	var part struct {
		Text string `json:"text"`
	}
	if err := json.Unmarshal(data, &part); err == nil && part.Text != "" {
		return part.Text, nil
	}

	var parts []struct {
		Text string `json:"text"`
	}
	if err := json.Unmarshal(data, &parts); err != nil {
		return "", err
	}

	var builder strings.Builder
	for _, part := range parts {
		builder.WriteString(part.Text)
	}
	return builder.String(), nil
}

type InferenceChatStreamingToolCall struct {
	Index    int                                     `json:"index"`
	Id       string                                  `json:"id,omitempty"`
	Type     string                                  `json:"type,omitempty"`
	Function *InferenceChatStreamingToolCallFunction `json:"function,omitempty"`
}

type InferenceChatStreamingToolCallFunction struct {
	Name      string `json:"name,omitempty"`
	Arguments string `json:"arguments,omitempty"`
}

type inferenceChatUpstreamCapture struct {
	CreatedAt string            `json:"created_at"`
	Kind      string            `json:"kind"`
	Request   json.RawMessage   `json:"request"`
	Chunks    []json.RawMessage `json:"chunks"`
}

func inferenceChatUpstreamCapturePath() string {
	return filepath.Join("/tmp", fmt.Sprintf("ucloud-inference-upstream-%d.json", time.Now().UnixNano()))
}

func inferenceChatWriteUpstreamCapture(request []byte, chunks []json.RawMessage) {
	if !inferenceChatCaptureUpstreamOutput || len(chunks) == 0 {
		return
	}
	capture := inferenceChatUpstreamCapture{
		CreatedAt: time.Now().Format(time.RFC3339Nano),
		Kind:      "chat.completions.stream",
		Request:   append(json.RawMessage(nil), request...),
		Chunks:    chunks,
	}
	encoded, err := json.MarshalIndent(capture, "", "  ")
	if err != nil {
		log.Info("Inference upstream capture encode failed: %v", err)
		return
	}
	path := inferenceChatUpstreamCapturePath()
	if err := os.WriteFile(path, encoded, 0600); err != nil {
		log.Info("Inference upstream capture write failed: path=%s err=%v", path, err)
		return
	}
	log.Info("Inference upstream capture written: %s chunks=%d", path, len(chunks))
}

func inferenceChatReadUpstreamReplay(path string) ([]json.RawMessage, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var capture inferenceChatUpstreamCapture
	if err := json.Unmarshal(data, &capture); err == nil && len(capture.Chunks) > 0 {
		return capture.Chunks, nil
	}
	var chunks []json.RawMessage
	if err := json.Unmarshal(data, &chunks); err != nil {
		return nil, err
	}
	return chunks, nil
}

func inferenceChatStreamingResponseFromRaw(raw []byte, modelName string, usageSeen InferenceChatUsage) (InferenceChatStreamingResponse, InferenceChatUsage, bool, bool) {
	var chunk struct {
		Id      string                          `json:"id"`
		Object  string                          `json:"object"`
		Created int64                           `json:"created"`
		Model   string                          `json:"model"`
		Choices []InferenceChatStreamingChoice  `json:"choices"`
		Usage   util.Option[InferenceChatUsage] `json:"usage"`
	}
	if jsonErr := json.Unmarshal(raw, &chunk); jsonErr != nil {
		return InferenceChatStreamingResponse{}, usageSeen, false, false
	}
	chunk.Model = modelName
	usagePresent := false
	if chunk.Usage.Present {
		if inferenceChatUsageValid(chunk.Usage.Value) {
			usageSeen = chunk.Usage.Value
			usagePresent = true
		} else {
			log.Warn("Inference upstream returned invalid negative chat usage: model=%s", modelName)
		}
	}
	return InferenceChatStreamingResponse{
		Id:      chunk.Id,
		Object:  chunk.Object,
		Created: chunk.Created,
		Model:   chunk.Model,
		Choices: chunk.Choices,
		Usage:   usageSeen,
	}, usageSeen, true, usagePresent
}

func inferenceChatDeltaHasOutput(delta InferenceChatDelta) bool {
	if delta.Content != "" || delta.Reasoning != "" {
		return true
	}
	for _, toolCall := range delta.ToolCalls {
		if toolCall.Id != "" || toolCall.Type != "" || toolCall.Function != nil {
			return true
		}
	}
	return false
}

func InferenceChat(ctx context.Context, owner apm.WalletOwner, username string, history InferenceChatRequest) (InferenceChatResponse, *util.HttpError) {
	return InferenceChatEx(ctx, owner, username, history, false)
}

func InferenceChatEx(ctx context.Context, owner apm.WalletOwner, username string, history InferenceChatRequest, skipAudit bool) (InferenceChatResponse, *util.HttpError) {
	requestStartedAt := time.Now()
	requestModel := "unknown"
	requestOutcome := "error"
	auditModel := history.Model
	auditUsage := InferenceChatUsage{}
	auditAborted := false
	defer func() {
		inferenceReportChatRequestMetrics(requestModel, requestOutcome, requestStartedAt, time.Now())
		if !skipAudit {
			inferenceAuditRecord(
				ctx,
				"inference.chat",
				owner,
				username,
				requestStartedAt,
				inferenceAuditChatBody(
					inferenceAuditChainKey(owner, username),
					auditModel,
					history,
					&auditUsage,
					requestOutcome,
					auditAborted,
					inferenceAuditSourceOf(ctx),
				),
				requestOutcome,
			)
		}
	}()

	if inferenceIsLocked(owner) {
		requestOutcome = "payment_required"
		return InferenceChatResponse{}, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, history.Model)
	if httpErr != nil {
		requestOutcome = "client_error"
		return InferenceChatResponse{}, httpErr
	}
	requestModel = model.Name
	if !history.ReasoningEffort.Present && model.DefaultReasoningEffort != "" {
		history.ReasoningEffort = util.OptValue(model.DefaultReasoningEffort)
	}
	if httpErr := inferenceValidateChatRequest(history, model); httpErr != nil {
		requestOutcome = "client_error"
		return InferenceChatResponse{}, httpErr
	}
	release, httpErr := inferenceAcquire(owner, username)
	if httpErr != nil {
		requestOutcome = "admission_rejected"
		return InferenceChatResponse{}, httpErr
	}
	defer release()
	history.Model = model.Endpoint.BackendModelName

	body, err := json.Marshal(history)
	if err != nil {
		requestOutcome = "client_error"
		return InferenceChatResponse{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		requestOutcome = "client_error"
		return InferenceChatResponse{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	respBody, httpErr := inferenceBackendJSONRequest(ctx, model.Endpoint.BasePath, http.MethodPost, "/chat/completions", body, "application/json")
	if httpErr != nil {
		requestOutcome = "upstream_error"
		return InferenceChatResponse{}, httpErr
	}

	var resp struct {
		Id      string                          `json:"id"`
		Object  string                          `json:"object"`
		Created int64                           `json:"created"`
		Model   string                          `json:"model"`
		Choices []InferenceChatChoice           `json:"choices"`
		Usage   util.Option[InferenceChatUsage] `json:"usage"`
	}
	if err := json.Unmarshal(respBody, &resp); err != nil {
		requestOutcome = "upstream_error"
		return InferenceChatResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response")
	}
	if resp.Usage.Present && !inferenceChatUsageValid(resp.Usage.Value) {
		requestOutcome = "upstream_error"
		return InferenceChatResponse{}, util.HttpErr(http.StatusBadGateway, "invalid usage from upstream")
	}
	if !resp.Usage.Present {
		inferenceWarnMissingUsage("chat", model.Name)
	}

	usage := inferenceChatUsage(resp.Usage)
	if resp.Usage.Present {
		cachedTokens, inputTokens, outputTokens := inferenceChatUsageComponents(usage)
		inferenceReportChatUsageMetrics(model.Name, cachedTokens, inputTokens, outputTokens)
		inferenceReportUsage(owner, model, cachedTokens, inputTokens, outputTokens)
	}

	requestOutcome = "success"
	if !resp.Usage.Present {
		requestOutcome = "success_missing_usage"
	}
	auditUsage = inferenceChatUsage(resp.Usage)
	return InferenceChatResponse{
		Id:      resp.Id,
		Object:  resp.Object,
		Created: resp.Created,
		Model:   model.Name,
		Choices: resp.Choices,
		Usage:   usage,
	}, nil
}

func InferenceChatStreaming(ctx context.Context, owner apm.WalletOwner, username string, history InferenceChatRequest) (chan InferenceChatStreamingResponse, *util.HttpError) {
	requestStartedAt := time.Now()
	requestModel := "unknown"
	auditModel := history.Model
	auditUsage := InferenceChatUsage{}
	auditAborted := false
	auditDone := false
	emitAudit := func(outcome string) {
		if auditDone {
			return
		}
		auditDone = true
		inferenceAuditRecord(
			ctx,
			"inference.chat",
			owner,
			username,
			requestStartedAt,
			inferenceAuditChatBody(
				inferenceAuditChainKey(owner, username),
				auditModel,
				history,
				&auditUsage,
				outcome,
				auditAborted,
				inferenceAuditSourceOf(ctx),
			),
			outcome,
		)
	}
	ch := make(chan InferenceChatStreamingResponse, 1024) // buffered to allow for slow consumers (e.g. playground UI)

	if inferenceIsLocked(owner) {
		close(ch)
		inferenceReportChatRequestMetrics(requestModel, "payment_required", requestStartedAt, time.Now())
		emitAudit("payment_required")
		return ch, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, history.Model)
	if httpErr != nil {
		close(ch)
		inferenceReportChatRequestMetrics(requestModel, "client_error", requestStartedAt, time.Now())
		emitAudit("client_error")
		return ch, httpErr
	}
	requestModel = model.Name
	auditModel = model.Name
	if !history.ReasoningEffort.Present && model.DefaultReasoningEffort != "" {
		history.ReasoningEffort = util.OptValue(model.DefaultReasoningEffort)
	}
	if httpErr := inferenceValidateChatRequest(history, model); httpErr != nil {
		close(ch)
		inferenceReportChatRequestMetrics(requestModel, "client_error", requestStartedAt, time.Now())
		emitAudit("client_error")
		return ch, httpErr
	}
	release, httpErr := inferenceAcquire(owner, username)
	if httpErr != nil {
		close(ch)
		inferenceReportChatRequestMetrics(requestModel, "admission_rejected", requestStartedAt, time.Now())
		emitAudit("admission_rejected")
		return ch, httpErr
	}
	history.Model = model.Endpoint.BackendModelName

	go func() {
		defer close(ch)
		defer release()
		streamOutcome := "error"
		defer func() {
			inferenceReportChatRequestMetrics(requestModel, streamOutcome, requestStartedAt, time.Now())
			emitAudit(streamOutcome)
		}()

		history.Stream = true
		if !history.StreamOptions.Present {
			history.StreamOptions = util.OptValue(InferenceChatStreamOptions{IncludeUsage: true})
		}
		history.StreamOptions.Value.IncludeUsage = true

		usageSeen := InferenceChatUsage{}
		body, err := json.Marshal(history)
		if err != nil {
			return
		}
		if len(body) > inferenceMaxJSONRequestBytes {
			return
		}

		streamStartedAt := time.Now()
		firstTokenAt := time.Time{}
		lastOutputAt := time.Time{}
		recordOutputDelta := func(resp InferenceChatStreamingResponse) {
			hasOutput := false
			for _, choice := range resp.Choices {
				if inferenceChatDeltaHasOutput(choice.Delta) {
					hasOutput = true
					break
				}
			}
			if !hasOutput {
				return
			}

			now := time.Now()
			if firstTokenAt.IsZero() {
				firstTokenAt = now
			} else if !lastOutputAt.IsZero() {
				metricInferenceOutputDeltaInterval.WithLabelValues(model.Name).Observe(now.Sub(lastOutputAt).Seconds())
			}
			lastOutputAt = now
		}
		reportMetrics := func(completedAt time.Time) {
			inferenceReportChatStreamingMetrics(model.Name, streamStartedAt, firstTokenAt, lastOutputAt, completedAt, usageSeen.CompletionTokens)
		}
		if inferenceChatReplayUpstreamOutputPath != "" {
			chunks, err := inferenceChatReadUpstreamReplay(inferenceChatReplayUpstreamOutputPath)
			if err != nil {
				log.Info("Inference upstream replay read failed: path=%s err=%v", inferenceChatReplayUpstreamOutputPath, err)
				streamOutcome = "upstream_error"
				return
			}
			log.Info("Inference upstream replay loaded: path=%s chunks=%d", inferenceChatReplayUpstreamOutputPath, len(chunks))
			for _, raw := range chunks {
				resp, usage, ok, _ := inferenceChatStreamingResponseFromRaw(raw, model.Name, usageSeen)
				if !ok {
					log.Info("Inference upstream replay skipped invalid chunk: len=%d", len(raw))
					continue
				}
				usageSeen = usage
				recordOutputDelta(resp)
				if !inferenceSend(ctx, ch, resp) {
					reportMetrics(time.Now())
					streamOutcome = "client_cancelled"
					return
				}
			}
			reportMetrics(time.Now())
			auditUsage = usageSeen
			streamOutcome = "success"
			return
		}

		streamCtx, cancel, touch := inferenceStreamContext(ctx)
		defer cancel()
		resp, httpErr := inferenceBackendStreamRequest(streamCtx, model.Endpoint.BasePath, "/chat/completions", body)
		if httpErr != nil {
			streamOutcome = "upstream_error"
			return
		}
		defer util.SilentClose(resp.Body)

		capturedChunks := []json.RawMessage{}
		usagePresent := false
		streamCompleted := false
		streamSendFailed := false
		readErr := inferenceReadSSE(streamCtx, resp.Body, touch, func(event []byte) bool {
			raw := strings.TrimSpace(string(event))
			if raw == "" {
				return true
			}
			if raw == "data: [DONE]" {
				streamCompleted = true
				return true
			}

			raw = inferenceSSEDataPayload(raw)
			if raw == "" {
				return true
			}
			if raw == "[DONE]" {
				streamCompleted = true
				return true
			}
			if resp, usage, ok, chunkUsagePresent := inferenceChatStreamingResponseFromRaw([]byte(raw), model.Name, usageSeen); ok {
				capturedChunks = append(capturedChunks, append(json.RawMessage(nil), raw...))
				usageSeen = usage
				usagePresent = usagePresent || chunkUsagePresent
				recordOutputDelta(resp)
				if !inferenceSend(streamCtx, ch, resp) {
					streamSendFailed = true
					return false
				}
				return true
			}
			return true
		})
		if readErr != nil && streamCtx.Err() == nil {
			log.Warn("Inference upstream chat stream failed: model=%s err=%v", model.Name, readErr)
		}
		streamCompletedAt := time.Now()
		if ctx.Err() != nil {
			streamOutcome = "client_cancelled"
			auditAborted = true
		} else if streamCtx.Err() != nil {
			streamOutcome = "stream_timeout"
			auditAborted = true
		} else if readErr != nil {
			streamOutcome = "upstream_error"
		} else if streamSendFailed {
			streamOutcome = "stream_timeout"
			auditAborted = true
		} else if !streamCompleted {
			streamOutcome = "incomplete"
		} else {
			streamOutcome = "success"
			if !usagePresent {
				streamOutcome = "success_missing_usage"
			}
		}
		if usagePresent {
			cachedTokens, inputTokens, outputTokens := inferenceChatUsageComponents(usageSeen)
			inferenceReportChatUsageMetrics(model.Name, cachedTokens, inputTokens, outputTokens)
			inferenceReportUsage(owner, model, cachedTokens, inputTokens, outputTokens)
		} else if streamCtx.Err() == nil {
			inferenceWarnMissingUsage("chat-stream", model.Name)
		}
		auditUsage = usageSeen
		reportMetrics(streamCompletedAt)
		inferenceChatWriteUpstreamCapture(body, capturedChunks)
	}()

	return ch, nil
}

// Helpers
// =====================================================================================================================

func parseFormBool(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "1", "t", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func inferenceSSEDataPayload(raw string) string {
	raw = strings.TrimSpace(raw)
	if strings.HasPrefix(raw, "data: ") && !strings.Contains(raw, "\n") {
		return strings.TrimSpace(strings.TrimPrefix(raw, "data: "))
	}

	var builder strings.Builder
	for _, line := range strings.Split(raw, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "data: ") {
			if builder.Len() > 0 {
				builder.WriteByte('\n')
			}
			builder.WriteString(strings.TrimPrefix(line, "data: "))
		}
	}
	return strings.TrimSpace(builder.String())
}

func inferenceBackendJSONRequest(ctx context.Context, basePath string, method string, path string, body []byte, contentType string) ([]byte, *util.HttpError) {
	resp, httpErr := inferenceBackendRequest(ctx, basePath, method, path, body, contentType)
	if httpErr != nil {
		return nil, httpErr
	}
	defer util.SilentClose(resp.Body)

	if resp.ContentLength > inferenceMaxUpstreamJSONBytes {
		return nil, util.HttpErr(http.StatusBadGateway, "response from upstream is too large")
	}
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, inferenceMaxUpstreamJSONBytes+1))
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}
	if len(respBody) > inferenceMaxUpstreamJSONBytes {
		return nil, util.HttpErr(http.StatusBadGateway, "response from upstream is too large")
	}

	return respBody, nil
}

func inferenceBackendStreamRequest(ctx context.Context, basePath string, path string, body []byte) (*http.Response, *util.HttpError) {
	return inferenceBackendRequest(ctx, basePath, http.MethodPost, path, body, "application/json")
}

func inferenceBackendRequest(ctx context.Context, basePath string, method string, path string, body []byte, contentType string) (*http.Response, *util.HttpError) {
	backend := strings.TrimRight(basePath, "/")
	if backend == "" {
		return nil, util.HttpErr(http.StatusServiceUnavailable, "inference backend is not configured")
	}
	if err := inferenceValidateBackendEndpoint(backend); err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, method, backend+path, bytes.NewReader(body))
	if err != nil {
		log.Info("Could not build inference upstream request: method=%s path=%s requestBytes=%d", method, path, len(body))
		return nil, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	req.Header.Set("Authorization", "Bearer notused")

	resp, err := inferenceHTTPClient.Do(req)
	if err != nil {
		log.Info("Inference upstream request failed: method=%s path=%s requestBytes=%d err=%v", method, path, len(body), err)
		return nil, util.HttpErr(http.StatusBadGateway, "invalid request")
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Info("Inference upstream rejected request: method=%s path=%s status=%d requestBytes=%d", method, path, resp.StatusCode, len(body))
		util.SilentClose(resp.Body)
		return nil, util.HttpErr(resp.StatusCode, "invalid request")
	}

	return resp, nil
}

func inferenceValidateBackendEndpoint(raw string) *util.HttpError {
	endpoint, err := url.Parse(raw)
	if err != nil || endpoint.User != nil || endpoint.RawQuery != "" || endpoint.Fragment != "" {
		return util.HttpErr(http.StatusBadRequest, "invalid model endpoint")
	}

	inferenceCfg := shared.ServiceConfig.Compute.Inference
	switch inferenceCfg.Provider {
	case cfg.KubernetesInferenceProviderDevelopment:
		configured, parseErr := url.Parse(strings.TrimRight(inferenceCfg.BackendServer, "/"))
		if parseErr != nil || configured.Scheme == "" || configured.Host == "" ||
			!strings.EqualFold(endpoint.Scheme, configured.Scheme) ||
			!strings.EqualFold(endpoint.Host, configured.Host) ||
			strings.TrimRight(endpoint.EscapedPath(), "/") != strings.TrimRight(configured.EscapedPath(), "/") {
			return util.HttpErr(http.StatusForbidden, "model endpoint is not allowed")
		}
	case cfg.KubernetesInferenceProviderDynamo:
		namespace := strings.ToLower(strings.TrimSpace(inferenceCfg.Dynamo.Namespace))
		labels := strings.Split(strings.ToLower(endpoint.Hostname()), ".")
		if endpoint.Scheme != "http" || endpoint.EscapedPath() != "/v1" || len(labels) != 5 ||
			labels[0] == "" || !strings.HasSuffix(labels[0], "-frontend") || labels[1] != namespace ||
			labels[2] != "svc" || labels[3] != "cluster" || labels[4] != "local" {
			return util.HttpErr(http.StatusForbidden, "model endpoint is not allowed")
		}
	default:
		return util.HttpErr(http.StatusServiceUnavailable, "inference provider is not configured")
	}
	return nil
}

func inferenceResolveModelForOwner(owner apm.WalletOwner, modelName string) (InferenceModel, *util.HttpError) {
	modelName = strings.TrimSpace(modelName)
	if modelName == "" {
		return InferenceModel{}, util.HttpErr(http.StatusBadRequest, "model is required")
	}

	model, ok := InferenceCatalogModelByName(modelName)
	if !ok {
		return InferenceModel{}, util.HttpErr(http.StatusNotFound, "model not found")
	}
	if !inferenceModelAvailableToOwner(model, owner) {
		return InferenceModel{}, util.HttpErr(http.StatusForbidden, "model is not available")
	}
	return model, nil
}

func inferenceValidateChatRequest(request InferenceChatRequest, model InferenceModel) *util.HttpError {
	if request.MaxCompletionTokens.Present && (request.MaxCompletionTokens.Value <= 0 || request.MaxCompletionTokens.Value > model.ChatSettings.MaxCompletionTokens) {
		return util.HttpErr(http.StatusBadRequest, "max completion tokens exceeds the model limit")
	}
	if request.N.Present && (request.N.Value <= 0 || request.N.Value > 8) {
		return util.HttpErr(http.StatusBadRequest, "invalid number of completions")
	}
	if request.ReasoningEffort.Present {
		supported := false
		for _, effort := range model.ReasoningEfforts {
			if request.ReasoningEffort.Value == effort.Value {
				supported = true
				break
			}
		}
		if !supported {
			return util.HttpErr(http.StatusBadRequest, "unsupported reasoning effort")
		}
	}
	if len(request.Messages) > 1024 || len(request.Tools) > 128 {
		return util.HttpErr(http.StatusBadRequest, "request contains too many items")
	}
	return nil
}

// Cost estimation
// =====================================================================================================================

func inferenceEstimateTokensFromText(text string) int {
	if text == "" {
		return 0
	}

	return (len([]rune(text)) + 3) / 4
}

func inferenceChatUsage(usage util.Option[InferenceChatUsage]) InferenceChatUsage {
	if usage.Present {
		result := usage.Value
		if result.TotalTokens == 0 {
			result.TotalTokens = result.PromptTokens + result.CompletionTokens
		}
		return result
	}
	return InferenceChatUsage{}
}

func inferenceChatUsageValid(usage InferenceChatUsage) bool {
	if usage.PromptTokens < 0 || usage.CompletionTokens < 0 || usage.TotalTokens < 0 {
		return false
	}
	return !usage.PromptTokensDetails.Present || usage.PromptTokensDetails.Value.CachedTokens >= 0
}

func inferenceChatUsageComponents(usage InferenceChatUsage) (cachedTokens int, inputTokens int, outputTokens int) {
	cachedTokens = 0
	if usage.PromptTokensDetails.Present {
		cachedTokens = usage.PromptTokensDetails.Value.CachedTokens
	}
	if cachedTokens < 0 {
		cachedTokens = 0
	}
	if cachedTokens > usage.PromptTokens {
		cachedTokens = usage.PromptTokens
	}

	inputTokens = usage.PromptTokens - cachedTokens
	outputTokens = usage.CompletionTokens
	return cachedTokens, inputTokens, outputTokens
}
