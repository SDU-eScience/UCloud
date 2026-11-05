package inference

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strings"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const (
	inferenceImageGenerationTokensPerMegaPixel = 1000.0
	inferenceMaxImageUpstreamJSONBytes         = 64 << 20
	inferenceDefaultImageDimension             = 1024
)

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
	Created      int64                                `json:"created"`
	Background   util.Option[string]                  `json:"background,omitempty"`
	Data         []InferenceImageGenerationResponseEl `json:"data,omitempty"`
	OutputFormat util.Option[string]                  `json:"output_format,omitempty"`
	Quality      util.Option[string]                  `json:"quality,omitempty"`
	Size         util.Option[string]                  `json:"size,omitempty"`
	Usage        InferenceImageGenerationUsage        `json:"usage"`
}

type InferenceImageGenerationResponseEl struct {
	B64JSON       util.Option[string] `json:"b64_json,omitempty"`
	RevisedPrompt util.Option[string] `json:"revised_prompt,omitempty"`
	URL           util.Option[string] `json:"url,omitempty"`
}

type InferenceImageGenerationUsage struct {
	InputTokens         int                                                     `json:"input_tokens"`
	InputTokensDetails  util.Option[InferenceImageGenerationInputTokenDetails]  `json:"input_tokens_details,omitempty"`
	OutputTokens        int                                                     `json:"output_tokens"`
	TotalTokens         int                                                     `json:"total_tokens"`
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
	Type              string                        `json:"type"`
	B64JSON           util.Option[string]           `json:"b64_json,omitempty"`
	PartialImageIndex util.Option[int]              `json:"partial_image_index,omitempty"`
	Usage             InferenceImageGenerationUsage `json:"usage"`
}

func inferenceGenerateImageResponse(ctx context.Context, owner apm.WalletOwner, username string, request InferenceImageGenerationRequest) ([]byte, *util.HttpError) {
	resp, httpErr := InferenceGenerateImage(ctx, owner, username, request)
	if httpErr != nil {
		return nil, httpErr
	}

	respBody, err := json.Marshal(resp)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	return respBody, nil
}

func InferenceGenerateImage(ctx context.Context, owner apm.WalletOwner, username string, request InferenceImageGenerationRequest) (InferenceImageGenerationResponse, *util.HttpError) {
	release, httpErr := inferenceAcquire(ctx, owner, username)
	if httpErr != nil {
		return InferenceImageGenerationResponse{}, httpErr
	}
	defer release()
	return inferenceGenerateImageAcquired(ctx, owner, username, request)
}

func inferenceGenerateImageAcquired(ctx context.Context, owner apm.WalletOwner, username string, request InferenceImageGenerationRequest) (InferenceImageGenerationResponse, *util.HttpError) {
	requestStartedAt := time.Now()
	auditModel := request.Model.GetOrDefault("")
	requestOutcome := "error"
	auditUsage := InferenceImageGenerationUsage{}
	auditUsagePresent := false
	auditUsageEstimated := false
	defer func() {
		var auditUsagePtr *InferenceImageGenerationUsage
		if auditUsagePresent {
			auditUsagePtr = &auditUsage
		}
		inferenceAuditRecord(
			ctx,
			"inference.image",
			owner,
			username,
			requestStartedAt,
			inferenceAuditImageBody(auditModel, request, auditUsagePtr, auditUsageEstimated, requestOutcome),
			requestOutcome,
		)
	}()

	if inferenceIsLocked(owner) {
		requestOutcome = "payment_required"
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, request.Model.GetOrDefault(""))
	if httpErr != nil {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, httpErr
	}
	auditModel = model.Name
	if httpErr := inferenceRequireCapability(model, InferenceTextToImage); httpErr != nil {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, httpErr
	}
	if httpErr := inferenceValidateImageRequest(request); httpErr != nil {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, httpErr
	}

	request.Model.Set(model.Endpoint.BackendModelName)

	if inferenceGlobals.MockImageGeneration {
		resp, httpErr := inferenceGenerateMockImageResponse(request)
		if httpErr != nil {
			requestOutcome = "client_error"
			return InferenceImageGenerationResponse{}, httpErr
		}
		inferenceReportUsage(owner, username, model, 0, resp.Usage.InputTokens, resp.Usage.OutputTokens)
		auditUsage = resp.Usage
		auditUsagePresent = true
		auditUsageEstimated = true
		requestOutcome = "success_missing_usage"
		return resp, nil
	}

	body, err := json.Marshal(request)
	if err != nil {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	respBody, httpErr := inferenceBackendJSONRequestWithLimit(ctx, model.Endpoint.BasePath, http.MethodPost, "/images/generations", body, "application/json", inferenceMaxImageUpstreamJSONBytes)
	if httpErr != nil {
		if ctx.Err() != nil {
			usage := inferenceEstimateImageUsage(request, 0)
			inferenceReportUsage(owner, username, model, 0, usage.InputTokens, usage.OutputTokens)
			auditUsage = usage
			auditUsagePresent = true
			auditUsageEstimated = true
			requestOutcome = "client_cancelled"
		} else {
			requestOutcome = "upstream_error"
		}
		return InferenceImageGenerationResponse{}, httpErr
	}

	var resp struct {
		Created      int64                                      `json:"created"`
		Background   util.Option[string]                        `json:"background,omitempty"`
		Data         []InferenceImageGenerationResponseEl       `json:"data,omitempty"`
		OutputFormat util.Option[string]                        `json:"output_format,omitempty"`
		Quality      util.Option[string]                        `json:"quality,omitempty"`
		Size         util.Option[string]                        `json:"size,omitempty"`
		Usage        util.Option[InferenceImageGenerationUsage] `json:"usage"`
	}
	if err := json.Unmarshal(respBody, &resp); err != nil {
		requestOutcome = "upstream_error"
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusBadGateway, "invalid response")
	}
	if resp.Usage.Present && !inferenceImageUsageValid(resp.Usage.Value) {
		requestOutcome = "upstream_error"
		return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusBadGateway, "invalid usage from upstream")
	}

	usage, estimated := inferenceImageUsageForResponse(request, resp.Usage, len(resp.Data))
	result := InferenceImageGenerationResponse{
		Created:      resp.Created,
		Background:   resp.Background,
		Data:         resp.Data,
		OutputFormat: resp.OutputFormat,
		Quality:      resp.Quality,
		Size:         resp.Size,
		Usage:        usage,
	}
	inferenceReportUsage(owner, username, model, 0, usage.InputTokens, usage.OutputTokens)
	auditUsage = usage
	auditUsagePresent = true
	auditUsageEstimated = estimated
	requestOutcome = "success"
	if estimated {
		requestOutcome = "success_missing_usage"
		inferenceWarnMissingUsage("image", model.Name)
	}
	return result, nil
}

func InferenceGenerateImageStreaming(ctx context.Context, owner apm.WalletOwner, username string, request InferenceImageGenerationRequest) (chan InferenceImageGenerationStreamEvent, *util.HttpError) {
	release, httpErr := inferenceAcquire(ctx, owner, username)
	if httpErr != nil {
		ch := make(chan InferenceImageGenerationStreamEvent)
		close(ch)
		return ch, httpErr
	}
	return inferenceGenerateImageStreamingAcquired(ctx, owner, username, request, release)
}

func inferenceGenerateImageStreamingAcquired(ctx context.Context, owner apm.WalletOwner, username string, request InferenceImageGenerationRequest, release func()) (chan InferenceImageGenerationStreamEvent, *util.HttpError) {
	requestStartedAt := time.Now()
	auditModel := request.Model.GetOrDefault("")
	requestOutcome := "error"
	auditUsage := InferenceImageGenerationUsage{}
	auditUsagePresent := false
	auditUsageEstimated := false
	auditDone := false
	emitAudit := func() {
		if auditDone {
			return
		}
		auditDone = true
		var auditUsagePtr *InferenceImageGenerationUsage
		if auditUsagePresent {
			auditUsagePtr = &auditUsage
		}
		inferenceAuditRecord(
			ctx,
			"inference.image",
			owner,
			username,
			requestStartedAt,
			inferenceAuditImageBody(auditModel, request, auditUsagePtr, auditUsageEstimated, requestOutcome),
			requestOutcome,
		)
	}
	ch := make(chan InferenceImageGenerationStreamEvent)

	if inferenceIsLocked(owner) {
		close(ch)
		release()
		requestOutcome = "payment_required"
		emitAudit()
		return ch, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	model, httpErr := inferenceResolveModelForOwner(owner, request.Model.GetOrDefault(""))
	if httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}
	auditModel = model.Name
	if httpErr := inferenceRequireCapability(model, InferenceTextToImage); httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}
	if httpErr := inferenceValidateImageRequest(request); httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}

	request.Model.Set(model.Endpoint.BackendModelName)
	request.Stream.Set(true)

	if inferenceGlobals.MockImageGeneration {
		go func() {
			defer close(ch)
			defer release()
			defer emitAudit()
			resp, httpErr := inferenceGenerateMockImageResponse(request)
			if httpErr != nil {
				requestOutcome = "client_error"
				return
			}
			if len(resp.Data) > 0 {
				if !inferenceSend(ctx, ch, InferenceImageGenerationStreamEvent{
					Type:    "image_generation.completed",
					B64JSON: resp.Data[0].B64JSON,
					Usage:   resp.Usage,
				}) {
					requestOutcome = "client_cancelled"
					return
				}
			}
			inferenceReportUsage(owner, username, model, 0, resp.Usage.InputTokens, resp.Usage.OutputTokens)
			auditUsage = resp.Usage
			auditUsagePresent = true
			auditUsageEstimated = true
			requestOutcome = "success_missing_usage"
		}()
		return ch, nil
	}

	body, err := json.Marshal(request)
	if err != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	streamCtx, cancel, touch := inferenceStreamContext(ctx)
	resp, httpErr := inferenceBackendRequest(streamCtx, model.Endpoint.BasePath, http.MethodPost, "/images/generations", body, "application/json")
	if httpErr != nil {
		cancel()
		close(ch)
		release()
		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case streamCtx.Err() != nil:
			requestOutcome = "stream_timeout"
		default:
			requestOutcome = "upstream_error"
		}
		emitAudit()
		return ch, httpErr
	}

	go func() {
		defer close(ch)
		defer release()
		defer cancel()
		defer emitAudit()
		defer util.SilentClose(resp.Body)

		usageSeen := InferenceImageGenerationUsage{}
		usagePresent := false
		usageEstimated := false
		readErr := inferenceReadSSE(streamCtx, resp.Body, touch, func(event []byte) bool {
			raw := strings.TrimSpace(string(event))
			if raw == "" {
				return true
			}

			payload := inferenceSSEDataPayload(raw)
			if payload == "" || payload == "[DONE]" {
				return true
			}

			var parsed struct {
				Type              string                                     `json:"type"`
				B64JSON           util.Option[string]                        `json:"b64_json,omitempty"`
				PartialImageIndex util.Option[int]                           `json:"partial_image_index,omitempty"`
				Usage             util.Option[InferenceImageGenerationUsage] `json:"usage"`
			}
			if err := json.Unmarshal([]byte(payload), &parsed); err != nil {
				return true
			}
			if parsed.Usage.Present && !inferenceImageUsageValid(parsed.Usage.Value) {
				log.Warn("Inference upstream returned invalid negative image usage: model=%s", model.Name)
				parsed.Usage.Clear()
			}
			streamUsage := inferenceImageUsage(parsed.Usage)
			if parsed.Type == "image_generation.completed" {
				streamUsage, usageEstimated = inferenceImageUsageForResponse(request, parsed.Usage, 0)
				usageSeen = streamUsage
				usagePresent = true
			}
			return inferenceSend(streamCtx, ch, InferenceImageGenerationStreamEvent{
				Type:              parsed.Type,
				B64JSON:           parsed.B64JSON,
				PartialImageIndex: parsed.PartialImageIndex,
				Usage:             streamUsage,
			})
		})
		if readErr != nil && streamCtx.Err() == nil {
			log.Warn("Inference upstream image stream failed: model=%s err=%v", model.Name, readErr)
		}

		var usage InferenceImageGenerationUsage
		estimated := false
		if usagePresent {
			usage = usageSeen
			estimated = usageEstimated
		} else {
			usage = inferenceEstimateImageUsage(request, 0)
			estimated = true
		}
		inferenceReportUsage(owner, username, model, 0, usage.InputTokens, usage.OutputTokens)
		auditUsage = usage
		auditUsagePresent = true
		auditUsageEstimated = estimated

		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case streamCtx.Err() != nil:
			requestOutcome = "stream_timeout"
		case readErr != nil:
			requestOutcome = "upstream_error"
		case estimated:
			requestOutcome = "success_missing_usage"
			inferenceWarnMissingUsage("image-stream", model.Name)
		default:
			requestOutcome = "success"
		}
	}()

	return ch, nil
}

func inferenceValidateImageRequest(request InferenceImageGenerationRequest) *util.HttpError {
	if request.N.Present && (request.N.Value <= 0 || request.N.Value > 8) {
		return util.HttpErr(http.StatusBadRequest, "invalid number of images")
	}
	if request.PartialImages.Present && (request.PartialImages.Value < 0 || request.PartialImages.Value > 3) {
		return util.HttpErr(http.StatusBadRequest, "invalid number of partial images")
	}
	if len(request.Prompt) > 1<<20 {
		return util.HttpErr(http.StatusBadRequest, "image prompt is too large")
	}
	return nil
}

func inferenceImageRequestCount(request InferenceImageGenerationRequest) int {
	if request.N.Present && request.N.Value > 0 {
		return request.N.Value
	}
	return 1
}

func inferenceImageRequestSize(request InferenceImageGenerationRequest) (int, int) {
	raw := strings.TrimSpace(request.Size.GetOrDefault(""))
	if raw == "" || strings.EqualFold(raw, "auto") {
		return inferenceDefaultImageDimension, inferenceDefaultImageDimension
	}

	width := 0
	height := 0
	_, _ = fmt.Sscanf(raw, "%dx%d", &width, &height)
	if width <= 0 || height <= 0 {
		return inferenceDefaultImageDimension, inferenceDefaultImageDimension
	}
	if width > 8192 {
		width = 8192
	}
	if height > 8192 {
		height = 8192
	}
	return width, height
}

func inferenceImageUsage(usage util.Option[InferenceImageGenerationUsage]) InferenceImageGenerationUsage {
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
	return InferenceImageGenerationUsage{}
}

func inferenceImageUsageForResponse(request InferenceImageGenerationRequest, reported util.Option[InferenceImageGenerationUsage], imageCount int) (InferenceImageGenerationUsage, bool) {
	if reported.Present {
		usage := inferenceImageUsage(reported)
		if usage.OutputTokens > 0 || usage.TotalTokens > 0 {
			return usage, false
		}
	}
	return inferenceEstimateImageUsage(request, imageCount), true
}

func inferenceEstimateImageUsage(request InferenceImageGenerationRequest, imageCount int) InferenceImageGenerationUsage {
	if imageCount <= 0 {
		imageCount = inferenceImageRequestCount(request)
	}
	width, height := inferenceImageRequestSize(request)
	megaPixels := float64(width) * float64(height) / 1_000_000.0
	outputTokens := int(math.Round(float64(imageCount) * megaPixels * inferenceImageGenerationTokensPerMegaPixel))
	if outputTokens < 1 && imageCount > 0 {
		outputTokens = 1
	}
	return InferenceImageGenerationUsage{OutputTokens: outputTokens, TotalTokens: outputTokens}
}

func inferenceImageUsageValid(usage InferenceImageGenerationUsage) bool {
	if usage.InputTokens < 0 || usage.OutputTokens < 0 || usage.TotalTokens < 0 {
		return false
	}
	if usage.InputTokensDetails.Present {
		details := usage.InputTokensDetails.Value
		if (details.ImageTokens.Present && details.ImageTokens.Value < 0) || (details.TextTokens.Present && details.TextTokens.Value < 0) {
			return false
		}
	}
	if usage.OutputTokensDetails.Present {
		details := usage.OutputTokensDetails.Value
		if (details.ImageTokens.Present && details.ImageTokens.Value < 0) || (details.TextTokens.Present && details.TextTokens.Value < 0) {
			return false
		}
	}
	return true
}
