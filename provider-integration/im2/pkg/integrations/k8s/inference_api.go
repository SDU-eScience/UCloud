package k8s

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"mime/multipart"
	"net/http"
	"slices"
	"strconv"
	"strings"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

// Models
// =====================================================================================================================

type InferenceModelCapability string

const (
	InferenceModelCapabilityChat            InferenceModelCapability = "Chat"
	InferenceModelCapabilityTranscription   InferenceModelCapability = "Transcription"
	InferenceModelCapabilityImageGeneration InferenceModelCapability = "ImageGeneration"
)

type InferenceModel struct {
	Id           string                     `json:"id"`
	Object       string                     `json:"object"`
	OwnedBy      string                     `json:"owned_by,omitempty"`
	Capabilities []InferenceModelCapability `json:"capabilities,omitempty"`
}

type InferenceModelsResponse struct {
	Object string           `json:"object"`
	Data   []InferenceModel `json:"data"`
}

func InferenceModels() (InferenceModelsResponse, *util.HttpError) {
	body, httpErr := inferenceBackendJSONRequest(http.MethodGet, "/models", nil, "")
	if httpErr != nil {
		return InferenceModelsResponse{}, httpErr
	}

	var resp InferenceModelsResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return InferenceModelsResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	if util.DevelopmentModeEnabled() {
		modelsToKeep := []string{"qwen3-0.6b", "whisper-1", "sd-1.5-ggml", "stablediffusion"}
		newResp := resp
		newResp.Data = nil

		for _, item := range resp.Data {
			if slices.Contains(modelsToKeep, item.Id) {
				newResp.Data = append(newResp.Data, item)
			}
		}

		resp = newResp
	}

	for i := range resp.Data {
		resp.Data[i].Capabilities = inferenceModelCapabilities(resp.Data[i].Id)
	}

	return resp, nil
}

func InferenceModelByID(id string) (InferenceModel, *util.HttpError) {
	if strings.TrimSpace(id) == "" {
		return InferenceModel{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	body, httpErr := inferenceBackendJSONRequest(http.MethodGet, "/models/"+id, nil, "")
	if httpErr != nil {
		return InferenceModel{}, httpErr
	}

	var resp InferenceModel
	if err := json.Unmarshal(body, &resp); err != nil {
		return InferenceModel{}, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	resp.Capabilities = inferenceModelCapabilities(resp.Id)
	return resp, nil
}

func inferenceModelCapabilities(modelId string) []InferenceModelCapability {
	id := strings.ToLower(modelId)
	if id == "" {
		return []InferenceModelCapability{InferenceModelCapabilityChat}
	}

	capabilities := make([]InferenceModelCapability, 0, 3)

	switch id {
	case "qwen3-0.6b":
		capabilities = append(capabilities, InferenceModelCapabilityChat)
	case "whisper-1":
		capabilities = append(capabilities, InferenceModelCapabilityTranscription)
	case "sd-1.5-ggml", "stablediffusion":
		capabilities = append(capabilities, InferenceModelCapabilityImageGeneration)
	}

	return capabilities
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
	Verbosity           util.Option[string]                     `json:"verbosity,omitempty"`
}

type InferenceChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type InferenceChatStreamOptions struct {
	IncludeUsage bool `json:"include_usage,omitempty"`
}

type InferenceChatUsage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens,omitempty"`
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

type InferenceChatResponse struct {
	Id      string                          `json:"id"`
	Object  string                          `json:"object"`
	Created int64                           `json:"created"`
	Model   string                          `json:"model"`
	Choices []InferenceChatChoice           `json:"choices"`
	Usage   util.Option[InferenceChatUsage] `json:"usage,omitempty"`
}

type InferenceChatChoice struct {
	Index        int                  `json:"index"`
	Message      InferenceChatMessage `json:"message"`
	FinishReason string               `json:"finish_reason"`
}

type InferenceChatStreamingResponse struct {
	Id      string                          `json:"id"`
	Object  string                          `json:"object"`
	Created int64                           `json:"created"`
	Model   string                          `json:"model"`
	Choices []InferenceChatStreamingChoice  `json:"choices"`
	Usage   util.Option[InferenceChatUsage] `json:"usage,omitempty"`
}

type InferenceChatStreamingChoice struct {
	Index        int                `json:"index"`
	Delta        InferenceChatDelta `json:"delta"`
	FinishReason string             `json:"finish_reason,omitempty"`
}

type InferenceChatDelta struct {
	Role    string `json:"role,omitempty"`
	Content string `json:"content,omitempty"`
}

func InferenceChat(owner apm.WalletOwner, history InferenceChatRequest) InferenceChatResponse {
	if inferenceIsLocked(owner) {
		return InferenceChatResponse{}
	}

	body, err := json.Marshal(history)
	if err != nil {
		return InferenceChatResponse{}
	}

	respBody, httpErr := inferenceBackendJSONRequest(http.MethodPost, "/chat/completions", body, "application/json")
	if httpErr != nil {
		return InferenceChatResponse{}
	}

	var resp InferenceChatResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return InferenceChatResponse{}
	}

	if usage := resp.Usage; usage.Present {
		inferenceReportUsage(owner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
	}

	return resp
}

func InferenceChatStreaming(owner apm.WalletOwner, history InferenceChatRequest) chan InferenceChatStreamingResponse {
	ch := make(chan InferenceChatStreamingResponse)

	if inferenceIsLocked(owner) {
		close(ch)
		return ch
	}

	go func() {
		defer close(ch)

		history.Stream = true
		if !history.StreamOptions.Present {
			history.StreamOptions = util.OptValue(InferenceChatStreamOptions{IncludeUsage: true})
		}

		body, err := json.Marshal(history)
		if err != nil {
			return
		}

		resp, httpErr := inferenceBackendStreamRequest("/chat/completions", body)
		if httpErr != nil {
			return
		}
		defer util.SilentClose(resp.Body)

		reader := bufio.NewReader(resp.Body)
		var event bytes.Buffer

		flush := func() {
			if event.Len() == 0 {
				return
			}

			raw := strings.TrimSpace(event.String())
			event.Reset()
			if raw == "" || raw == "data: [DONE]" {
				return
			}

			raw = strings.TrimPrefix(raw, "data: ")
			var chunk InferenceChatStreamingResponse
			if jsonErr := json.Unmarshal([]byte(raw), &chunk); jsonErr == nil {
				if usage := chunk.Usage; usage.Present && len(chunk.Choices) == 0 {
					inferenceReportUsage(owner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
				}
				ch <- chunk
			}
		}

		for {
			line, readErr := reader.ReadBytes('\n')
			if len(line) > 0 {
				event.Write(line)
				if bytes.HasSuffix(event.Bytes(), []byte("\n\n")) {
					flush()
				}
			}

			if readErr != nil {
				if readErr == io.EOF {
					flush()
				}
				return
			}
		}
	}()

	return ch
}

// Transcription
// =====================================================================================================================

type InferenceTranscriptionRequest struct {
	File                   InferenceTranscriptionFile
	Model                  string
	Language               util.Option[string]
	Prompt                 util.Option[string]
	ResponseFormat         InferenceTranscriptionResponseFormat
	Stream                 bool
	Temperature            util.Option[float64]
	Include                []string
	TimestampGranularities []string
	ChunkingStrategy       util.Option[string]
	KnownSpeakerNames      []string
	KnownSpeakerReferences []string
}

type InferenceTranscriptionFile struct {
	Name        string
	ContentType string
	Data        []byte
}

type InferenceTranscriptionResponseFormat string

const (
	InferenceTranscriptionRespJson         InferenceTranscriptionResponseFormat = "json"
	InferenceTranscriptionRespDiarizedJson InferenceTranscriptionResponseFormat = "diarized_json"
	InferenceTranscriptionRespVerboseJson  InferenceTranscriptionResponseFormat = "verbose_json"
)

type InferenceTranscriptionLogprob struct {
	Token   util.Option[string] `json:"token,omitempty"`
	Bytes   util.Option[[]int]  `json:"bytes,omitempty"`
	Logprob float64             `json:"logprob"`
}

type InferenceTranscriptionInputTokenDetails struct {
	AudioTokens util.Option[int] `json:"audio_tokens,omitempty"`
	TextTokens  util.Option[int] `json:"text_tokens,omitempty"`
}

type InferenceTranscriptionUsage struct {
	Type              string                                               `json:"type"`
	Seconds           util.Option[float64]                                 `json:"seconds,omitempty"`
	InputTokens       util.Option[int]                                     `json:"input_tokens,omitempty"`
	OutputTokens      util.Option[int]                                     `json:"output_tokens,omitempty"`
	TotalTokens       util.Option[int]                                     `json:"total_tokens,omitempty"`
	InputTokenDetails util.Option[InferenceTranscriptionInputTokenDetails] `json:"input_token_details,omitempty"`
}

type InferenceTranscriptionResponse struct {
	Json         *InferenceTranscriptionJsonResponse
	VerboseJson  *InferenceTranscriptionVerboseResponse
	DiarizedJson *InferenceTranscriptionDiarizedResponse
}

type InferenceTranscriptionJsonResponse struct {
	Text     string                                       `json:"text"`
	Logprobs util.Option[[]InferenceTranscriptionLogprob] `json:"logprobs,omitempty"`
	Usage    util.Option[InferenceTranscriptionUsage]     `json:"usage,omitempty"`
}

type InferenceTranscriptionDiarizedResponse struct {
	Task     string                                   `json:"task"`
	Duration float64                                  `json:"duration"`
	Text     string                                   `json:"text"`
	Segments []InferenceTranscriptionDiarizedSegment  `json:"segments"`
	Usage    util.Option[InferenceTranscriptionUsage] `json:"usage,omitempty"`
}

type InferenceTranscriptionDiarizedSegment struct {
	Type    string  `json:"type"`
	ID      string  `json:"id"`
	Start   float64 `json:"start"`
	End     float64 `json:"end"`
	Text    string  `json:"text"`
	Speaker string  `json:"speaker"`
}

type InferenceTranscriptionVerboseResponse struct {
	Task     string                                              `json:"task"`
	Language string                                              `json:"language"`
	Duration float64                                             `json:"duration"`
	Text     string                                              `json:"text"`
	Segments util.Option[[]InferenceTranscriptionVerboseSegment] `json:"segments,omitempty"`
	Words    util.Option[[]InferenceTranscriptionWord]           `json:"words,omitempty"`
	Usage    util.Option[InferenceTranscriptionUsage]            `json:"usage,omitempty"`
}

type InferenceTranscriptionVerboseSegment struct {
	ID               int     `json:"id"`
	Seek             int     `json:"seek"`
	Start            float64 `json:"start"`
	End              float64 `json:"end"`
	Text             string  `json:"text"`
	Tokens           []int   `json:"tokens"`
	Temperature      float64 `json:"temperature"`
	AvgLogprob       float64 `json:"avg_logprob"`
	CompressionRatio float64 `json:"compression_ratio"`
	NoSpeechProb     float64 `json:"no_speech_prob"`
}

type InferenceTranscriptionWord struct {
	Start float64 `json:"start"`
	End   float64 `json:"end"`
	Word  string  `json:"word"`
}

type InferenceTranscriptionStreamEvent struct {
	Type     string                                       `json:"type"`
	Delta    string                                       `json:"delta,omitempty"`
	Text     string                                       `json:"text,omitempty"`
	Logprobs util.Option[[]InferenceTranscriptionLogprob] `json:"logprobs,omitempty"`
	Usage    util.Option[InferenceTranscriptionUsage]     `json:"usage,omitempty"`
}

func InferenceTranscriptionParseRequest(r *http.Request) (InferenceTranscriptionRequest, *util.HttpError) {
	if err := r.ParseMultipartForm(32 << 20); err != nil {
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	defer util.SilentClose(file)

	fileData, err := io.ReadAll(file)
	if err != nil {
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	request := InferenceTranscriptionRequest{
		File: InferenceTranscriptionFile{
			Name:        header.Filename,
			ContentType: header.Header.Get("Content-Type"),
			Data:        fileData,
		},
		Model:                  r.FormValue("model"),
		ResponseFormat:         InferenceTranscriptionResponseFormat(strings.TrimSpace(r.FormValue("response_format"))),
		Stream:                 parseFormBool(r.FormValue("stream")),
		Include:                r.MultipartForm.Value["include[]"],
		TimestampGranularities: r.MultipartForm.Value["timestamp_granularities[]"],
		KnownSpeakerNames:      r.MultipartForm.Value["known_speaker_names[]"],
		KnownSpeakerReferences: r.MultipartForm.Value["known_speaker_references[]"],
	}

	if request.ResponseFormat == "" {
		request.ResponseFormat = InferenceTranscriptionRespJson
	}

	if v := strings.TrimSpace(r.FormValue("language")); v != "" {
		request.Language = util.OptValue(v)
	}
	if v := strings.TrimSpace(r.FormValue("prompt")); v != "" {
		request.Prompt = util.OptValue(v)
	}
	if v := strings.TrimSpace(r.FormValue("temperature")); v != "" {
		if parsed, err := parseFormFloat(v); err == nil {
			request.Temperature = util.OptValue(parsed)
		}
	}
	if v := strings.TrimSpace(r.FormValue("chunking_strategy")); v != "" {
		request.ChunkingStrategy = util.OptValue(v)
	}

	return request, nil
}

func InferenceTranscribe(owner apm.WalletOwner, request InferenceTranscriptionRequest) (InferenceTranscriptionResponse, *util.HttpError) {
	if inferenceIsLocked(owner) {
		return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	body, contentType, httpErr := inferenceBuildTranscriptionMultipart(request)
	if httpErr != nil {
		return InferenceTranscriptionResponse{}, httpErr
	}

	respBody, httpErr := inferenceBackendJSONRequest(http.MethodPost, "/audio/transcriptions", body, contentType)
	if httpErr != nil {
		return InferenceTranscriptionResponse{}, httpErr
	}

	inferenceReportTranscriptionUsage(owner, request, respBody)

	if request.ResponseFormat == InferenceTranscriptionRespDiarizedJson {
		var resp InferenceTranscriptionDiarizedResponse
		if err := json.Unmarshal(respBody, &resp); err == nil {
			return InferenceTranscriptionResponse{DiarizedJson: &resp}, nil
		} else {
			return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
		}
	}
	if request.ResponseFormat == InferenceTranscriptionRespVerboseJson {
		var resp InferenceTranscriptionVerboseResponse
		if err := json.Unmarshal(respBody, &resp); err == nil {
			return InferenceTranscriptionResponse{VerboseJson: &resp}, nil
		} else {
			return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
		}
	}

	var resp InferenceTranscriptionJsonResponse
	if err := json.Unmarshal(respBody, &resp); err == nil {
		return InferenceTranscriptionResponse{Json: &resp}, nil
	} else {
		return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
	}
}

func InferenceTranscribeStreaming(owner apm.WalletOwner, request InferenceTranscriptionRequest) chan InferenceTranscriptionStreamEvent {
	ch := make(chan InferenceTranscriptionStreamEvent)

	if inferenceIsLocked(owner) {
		close(ch)
		return ch
	}

	go func() {
		defer close(ch)

		request.Stream = true
		body, contentType, httpErr := inferenceBuildTranscriptionMultipart(request)
		if httpErr != nil {
			return
		}

		resp, httpErr := inferenceBackendRequest(http.MethodPost, "/audio/transcriptions", body, contentType)
		if httpErr != nil {
			return
		}
		defer util.SilentClose(resp.Body)

		reader := bufio.NewReader(resp.Body)
		var event bytes.Buffer

		flush := func() {
			if event.Len() == 0 {
				return
			}

			raw := strings.TrimSpace(event.String())
			event.Reset()
			if raw == "" || raw == "data: [DONE]" {
				return
			}

			raw = strings.TrimPrefix(raw, "data: ")
			var parsed InferenceTranscriptionStreamEvent
			if err := json.Unmarshal([]byte(raw), &parsed); err == nil {
				if parsed.Type == "transcript.text.done" {
					inferenceReportTranscriptionStreamUsage(owner, request, parsed)
				}
				ch <- parsed
			}
		}

		for {
			line, readErr := reader.ReadBytes('\n')
			if len(line) > 0 {
				event.Write(line)
				if bytes.HasSuffix(event.Bytes(), []byte("\n\n")) {
					flush()
				}
			}

			if readErr != nil {
				if readErr == io.EOF {
					flush()
				}
				return
			}
		}
	}()

	return ch
}

func inferenceBuildTranscriptionMultipart(request InferenceTranscriptionRequest) ([]byte, string, *util.HttpError) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	if request.File.Name == "" {
		request.File.Name = "audio"
	}
	part, err := writer.CreateFormFile("file", request.File.Name)
	if err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if _, err := part.Write(request.File.Data); err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	writeField := func(key string, value string) {
		if value != "" {
			_ = writer.WriteField(key, value)
		}
	}

	writeField("model", request.Model)
	writeField("response_format", string(request.ResponseFormat))
	writeField("stream", fmt.Sprint(request.Stream))
	if request.Language.Present {
		writeField("language", request.Language.Value)
	}
	if request.Prompt.Present {
		writeField("prompt", request.Prompt.Value)
	}
	if request.Temperature.Present {
		writeField("temperature", fmt.Sprint(request.Temperature.Value))
	}
	if request.ChunkingStrategy.Present {
		writeField("chunking_strategy", request.ChunkingStrategy.Value)
	}
	for _, v := range request.Include {
		writeField("include[]", v)
	}
	for _, v := range request.TimestampGranularities {
		writeField("timestamp_granularities[]", v)
	}
	for _, v := range request.KnownSpeakerNames {
		writeField("known_speaker_names[]", v)
	}
	for _, v := range request.KnownSpeakerReferences {
		writeField("known_speaker_references[]", v)
	}

	if err := writer.Close(); err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	return buf.Bytes(), writer.FormDataContentType(), nil
}

func inferenceReportTranscriptionUsage(owner apm.WalletOwner, request InferenceTranscriptionRequest, responseBody []byte) {
	promptTokens, completionTokens := inferenceUsageFromTranscriptionResponse(responseBody)
	if promptTokens == 0 && completionTokens == 0 {
		var payload struct {
			Text string `json:"text"`
		}
		_ = json.Unmarshal(responseBody, &payload)
		completionTokens = inferenceEstimateTokensFromText(payload.Text)
	}
	inferenceReportUsage(owner, promptTokens, completionTokens)
}

func inferenceReportTranscriptionStreamUsage(owner apm.WalletOwner, request InferenceTranscriptionRequest, event InferenceTranscriptionStreamEvent) {
	if usage := event.Usage; usage.Present && usage.Value.Type == "tokens" {
		inputTokens := usage.Value.InputTokens.GetOrDefault(0)
		outputTokens := usage.Value.OutputTokens.GetOrDefault(0)
		inferenceReportUsage(owner, inputTokens, outputTokens)
		return
	}

	if event.Text != "" {
		inferenceReportUsage(owner, 0, inferenceEstimateTokensFromText(event.Text))
	}
}

// Image generation
// =====================================================================================================================

type InferenceImageGenerationRequest struct {
	Background        util.Option[string] `json:"background,omitempty"`
	Model             util.Option[string] `json:"model,omitempty"`
	Moderation        util.Option[string] `json:"moderation,omitempty"`
	N                 util.Option[int]    `json:"n,omitempty"`
	OutputCompression util.Option[int]    `json:"output_compression,omitempty"`
	OutputFormat      util.Option[string] `json:"output_format,omitempty"`
	PartialImages     util.Option[int]    `json:"partial_images,omitempty"`
	Prompt            string              `json:"prompt"`
	Quality           util.Option[string] `json:"quality,omitempty"`
	ResponseFormat    util.Option[string] `json:"response_format,omitempty"`
	Size              util.Option[string] `json:"size,omitempty"`
	Stream            util.Option[bool]   `json:"stream,omitempty"`
	Style             util.Option[string] `json:"style,omitempty"`
	User              util.Option[string] `json:"user,omitempty"`
}

type InferenceImageGenerationResponse struct {
	Created      int64                                      `json:"created"`
	Background   util.Option[string]                        `json:"background,omitempty"`
	Data         []InferenceImageGenerationResponseEl       `json:"data,omitempty"`
	OutputFormat util.Option[string]                        `json:"output_format,omitempty"`
	Quality      util.Option[string]                        `json:"quality,omitempty"`
	Size         util.Option[string]                        `json:"size,omitempty"`
	Usage        util.Option[InferenceImageGenerationUsage] `json:"usage,omitempty"`
}

type InferenceImageGenerationResponseEl struct {
	B64JSON       util.Option[string] `json:"b64_json,omitempty"`
	RevisedPrompt util.Option[string] `json:"revised_prompt,omitempty"`
	URL           util.Option[string] `json:"url,omitempty"`
}

type InferenceImageGenerationUsage struct {
	InputTokens         util.Option[int]                                        `json:"input_tokens,omitempty"`
	InputTokensDetails  util.Option[InferenceImageGenerationInputTokenDetails]  `json:"input_tokens_details,omitempty"`
	OutputTokens        util.Option[int]                                        `json:"output_tokens,omitempty"`
	TotalTokens         util.Option[int]                                        `json:"total_tokens,omitempty"`
	OutputTokensDetails util.Option[InferenceImageGenerationOutputTokenDetails] `json:"output_tokens_details,omitempty"`
}

type InferenceImageGenerationInputTokenDetails struct {
	ImageTokens util.Option[int] `json:"image_tokens,omitempty"`
	TextTokens  util.Option[int] `json:"text_tokens,omitempty"`
}

type InferenceImageGenerationOutputTokenDetails struct {
	ImageTokens util.Option[int] `json:"image_tokens,omitempty"`
	TextTokens  util.Option[int] `json:"text_tokens,omitempty"`
}

type InferenceImageGenerationStreamEvent struct {
	Type              string                                     `json:"type"`
	B64JSON           util.Option[string]                        `json:"b64_json,omitempty"`
	PartialImageIndex util.Option[int]                           `json:"partial_image_index,omitempty"`
	Usage             util.Option[InferenceImageGenerationUsage] `json:"usage,omitempty"`
}

func inferenceGenerateImageResponse(owner apm.WalletOwner, request InferenceImageGenerationRequest) ([]byte, *util.HttpError) {
	resp, httpErr := InferenceGenerateImage(owner, request)
	if httpErr != nil {
		return nil, httpErr
	}

	respBody, err := json.Marshal(resp)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	return respBody, nil
}

func InferenceGenerateImage(owner apm.WalletOwner, request InferenceImageGenerationRequest) (InferenceImageGenerationResponse, *util.HttpError) {
	if inferenceIsLocked(owner) {
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	if inferenceGlobals.MockImageGeneration {
		resp, httpErr := inferenceGenerateMockImageResponse(request)
		if httpErr != nil {
			return InferenceImageGenerationResponse{}, httpErr
		}

		inferenceReportImageUsage(owner, request, resp)
		return resp, nil
	}

	body, err := json.Marshal(request)
	if err != nil {
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	respBody, httpErr := inferenceBackendJSONRequest(http.MethodPost, "/images/generations", body, "application/json")
	if httpErr != nil {
		return InferenceImageGenerationResponse{}, httpErr
	}

	var resp InferenceImageGenerationResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	inferenceReportImageUsage(owner, request, resp)
	return resp, nil
}

func InferenceGenerateImageStreaming(owner apm.WalletOwner, request InferenceImageGenerationRequest) chan InferenceImageGenerationStreamEvent {
	ch := make(chan InferenceImageGenerationStreamEvent)

	if inferenceIsLocked(owner) {
		close(ch)
		return ch
	}

	go func() {
		defer close(ch)

		request.Stream.Set(true)
		if inferenceGlobals.MockImageGeneration {
			resp, httpErr := inferenceGenerateMockImageResponse(request)
			if httpErr != nil {
				return
			}

			if len(resp.Data) > 0 {
				ch <- InferenceImageGenerationStreamEvent{
					Type:    "image_generation.completed",
					B64JSON: resp.Data[0].B64JSON,
					Usage:   resp.Usage,
				}
			}
			inferenceReportImageUsage(owner, request, resp)
			return
		}

		body, err := json.Marshal(request)
		if err != nil {
			return
		}

		resp, httpErr := inferenceBackendRequest(http.MethodPost, "/images/generations", body, "application/json")
		if httpErr != nil {
			return
		}
		defer util.SilentClose(resp.Body)

		reader := bufio.NewReader(resp.Body)
		var event bytes.Buffer

		flush := func() {
			if event.Len() == 0 {
				return
			}

			raw := strings.TrimSpace(event.String())
			event.Reset()
			if raw == "" {
				return
			}

			var payload string
			for _, line := range strings.Split(raw, "\n") {
				line = strings.TrimSpace(line)
				if strings.HasPrefix(line, "data: ") {
					payload = strings.TrimPrefix(line, "data: ")
				}
			}

			if payload == "" || payload == "[DONE]" {
				return
			}

			var parsed InferenceImageGenerationStreamEvent
			if err := json.Unmarshal([]byte(payload), &parsed); err == nil {
				if parsed.Type == "image_generation.completed" {
					inferenceReportImageStreamUsage(owner, request, parsed)
				}
				ch <- parsed
			}
		}

		for {
			line, readErr := reader.ReadBytes('\n')
			if len(line) > 0 {
				event.Write(line)
				if bytes.HasSuffix(event.Bytes(), []byte("\n\n")) {
					flush()
				}
			}

			if readErr != nil {
				if readErr == io.EOF {
					flush()
				}
				return
			}
		}
	}()

	return ch
}

func inferenceReportImageUsage(owner apm.WalletOwner, request InferenceImageGenerationRequest, response InferenceImageGenerationResponse) {
	promptTokens, completionTokens := inferenceImageUsageFromResponse(request, response)
	inferenceReportUsage(owner, promptTokens, completionTokens)
}

func inferenceReportImageStreamUsage(owner apm.WalletOwner, request InferenceImageGenerationRequest, event InferenceImageGenerationStreamEvent) {
	if usage := event.Usage; usage.Present {
		inputTokens := usage.Value.InputTokens.GetOrDefault(0)
		outputTokens := usage.Value.OutputTokens.GetOrDefault(0)
		if outputTokens == 0 {
			outputTokens = usage.Value.TotalTokens.GetOrDefault(0)
		}
		inferenceReportUsage(owner, inputTokens, outputTokens)
	}
	_ = request
}

func inferenceImageUsageFromResponse(request InferenceImageGenerationRequest, response InferenceImageGenerationResponse) (promptTokens int, completionTokens int) {
	if usage := response.Usage; usage.Present {
		inputTokens := usage.Value.InputTokens.GetOrDefault(0)
		outputTokens := usage.Value.OutputTokens.GetOrDefault(0)
		if outputTokens == 0 {
			outputTokens = usage.Value.TotalTokens.GetOrDefault(0)
		}
		return inputTokens, outputTokens
	}

	imageCount := len(response.Data)
	if imageCount == 0 {
		imageCount = inferenceImageRequestCount(request)
	}
	width, height := inferenceImageRequestSize(request)

	megaPixels := float64(width*height) / 1_000_000.0
	completionTokens = int(math.Round(float64(imageCount) * megaPixels * inferenceImageGenerationTokensPerMegaPixel))
	if completionTokens < 1 && imageCount > 0 {
		completionTokens = 1
	}

	return 0, completionTokens
}

func inferenceImageRequestCount(request InferenceImageGenerationRequest) int {
	if request.N.Present && request.N.Value > 0 {
		return request.N.Value
	}

	return 1
}

func inferenceImageRequestSize(request InferenceImageGenerationRequest) (int, int) {
	if request.Size.Present {
		return inferenceParseImageSize(request.Size.Value)
	}

	return inferenceParseImageSize("")
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

func parseFormFloat(raw string) (float64, error) {
	return strconv.ParseFloat(raw, 64)
}

func inferenceBackendJSONRequest(method string, path string, body []byte, contentType string) ([]byte, *util.HttpError) {
	resp, httpErr := inferenceBackendRequest(method, path, body, contentType)
	if httpErr != nil {
		return nil, httpErr
	}
	defer util.SilentClose(resp.Body)

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, util.HttpErr(resp.StatusCode, "invalid request")
	}

	return respBody, nil
}

func inferenceBackendStreamRequest(path string, body []byte) (*http.Response, *util.HttpError) {
	return inferenceBackendRequest(http.MethodPost, path, body, "application/json")
}

func inferenceBackendRequest(method string, path string, body []byte, contentType string) (*http.Response, *util.HttpError) {
	backend := strings.TrimRight(inferenceGlobals.BackendServer, "/")
	if backend == "" {
		return nil, util.HttpErr(http.StatusServiceUnavailable, "inference backend is not configured")
	}

	req, err := http.NewRequest(method, backend+path, bytes.NewBuffer(body))
	if err != nil {
		return nil, util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	req.Header.Set("Authorization", "Bearer notused")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid request")
	}

	return resp, nil
}
