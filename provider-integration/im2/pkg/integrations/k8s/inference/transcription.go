package inference

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/pkg/integrations/k8s/filesystem"
	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const (
	inferenceMaxTranscriptionRequestBytes = 64 << 20
	inferenceMaxAudioMilliseconds         = 24 * 60 * 60 * 1000

	inferenceTranscriptionTaskSubPath  = "Inference/AudioTasks"
	inferenceTranscriptionTaskTimeout  = 125 * time.Second
	inferenceTranscriptionTaskDeadline = 120
)

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
	InferenceTranscriptionRespText         InferenceTranscriptionResponseFormat = "text"
	InferenceTranscriptionRespSrt          InferenceTranscriptionResponseFormat = "srt"
	InferenceTranscriptionRespVtt          InferenceTranscriptionResponseFormat = "vtt"
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
	Seconds           util.Option[float64]                                 `json:"seconds,omitempty"`
	InputTokens       int                                                  `json:"input_tokens"`
	OutputTokens      int                                                  `json:"output_tokens"`
	TotalTokens       int                                                  `json:"total_tokens"`
	InputTokenDetails util.Option[InferenceTranscriptionInputTokenDetails] `json:"input_token_details,omitempty"`
}

type InferenceTranscriptionResponse struct {
	AudioMilliseconds int64
	Json              *InferenceTranscriptionJsonResponse
	VerboseJson       *InferenceTranscriptionVerboseResponse
	DiarizedJson      *InferenceTranscriptionDiarizedResponse
	Raw               *InferenceTranscriptionRawResponse
}

type InferenceTranscriptionRawResponse struct {
	ContentType string
	Data        []byte
}

type InferenceTranscriptionJsonResponse struct {
	Text     string                                       `json:"text"`
	Logprobs util.Option[[]InferenceTranscriptionLogprob] `json:"logprobs,omitempty"`
	Usage    InferenceTranscriptionUsage                  `json:"usage"`
}

type InferenceTranscriptionDiarizedResponse struct {
	Task     string                                  `json:"task"`
	Duration float64                                 `json:"duration"`
	Text     string                                  `json:"text"`
	Segments []InferenceTranscriptionDiarizedSegment `json:"segments"`
	Usage    InferenceTranscriptionUsage             `json:"usage"`
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
	Usage    InferenceTranscriptionUsage                         `json:"usage"`
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
	Usage    InferenceTranscriptionUsage                  `json:"usage"`
}

func InferenceTranscriptionParseRequest(w http.ResponseWriter, r *http.Request) (InferenceTranscriptionRequest, *util.HttpError) {
	if r.ContentLength > inferenceMaxTranscriptionRequestBytes {
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	r.Body = http.MaxBytesReader(w, r.Body, inferenceMaxTranscriptionRequestBytes)
	if err := r.ParseMultipartForm(8 << 20); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
		}
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if r.MultipartForm != nil {
		defer r.MultipartForm.RemoveAll()
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

	responseFormat := InferenceTranscriptionResponseFormat(strings.TrimSpace(r.FormValue("response_format")))
	if responseFormat == "" {
		responseFormat = InferenceTranscriptionRespJson
	}
	switch responseFormat {
	case InferenceTranscriptionRespJson,
		InferenceTranscriptionRespDiarizedJson,
		InferenceTranscriptionRespVerboseJson,
		InferenceTranscriptionRespText,
		InferenceTranscriptionRespSrt,
		InferenceTranscriptionRespVtt:
	default:
		return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "unsupported response format")
	}

	request := InferenceTranscriptionRequest{
		File: InferenceTranscriptionFile{
			Name:        header.Filename,
			ContentType: header.Header.Get("Content-Type"),
			Data:        fileData,
		},
		Model:                  r.FormValue("model"),
		ResponseFormat:         responseFormat,
		Stream:                 parseFormBool(r.FormValue("stream")),
		Include:                inferenceTranscriptionFormValues(r, "include"),
		TimestampGranularities: inferenceTranscriptionFormValues(r, "timestamp_granularities"),
		KnownSpeakerNames:      inferenceTranscriptionFormValues(r, "known_speaker_names"),
		KnownSpeakerReferences: inferenceTranscriptionFormValues(r, "known_speaker_references"),
	}

	if request.Stream {
		switch responseFormat {
		case InferenceTranscriptionRespJson,
			InferenceTranscriptionRespDiarizedJson,
			InferenceTranscriptionRespVerboseJson:
		default:
			return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "streaming is not supported for this response format")
		}
	}

	if v := strings.TrimSpace(r.FormValue("language")); v != "" {
		request.Language = util.OptValue(v)
	}
	if v := strings.TrimSpace(r.FormValue("prompt")); v != "" {
		request.Prompt = util.OptValue(v)
	}
	if v := strings.TrimSpace(r.FormValue("temperature")); v != "" {
		parsed, err := strconv.ParseFloat(v, 64)
		if err != nil || parsed < 0 || parsed > 1 {
			return InferenceTranscriptionRequest{}, util.HttpErr(http.StatusBadRequest, "invalid temperature")
		}
		request.Temperature = util.OptValue(parsed)
	}
	if v := strings.TrimSpace(r.FormValue("chunking_strategy")); v != "" {
		request.ChunkingStrategy = util.OptValue(v)
	}

	return request, nil
}

func inferenceTranscriptionFormValues(r *http.Request, key string) []string {
	if r.MultipartForm == nil {
		return nil
	}
	values := append([]string{}, r.MultipartForm.Value[key+"[]"]...)
	values = append(values, r.MultipartForm.Value[key]...)
	return values
}

func inferenceMeasureAudioMilliseconds(ctx context.Context, username string, file InferenceTranscriptionFile) (int64, *util.HttpError) {
	if username == "" {
		return 0, util.HttpErr(http.StatusInternalServerError, "could not measure audio")
	}

	basePath, drive, httpErr := filesystem.InitializeMemberFiles(username, util.OptNone[string]())
	if httpErr != nil {
		log.Warn("Could not resolve member files for audio measurement: %v", httpErr)
		return 0, util.HttpErr(http.StatusServiceUnavailable, "could not measure audio")
	}

	dir := filepath.Join(basePath, inferenceTranscriptionTaskSubPath, "audio-"+util.SecureToken())
	if createErr := filesystem.DoCreateFolder(dir); createErr != nil {
		log.Warn("Could not create inference audio task directory: %v", createErr)
		return 0, util.HttpErr(http.StatusInternalServerError, "could not measure audio")
	}
	defer func() {
		if deleteErr := filesystem.DoDeleteFile(dir); deleteErr != nil {
			log.Warn("Could not delete inference audio task directory: path=%s err=%v", dir, deleteErr)
		}
	}()

	audioPath := filepath.Join(dir, "input"+inferenceAudioExtension(file.Name))
	if writeErr := filesystem.WriteFileAtomic(audioPath, file.Data, 0660); writeErr != nil {
		log.Warn("Could not write inference audio file: %v", writeErr)
		return 0, util.HttpErr(http.StatusInternalServerError, "could not measure audio")
	}

	ucloudDir, ok := filesystem.InternalToUCloudWithDrive(drive, dir)
	if !ok {
		return 0, util.HttpErr(http.StatusInternalServerError, "could not measure audio")
	}
	ucloudAudio, ok := filesystem.InternalToUCloudWithDrive(drive, audioPath)
	if !ok {
		return 0, util.HttpErr(http.StatusInternalServerError, "could not measure audio")
	}

	waitCtx, cancel := context.WithTimeout(ctx, inferenceTranscriptionTaskTimeout)
	defer cancel()
	taskErr := filesystem.TaskSubmitAndWait(waitCtx, filesystem.TaskSpec{
		Type:            filesystem.TaskTypeFFProbe,
		Internal:        true,
		Mounts:          []filesystem.TaskMount{{UCloudPath: ucloudDir}},
		Source:          ucloudAudio,
		DeadlineSeconds: util.OptValue(inferenceTranscriptionTaskDeadline),
		Resources:       util.OptValue(filesystem.TaskResources{CpuMilli: 1000, MemoryMB: 1024}),
		CreationState: struct {
			Username string
			Icon     string
		}{Username: username, Icon: "heroMusicalNote"},
	}, time.Second)
	if taskErr != nil {
		log.Warn("Inference audio measurement task failed: %v", taskErr)
	}

	data, readErr := filesystem.ReadFile(filepath.Join(dir, filesystem.TaskFFProbeDurationFile), 4096)
	if readErr != nil {
		return 0, inferenceAudioMeasurementError(taskErr)
	}
	var result filesystem.TaskFFProbeDuration
	if err := json.Unmarshal(data, &result); err != nil ||
		result.DurationMilliseconds <= 0 ||
		result.DurationMilliseconds > inferenceMaxAudioMilliseconds {
		return 0, inferenceAudioMeasurementError(taskErr)
	}
	return result.DurationMilliseconds, nil
}

func inferenceAudioMeasurementError(taskErr *util.HttpError) *util.HttpError {
	if taskErr != nil && strings.Contains(taskErr.Why, "unable to measure audio duration") {
		return util.HttpErr(http.StatusUnprocessableEntity, "could not measure audio")
	}
	return util.HttpErr(http.StatusServiceUnavailable, "could not measure audio")
}

func inferenceAudioExtension(name string) string {
	ext := strings.ToLower(filepath.Ext(filepath.Base(name)))
	if len(ext) < 2 || len(ext) > 11 {
		return ""
	}
	for _, r := range ext[1:] {
		if (r < 'a' || r > 'z') && (r < '0' || r > '9') {
			return ""
		}
	}
	return ext
}

func InferenceTranscribe(ctx context.Context, owner apm.WalletOwner, username string, request InferenceTranscriptionRequest) (InferenceTranscriptionResponse, *util.HttpError) {
	release, httpErr := inferenceAcquire(ctx, owner, username)
	if httpErr != nil {
		return InferenceTranscriptionResponse{}, httpErr
	}
	defer release()
	return inferenceTranscribeAcquired(ctx, owner, username, request)
}

func inferenceTranscribeAcquired(ctx context.Context, owner apm.WalletOwner, username string, request InferenceTranscriptionRequest) (InferenceTranscriptionResponse, *util.HttpError) {
	requestStartedAt := time.Now()
	auditModel := request.Model
	requestOutcome := "error"
	audioMilliseconds := int64(0)
	defer func() {
		inferenceAuditRecord(
			ctx,
			"inference.transcribe",
			owner,
			username,
			requestStartedAt,
			inferenceAuditTranscribeBody(auditModel, request, audioMilliseconds, requestOutcome),
			requestOutcome,
		)
	}()

	if inferenceIsLocked(owner) {
		requestOutcome = "payment_required"
		return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, request.Model)
	if httpErr != nil {
		requestOutcome = "client_error"
		return InferenceTranscriptionResponse{}, httpErr
	}
	auditModel = model.Name
	if httpErr := inferenceRequireCapability(model, InferenceSpeechToText); httpErr != nil {
		requestOutcome = "client_error"
		return InferenceTranscriptionResponse{}, httpErr
	}

	audioMilliseconds, httpErr = inferenceMeasureAudioMilliseconds(ctx, username, request.File)
	if httpErr != nil {
		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case httpErr.StatusCode == http.StatusUnprocessableEntity:
			requestOutcome = "unprocessable"
		default:
			requestOutcome = "server_error"
		}
		return InferenceTranscriptionResponse{}, httpErr
	}

	request.Model = model.Endpoint.BackendModelName
	body, contentType, httpErr := inferenceBuildTranscriptionMultipart(request)
	if httpErr != nil {
		requestOutcome = "client_error"
		return InferenceTranscriptionResponse{}, httpErr
	}
	if len(body) > inferenceMaxTranscriptionRequestBytes {
		requestOutcome = "client_error"
		return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	if ctx.Err() != nil {
		requestOutcome = "client_cancelled"
		return InferenceTranscriptionResponse{}, util.HttpErr(499, "client disconnected")
	}

	respBody, httpErr := inferenceBackendJSONRequest(ctx, model.Endpoint.BasePath, http.MethodPost, "/audio/transcriptions", body, contentType)
	if httpErr != nil {
		if ctx.Err() != nil {
			inferenceReportBilling(owner, username, model, InferenceBillingUsage{AudioMilliseconds: audioMilliseconds})
			requestOutcome = "client_cancelled"
		} else {
			requestOutcome = "upstream_error"
		}
		return InferenceTranscriptionResponse{}, httpErr
	}
	inferenceReportBilling(owner, username, model, InferenceBillingUsage{AudioMilliseconds: audioMilliseconds})

	switch request.ResponseFormat {
	case InferenceTranscriptionRespText, InferenceTranscriptionRespSrt, InferenceTranscriptionRespVtt:
		requestOutcome = "success"
		return InferenceTranscriptionResponse{AudioMilliseconds: audioMilliseconds, Raw: &InferenceTranscriptionRawResponse{
			ContentType: inferenceTranscriptionRawContentType(request.ResponseFormat),
			Data:        respBody,
		}}, nil
	}

	switch request.ResponseFormat {
	case InferenceTranscriptionRespDiarizedJson:
		var resp struct {
			Task     string                                   `json:"task"`
			Duration float64                                  `json:"duration"`
			Text     string                                   `json:"text"`
			Segments []InferenceTranscriptionDiarizedSegment  `json:"segments"`
			Usage    util.Option[InferenceTranscriptionUsage] `json:"usage"`
		}
		if err := json.Unmarshal(respBody, &resp); err != nil {
			requestOutcome = "upstream_error"
			return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
		}
		if resp.Usage.Present && !inferenceTranscriptionUsageValid(resp.Usage.Value) {
			log.Warn("Inference upstream returned invalid transcription usage: model=%s", model.Name)
			resp.Usage.Clear()
		}
		usage := inferenceTranscriptionUsage(resp.Usage)
		requestOutcome = "success"
		return InferenceTranscriptionResponse{AudioMilliseconds: audioMilliseconds, DiarizedJson: &InferenceTranscriptionDiarizedResponse{
			Task:     resp.Task,
			Duration: resp.Duration,
			Text:     resp.Text,
			Segments: resp.Segments,
			Usage:    usage,
		}}, nil
	case InferenceTranscriptionRespVerboseJson:
		var resp struct {
			Task     string                                              `json:"task"`
			Language string                                              `json:"language"`
			Duration float64                                             `json:"duration"`
			Text     string                                              `json:"text"`
			Segments util.Option[[]InferenceTranscriptionVerboseSegment] `json:"segments,omitempty"`
			Words    util.Option[[]InferenceTranscriptionWord]           `json:"words,omitempty"`
			Usage    util.Option[InferenceTranscriptionUsage]            `json:"usage"`
		}
		if err := json.Unmarshal(respBody, &resp); err != nil {
			requestOutcome = "upstream_error"
			return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
		}
		if resp.Usage.Present && !inferenceTranscriptionUsageValid(resp.Usage.Value) {
			log.Warn("Inference upstream returned invalid transcription usage: model=%s", model.Name)
			resp.Usage.Clear()
		}
		usage := inferenceTranscriptionUsage(resp.Usage)
		requestOutcome = "success"
		return InferenceTranscriptionResponse{AudioMilliseconds: audioMilliseconds, VerboseJson: &InferenceTranscriptionVerboseResponse{
			Task:     resp.Task,
			Language: resp.Language,
			Duration: resp.Duration,
			Text:     resp.Text,
			Segments: resp.Segments,
			Words:    resp.Words,
			Usage:    usage,
		}}, nil
	case InferenceTranscriptionRespJson:
		var resp struct {
			Text     string                                       `json:"text"`
			Logprobs util.Option[[]InferenceTranscriptionLogprob] `json:"logprobs,omitempty"`
			Usage    util.Option[InferenceTranscriptionUsage]     `json:"usage"`
		}
		if err := json.Unmarshal(respBody, &resp); err != nil {
			requestOutcome = "upstream_error"
			return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response from upstream")
		}
		if resp.Usage.Present && !inferenceTranscriptionUsageValid(resp.Usage.Value) {
			log.Warn("Inference upstream returned invalid transcription usage: model=%s", model.Name)
			resp.Usage.Clear()
		}
		usage := inferenceTranscriptionUsage(resp.Usage)
		requestOutcome = "success"
		return InferenceTranscriptionResponse{AudioMilliseconds: audioMilliseconds, Json: &InferenceTranscriptionJsonResponse{
			Text:     resp.Text,
			Logprobs: resp.Logprobs,
			Usage:    usage,
		}}, nil
	default:
		requestOutcome = "client_error"
		return InferenceTranscriptionResponse{}, util.HttpErr(http.StatusBadRequest, "unsupported response format")
	}
}

func InferenceTranscribeStreaming(ctx context.Context, owner apm.WalletOwner, username string, request InferenceTranscriptionRequest) (chan InferenceTranscriptionStreamEvent, *util.HttpError) {
	release, httpErr := inferenceAcquire(ctx, owner, username)
	if httpErr != nil {
		ch := make(chan InferenceTranscriptionStreamEvent)
		close(ch)
		return ch, httpErr
	}
	return inferenceTranscribeStreamingAcquired(ctx, owner, username, request, release)
}

func inferenceTranscribeStreamingAcquired(ctx context.Context, owner apm.WalletOwner, username string, request InferenceTranscriptionRequest, release func()) (chan InferenceTranscriptionStreamEvent, *util.HttpError) {
	requestStartedAt := time.Now()
	auditModel := request.Model
	requestOutcome := "error"
	audioMilliseconds := int64(0)
	auditDone := false
	emitAudit := func() {
		if auditDone {
			return
		}
		auditDone = true
		inferenceAuditRecord(
			ctx,
			"inference.transcribe",
			owner,
			username,
			requestStartedAt,
			inferenceAuditTranscribeBody(auditModel, request, audioMilliseconds, requestOutcome),
			requestOutcome,
		)
	}
	ch := make(chan InferenceTranscriptionStreamEvent)

	if inferenceIsLocked(owner) {
		close(ch)
		release()
		requestOutcome = "payment_required"
		emitAudit()
		return ch, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, request.Model)
	if httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}
	auditModel = model.Name
	if httpErr := inferenceRequireCapability(model, InferenceSpeechToText); httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}

	audioMilliseconds, httpErr = inferenceMeasureAudioMilliseconds(ctx, username, request.File)
	if httpErr != nil {
		close(ch)
		release()
		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case httpErr.StatusCode == http.StatusUnprocessableEntity:
			requestOutcome = "unprocessable"
		default:
			requestOutcome = "server_error"
		}
		emitAudit()
		return ch, httpErr
	}

	request.Model = model.Endpoint.BackendModelName
	request.Stream = true
	body, contentType, httpErr := inferenceBuildTranscriptionMultipart(request)
	if httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}
	if len(body) > inferenceMaxTranscriptionRequestBytes {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	if ctx.Err() != nil {
		close(ch)
		release()
		requestOutcome = "client_cancelled"
		emitAudit()
		return ch, util.HttpErr(499, "client disconnected")
	}

	streamCtx, cancel, touch := inferenceStreamContext(ctx)
	resp, httpErr := inferenceBackendRequest(streamCtx, model.Endpoint.BasePath, http.MethodPost, "/audio/transcriptions", body, contentType)
	if httpErr != nil {
		cancel()
		close(ch)
		release()
		switch {
		case ctx.Err() != nil:
			inferenceReportBilling(owner, username, model, InferenceBillingUsage{AudioMilliseconds: audioMilliseconds})
			requestOutcome = "client_cancelled"
		case streamCtx.Err() != nil:
			requestOutcome = "stream_timeout"
		default:
			requestOutcome = "upstream_error"
		}
		emitAudit()
		return ch, httpErr
	}

	inferenceReportBilling(owner, username, model, InferenceBillingUsage{AudioMilliseconds: audioMilliseconds})

	go func() {
		defer close(ch)
		defer release()
		defer cancel()
		defer emitAudit()

		defer util.SilentClose(resp.Body)

		usageSeen := InferenceTranscriptionUsage{}
		usagePresent := false
		readErr := inferenceReadSSE(streamCtx, resp.Body, touch, func(event []byte) bool {
			raw := strings.TrimSpace(string(event))
			if raw == "" || raw == "data: [DONE]" {
				return true
			}

			raw = inferenceSSEDataPayload(raw)
			var parsed struct {
				Type     string                                       `json:"type"`
				Delta    string                                       `json:"delta,omitempty"`
				Text     string                                       `json:"text,omitempty"`
				Logprobs util.Option[[]InferenceTranscriptionLogprob] `json:"logprobs,omitempty"`
				Usage    util.Option[InferenceTranscriptionUsage]     `json:"usage"`
			}
			if err := json.Unmarshal([]byte(raw), &parsed); err != nil {
				return true
			}
			if parsed.Usage.Present {
				if inferenceTranscriptionUsageValid(parsed.Usage.Value) {
					usageSeen = parsed.Usage.Value
					usagePresent = true
				} else {
					log.Warn("Inference upstream returned invalid negative transcription usage: model=%s", model.Name)
					parsed.Usage.Clear()
				}
			}
			streamUsage := inferenceTranscriptionUsage(parsed.Usage)
			if !parsed.Usage.Present && usagePresent {
				streamUsage = usageSeen
			}
			return inferenceSend(streamCtx, ch, InferenceTranscriptionStreamEvent{
				Type:     parsed.Type,
				Delta:    parsed.Delta,
				Text:     parsed.Text,
				Logprobs: parsed.Logprobs,
				Usage:    streamUsage,
			})
		})

		if readErr != nil && streamCtx.Err() == nil {
			log.Warn("Inference upstream transcription stream failed: model=%s err=%v", model.Name, readErr)
		}

		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case streamCtx.Err() != nil:
			requestOutcome = "stream_timeout"
		case readErr != nil:
			requestOutcome = "upstream_error"
		default:
			requestOutcome = "success"
		}
	}()

	return ch, nil
}

func inferenceBuildTranscriptionMultipart(request InferenceTranscriptionRequest) ([]byte, string, *util.HttpError) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	if request.File.Name == "" {
		request.File.Name = "audio"
	}
	filename := strings.ReplaceAll(request.File.Name, "\r", "")
	filename = strings.ReplaceAll(filename, "\n", "")
	filename = strings.ReplaceAll(filename, `\`, `\\`)
	filename = strings.ReplaceAll(filename, `"`, `\"`)
	header := make(textproto.MIMEHeader)
	header.Set("Content-Disposition", fmt.Sprintf(`form-data; name="file"; filename="%s"`, filename))
	contentType := request.File.ContentType
	if contentType == "" {
		contentType = "application/octet-stream"
	}
	header.Set("Content-Type", contentType)
	part, err := writer.CreatePart(header)
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

func inferenceTranscriptionRawContentType(format InferenceTranscriptionResponseFormat) string {
	switch format {
	case InferenceTranscriptionRespText:
		return "text/plain; charset=utf-8"
	case InferenceTranscriptionRespSrt:
		return "application/x-subrip; charset=utf-8"
	case InferenceTranscriptionRespVtt:
		return "text/vtt; charset=utf-8"
	default:
		return "application/json"
	}
}

func inferenceTranscriptionUsage(usage util.Option[InferenceTranscriptionUsage]) InferenceTranscriptionUsage {
	if usage.Present {
		result := usage.Value
		if result.TotalTokens == 0 {
			result.TotalTokens = result.InputTokens + result.OutputTokens
		}
		if result.TotalTokens > result.InputTokens+result.OutputTokens {
			result.OutputTokens += result.TotalTokens - (result.InputTokens + result.OutputTokens)
		}
		return result
	}
	return InferenceTranscriptionUsage{}
}

func inferenceTranscriptionUsageValid(usage InferenceTranscriptionUsage) bool {
	if usage.Seconds.Present && (usage.Seconds.Value < 0 || math.IsNaN(usage.Seconds.Value) || math.IsInf(usage.Seconds.Value, 0)) {
		return false
	}
	if usage.InputTokens < 0 || usage.OutputTokens < 0 || usage.TotalTokens < 0 {
		return false
	}
	if usage.InputTokenDetails.Present {
		details := usage.InputTokenDetails.Value
		return (!details.AudioTokens.Present || details.AudioTokens.Value >= 0) && (!details.TextTokens.Present || details.TextTokens.Value >= 0)
	}
	return true
}
