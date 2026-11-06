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

	Image *InferenceImageEditFile `json:"-"`
	Mask  *InferenceImageEditFile `json:"-"`
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
	URL               util.Option[string]           `json:"url,omitempty"`
	PartialImageIndex util.Option[int]              `json:"partial_image_index,omitempty"`
	Size              util.Option[string]           `json:"size,omitempty"`
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
	capability := InferenceTextToImage
	if request.Image != nil {
		capability = InferenceImageToImage
	}
	if httpErr := inferenceRequireCapability(model, capability); httpErr != nil {
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
		measured, imageCount, imagePixels, estimated, httpErr := inferenceMeasureImageElements(
			resp.Data,
			model,
			request.Size.GetOrDefault(""),
			resp.Size.GetOrDefault(""),
		)
		if httpErr != nil {
			requestOutcome = "upstream_error"
			return InferenceImageGenerationResponse{}, httpErr
		}
		resp.Data = measured
		inferenceReportBilling(owner, username, model, InferenceBillingUsage{
			ImageCount:  imageCount,
			ImagePixels: imagePixels,
		})
		auditUsagePresent = false
		auditUsageEstimated = estimated
		requestOutcome = "success"
		return resp, nil
	}

	path, body, contentType, httpErr := inferenceImageBackendBody(request)
	if httpErr != nil {
		requestOutcome = "client_error"
		return InferenceImageGenerationResponse{}, httpErr
	}

	respBody, httpErr := inferenceBackendJSONRequestWithLimit(ctx, model.Endpoint.BasePath, http.MethodPost, path, body, contentType, inferenceMaxImageUpstreamJSONBytes)
	if httpErr != nil {
		if ctx.Err() != nil {
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
		log.Warn("Inference upstream returned invalid image usage: model=%s", model.Name)
		resp.Usage.Clear()
	}

	measured, imageCount, imagePixels, estimated, httpErr := inferenceMeasureImageElements(
		resp.Data,
		model,
		request.Size.GetOrDefault(""),
		resp.Size.GetOrDefault(""),
	)
	if httpErr != nil {
		requestOutcome = "upstream_error"
		return InferenceImageGenerationResponse{}, httpErr
	}

	result := InferenceImageGenerationResponse{
		Created:      resp.Created,
		Background:   resp.Background,
		Data:         measured,
		OutputFormat: resp.OutputFormat,
		Quality:      resp.Quality,
		Size:         resp.Size,
		Usage:        inferenceImageUsage(resp.Usage),
	}
	inferenceReportBilling(owner, username, model, InferenceBillingUsage{
		ImageCount:  imageCount,
		ImagePixels: imagePixels,
	})
	auditUsagePresent = false
	auditUsageEstimated = estimated
	requestOutcome = "success"
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
	capability := InferenceTextToImage
	if request.Image != nil {
		capability = InferenceImageToImage
	}
	if httpErr := inferenceRequireCapability(model, capability); httpErr != nil {
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
			measured, imageCount, imagePixels, estimated, httpErr := inferenceMeasureImageElements(
				resp.Data,
				model,
				request.Size.GetOrDefault(""),
				resp.Size.GetOrDefault(""),
			)
			if httpErr != nil {
				requestOutcome = "upstream_error"
				return
			}
			for _, element := range measured {
				if !inferenceSend(ctx, ch, InferenceImageGenerationStreamEvent{
					Type:    "image_generation.completed",
					B64JSON: element.B64JSON,
					URL:     element.URL,
					Size:    resp.Size,
					Usage:   resp.Usage,
				}) {
					requestOutcome = "client_cancelled"
					break
				}
			}
			inferenceReportBilling(owner, username, model, InferenceBillingUsage{
				ImageCount:  imageCount,
				ImagePixels: imagePixels,
			})
			auditUsagePresent = false
			auditUsageEstimated = estimated
			if requestOutcome != "client_cancelled" {
				requestOutcome = "success"
			}
		}()
		return ch, nil
	}

	path, body, contentType, httpErr := inferenceImageBackendBody(request)
	if httpErr != nil {
		close(ch)
		release()
		requestOutcome = "client_error"
		emitAudit()
		return ch, httpErr
	}

	streamCtx, cancel, touch := inferenceStreamContext(ctx)
	resp, httpErr := inferenceBackendRequest(streamCtx, model.Endpoint.BasePath, http.MethodPost, path, body, contentType)
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
		imageCount := 0
		var imagePixels int64
		imageEstimated := false
		measurementFailed := false

		readErr := inferenceReadSSEWithLimit(streamCtx, resp.Body, touch, inferenceMaxImageUpstreamJSONBytes, func(event []byte) bool {
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
				URL               util.Option[string]                        `json:"url,omitempty"`
				PartialImageIndex util.Option[int]                           `json:"partial_image_index,omitempty"`
				Size              util.Option[string]                        `json:"size,omitempty"`
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
			if parsed.Usage.Present {
				usageSeen = streamUsage
				usagePresent = true
			}
			if parsed.B64JSON.Present && strings.TrimSpace(parsed.B64JSON.Value) == "" {
				parsed.B64JSON.Clear()
			}
			if parsed.URL.Present && strings.TrimSpace(parsed.URL.Value) == "" {
				parsed.URL.Clear()
			}
			if parsed.B64JSON.Present || parsed.URL.Present {
				if parsed.Type == "image_generation.completed" && imageCount >= inferenceMaxImageCount {
					log.Warn("Inference upstream returned too many images: model=%s", model.Name)
					measurementFailed = true
					return false
				}
				measured, pixels, estimated, httpErr := inferenceMeasureImageElement(
					InferenceImageGenerationResponseEl{B64JSON: parsed.B64JSON, URL: parsed.URL},
					model,
					request.Size.GetOrDefault(""),
					parsed.Size.GetOrDefault(""),
				)
				if httpErr != nil {
					log.Warn("Could not measure inference image stream: model=%s err=%v", model.Name, httpErr)
					measurementFailed = true
					return false
				}
				parsed.B64JSON = measured.B64JSON
				parsed.URL = measured.URL
				if parsed.Type == "image_generation.completed" {
					imageCount++
					imagePixels = inferenceUsageAdd(imagePixels, pixels)
					imageEstimated = imageEstimated || estimated
				}
			}
			return inferenceSend(streamCtx, ch, InferenceImageGenerationStreamEvent{
				Type:              parsed.Type,
				B64JSON:           parsed.B64JSON,
				URL:               parsed.URL,
				PartialImageIndex: parsed.PartialImageIndex,
				Size:              parsed.Size,
				Usage:             streamUsage,
			})
		})
		if readErr != nil && streamCtx.Err() == nil {
			log.Warn("Inference upstream image stream failed: model=%s err=%v", model.Name, readErr)
		}

		inferenceReportBilling(owner, username, model, InferenceBillingUsage{
			ImageCount:  imageCount,
			ImagePixels: imagePixels,
		})
		auditUsagePresent = false
		auditUsageEstimated = imageEstimated
		if usagePresent {
			auditUsage = usageSeen
			auditUsagePresent = true
		}

		switch {
		case ctx.Err() != nil:
			requestOutcome = "client_cancelled"
		case streamCtx.Err() != nil:
			requestOutcome = "stream_timeout"
		case measurementFailed:
			requestOutcome = "upstream_error"
		case readErr != nil:
			requestOutcome = "upstream_error"
		default:
			requestOutcome = "success"
		}
	}()

	return ch, nil
}

func inferenceImageBackendBody(request InferenceImageGenerationRequest) (string, []byte, string, *util.HttpError) {
	if request.Image != nil {
		body, contentType, httpErr := inferenceBuildImageEditMultipart(request)
		if httpErr != nil {
			return "", nil, "", httpErr
		}
		if len(body) > inferenceMaxImageEditRequestBytes {
			return "", nil, "", util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
		}
		return "/images/edits", body, contentType, nil
	}

	body, err := json.Marshal(request)
	if err != nil {
		return "", nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		return "", nil, "", util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	return "/images/generations", body, "application/json", nil
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
	if width > inferenceMaxImageDimension {
		width = inferenceMaxImageDimension
	}
	if height > inferenceMaxImageDimension {
		height = inferenceMaxImageDimension
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
