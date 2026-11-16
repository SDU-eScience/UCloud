package inference

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const (
	inferenceMaxVideoCreateRequestBytes = 64 << 20
	inferenceMaxVideoPromptBytes        = 1 << 20
	inferenceMaxVideoSeconds            = 600
	inferenceMaxVideoDimension          = 8192
	inferenceVideoDispatchTimeout       = 5 * time.Minute
	inferenceVideoPollTimeout           = 2 * time.Minute
	inferenceVideoSettlementTimeout     = 30 * time.Second
	inferenceVideoContentTimeout        = 30 * time.Minute
	inferenceMaxActiveVideoJobsPerOwner = 16
	inferenceVideoSettlementExpiry      = 24 * time.Hour
	inferenceVideoSettlementCleanupAge  = 7 * 24 * time.Hour
)

type InferenceVideoCreateRequest struct {
	Prompt         string
	Model          util.Option[string]
	Seconds        util.Option[string]
	Size           util.Option[string]
	User           util.Option[string]
	InputReference *InferenceImageEditFile
}

type InferenceVideoDeleteResponse struct {
	Id      string `json:"id"`
	Object  string `json:"object"`
	Deleted bool   `json:"deleted"`
}

type inferenceVideoCreateJSONRequest struct {
	Prompt         string                    `json:"prompt"`
	Model          util.Option[string]       `json:"model,omitzero"`
	Seconds        inferenceVideoJSONSeconds `json:"seconds,omitzero"`
	Size           util.Option[string]       `json:"size,omitzero"`
	User           util.Option[string]       `json:"user,omitzero"`
	InputReference util.Option[string]       `json:"input_reference,omitzero"`
}

type inferenceVideoJSONSeconds struct {
	util.Option[string]
}

func (s *inferenceVideoJSONSeconds) UnmarshalJSON(data []byte) error {
	if string(data) == "null" {
		s.Present = false
		return nil
	}

	var text string
	if json.Unmarshal(data, &text) == nil {
		s.Value = text
		s.Present = true
		return nil
	}

	var number json.Number
	if err := json.Unmarshal(data, &number); err != nil {
		return errors.New("seconds must be a string or number")
	}
	s.Value = number.String()
	s.Present = true
	return nil
}

type inferenceVideoJob struct {
	Id           string
	Owner        string
	Username     string
	Model        string
	BackendId    string
	Status       string
	VideoTier    InferenceVideoTier
	VideoSeconds int64
	CreatedAt    time.Time
	UpdatedAt    time.Time
	LastPolledAt time.Time
	SettledAt    sql.NullTime
	BilledAt     sql.NullTime
	DeletedAt    sql.NullTime
}

func InferenceVideoCreate(ctx context.Context, owner apm.WalletOwner, username string, request InferenceVideoCreateRequest) ([]byte, *util.HttpError) {
	release, httpErr := inferenceAcquire(ctx, owner, username)
	if httpErr != nil {
		return nil, httpErr
	}
	defer release()
	return inferenceVideoCreateAcquired(ctx, owner, username, request)
}

func inferenceVideoCreateAcquired(ctx context.Context, owner apm.WalletOwner, username string, request InferenceVideoCreateRequest) ([]byte, *util.HttpError) {
	requestStartedAt := time.Now()
	auditModel := request.Model.GetOrDefault("")
	auditTier := InferenceVideoTier("")
	auditSeconds := int64(0)
	auditStatus := ""
	requestOutcome := "error"
	defer func() {
		inferenceAuditRecord(
			ctx,
			"inference.video",
			owner,
			username,
			requestStartedAt,
			inferenceAuditVideoBody(auditModel, request, auditTier, auditSeconds, auditStatus, requestOutcome),
			requestOutcome,
		)
	}()

	if inferenceIsLocked(owner) {
		requestOutcome = "payment_required"
		return nil, util.HttpErr(http.StatusPaymentRequired, "payment required")
	}

	capability := InferenceTextToVideo
	if request.InputReference != nil {
		capability = InferenceImageToVideo
	}

	modelName := strings.TrimSpace(request.Model.GetOrDefault(""))
	if modelName == "" {
		modelName = playgroundDefaultModel(owner, capability)
		if modelName == "" {
			requestOutcome = "client_error"
			return nil, util.HttpErr(http.StatusBadRequest, "model is required")
		}
		request.Model.Set(modelName)
	}

	model, httpErr := inferenceResolveModelForOwner(owner, modelName)
	if httpErr != nil {
		requestOutcome = "client_error"
		return nil, httpErr
	}
	auditModel = model.Name
	if httpErr := inferenceRequireCapability(model, capability); httpErr != nil {
		requestOutcome = "client_error"
		return nil, httpErr
	}
	if httpErr := inferenceValidateVideoCreateRequest(request); httpErr != nil {
		requestOutcome = "client_error"
		return nil, httpErr
	}
	videoTier, httpErr := inferenceVideoSelectTier(model, request)
	if httpErr != nil {
		requestOutcome = "client_error"
		return nil, httpErr
	}
	if inferenceVideoActiveJobCount(owner.Reference()) >= inferenceMaxActiveVideoJobsPerOwner {
		requestOutcome = "too_many_active"
		return nil, util.HttpErr(http.StatusTooManyRequests, "too many active video jobs for this owner")
	}
	requestedVideoSeconds := inferenceVideoRequestedSeconds(request)
	auditTier = videoTier
	auditSeconds = requestedVideoSeconds

	request.Model.Set(model.Endpoint.BackendModelName)
	body, contentType, httpErr := inferenceVideoBuildBackendBody(request)
	if httpErr != nil {
		requestOutcome = "client_error"
		return nil, httpErr
	}

	dispatchCtx, cancel := context.WithTimeout(ctx, inferenceVideoDispatchTimeout)
	defer cancel()

	respBody, httpErr := inferenceBackendJSONRequestWithLimit(
		dispatchCtx,
		model.Endpoint.BasePath,
		http.MethodPost,
		"/videos",
		body,
		contentType,
		inferenceMaxUpstreamJSONBytes,
	)
	if httpErr != nil {
		if ctx.Err() != nil {
			requestOutcome = "client_cancelled"
		} else {
			requestOutcome = "upstream_error"
		}
		return nil, httpErr
	}

	backendId, status, object, httpErr := inferenceVideoUpstreamJob(respBody)
	if httpErr != nil {
		requestOutcome = "upstream_error"
		return nil, httpErr
	}

	publicId := "video_" + util.SecureToken()
	job := inferenceVideoJob{
		Id:           publicId,
		Owner:        owner.Reference(),
		Username:     username,
		Model:        model.Name,
		BackendId:    backendId,
		Status:       inferenceVideoStatus(status),
		VideoTier:    videoTier,
		VideoSeconds: requestedVideoSeconds,
	}
	if !inferenceVideoJobInsert(job) {
		inferenceVideoBackendDeleteBestEffort(model, backendId)
		requestOutcome = "too_many_active"
		return nil, util.HttpErr(http.StatusTooManyRequests, "too many active video jobs for this owner")
	}
	auditStatus = job.Status

	respData, httpErr := inferenceVideoPublicJob(object, publicId, model.Name)
	if httpErr != nil {
		requestOutcome = "upstream_error"
		return nil, httpErr
	}

	requestOutcome = "success"
	return respData, nil
}

func inferenceVideoParseCreateRequest(w http.ResponseWriter, r *http.Request) (InferenceVideoCreateRequest, *util.HttpError) {
	var request InferenceVideoCreateRequest
	var httpErr *util.HttpError

	contentType := strings.ToLower(strings.TrimSpace(strings.Split(r.Header.Get("Content-Type"), ";")[0]))
	switch contentType {
	case "application/json":
		request, httpErr = inferenceVideoParseJSONCreateRequest(r)
	case "multipart/form-data":
		request, httpErr = inferenceVideoParseMultipartCreateRequest(w, r)
	default:
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusUnsupportedMediaType, "unsupported content type")
	}
	if httpErr != nil {
		return InferenceVideoCreateRequest{}, httpErr
	}

	inferenceVideoNormalizeCreateRequest(&request)
	return request, nil
}

func inferenceVideoNormalizeCreateRequest(request *InferenceVideoCreateRequest) {
	if request.Model.Present {
		request.Model.Value = strings.TrimSpace(request.Model.Value)
	}
	if request.Seconds.Present {
		request.Seconds.Value = strings.TrimSpace(request.Seconds.Value)
	}
	if request.Size.Present {
		request.Size.Value = strings.ToLower(strings.TrimSpace(request.Size.Value))
	}
	if request.User.Present {
		request.User.Value = strings.TrimSpace(request.User.Value)
	}
}

func inferenceVideoParseJSONCreateRequest(r *http.Request) (InferenceVideoCreateRequest, *util.HttpError) {
	if r.ContentLength > inferenceMaxJSONRequestBytes {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, inferenceMaxJSONRequestBytes+1))
	if err != nil {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}

	var raw inferenceVideoCreateJSONRequest
	if err := json.Unmarshal(body, &raw); err != nil {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if raw.InputReference.Present && strings.TrimSpace(raw.InputReference.Value) != "" {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusBadRequest, "input reference must use multipart form data")
	}

	return InferenceVideoCreateRequest{
		Prompt:  raw.Prompt,
		Model:   raw.Model,
		Seconds: raw.Seconds.Option,
		Size:    raw.Size,
		User:    raw.User,
	}, nil
}

func inferenceVideoParseMultipartCreateRequest(w http.ResponseWriter, r *http.Request) (InferenceVideoCreateRequest, *util.HttpError) {
	if r.ContentLength > inferenceMaxVideoCreateRequestBytes {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	r.Body = http.MaxBytesReader(w, r.Body, inferenceMaxVideoCreateRequestBytes)
	if err := r.ParseMultipartForm(8 << 20); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
		}
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if r.MultipartForm != nil {
		defer r.MultipartForm.RemoveAll()
	}

	request := InferenceVideoCreateRequest{
		Prompt: r.FormValue("prompt"),
	}
	if v := strings.TrimSpace(r.FormValue("model")); v != "" {
		request.Model.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("seconds")); v != "" {
		request.Seconds.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("size")); v != "" {
		request.Size.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("user")); v != "" {
		request.User.Set(v)
	}

	referenceFile, referenceHeader, referenceErr := r.FormFile("input_reference")
	if referenceErr == nil {
		defer util.SilentClose(referenceFile)
		reference, httpErr := inferenceReadImageEditFile(referenceFile, referenceHeader)
		if httpErr != nil {
			return InferenceVideoCreateRequest{}, httpErr
		}
		request.InputReference = &reference
	} else if !errors.Is(referenceErr, http.ErrMissingFile) {
		return InferenceVideoCreateRequest{}, util.HttpErr(http.StatusBadRequest, "invalid input reference")
	}

	return request, nil
}

func inferenceValidateVideoCreateRequest(request InferenceVideoCreateRequest) *util.HttpError {
	if strings.TrimSpace(request.Prompt) == "" {
		return util.HttpErr(http.StatusBadRequest, "prompt is required")
	}
	if len(request.Prompt) > inferenceMaxVideoPromptBytes {
		return util.HttpErr(http.StatusBadRequest, "video prompt is too large")
	}
	if request.Seconds.Present {
		seconds, err := strconv.Atoi(strings.TrimSpace(request.Seconds.Value))
		if err != nil || seconds <= 0 || seconds > inferenceMaxVideoSeconds {
			return util.HttpErr(http.StatusBadRequest, "invalid video duration")
		}
	}
	if request.Size.Present && !inferenceVideoSizeUsesDefault(request.Size.Value) {
		if httpErr := inferenceValidateVideoSize(request.Size.Value); httpErr != nil {
			return httpErr
		}
	}
	if request.InputReference != nil {
		if len(request.InputReference.Data) == 0 {
			return util.HttpErr(http.StatusBadRequest, "input reference is empty")
		}
	}
	return nil
}

func inferenceValidateVideoSize(raw string) *util.HttpError {
	parts := strings.Split(strings.ToLower(strings.TrimSpace(raw)), "x")
	if len(parts) != 2 {
		return util.HttpErr(http.StatusBadRequest, "invalid video size")
	}
	width, widthErr := strconv.Atoi(parts[0])
	height, heightErr := strconv.Atoi(parts[1])
	if widthErr != nil || heightErr != nil || width <= 0 || height <= 0 ||
		width > inferenceMaxVideoDimension || height > inferenceMaxVideoDimension {
		return util.HttpErr(http.StatusBadRequest, "invalid video size")
	}
	return nil
}

func inferenceVideoRequestedSeconds(request InferenceVideoCreateRequest) int64 {
	if !request.Seconds.Present {
		return 0
	}
	seconds, err := strconv.ParseInt(strings.TrimSpace(request.Seconds.Value), 10, 64)
	if err != nil || seconds <= 0 {
		return 0
	}
	return seconds
}

func inferenceVideoSizeUsesDefault(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "", "auto", "source":
		return true
	default:
		return false
	}
}

func inferenceVideoSelectTier(model InferenceModel, request InferenceVideoCreateRequest) (InferenceVideoTier, *util.HttpError) {
	if !request.Size.Present || inferenceVideoSizeUsesDefault(request.Size.Value) {
		if inferenceVideoTierValid(model.DefaultVideoTier) {
			return model.DefaultVideoTier, nil
		}
		return "", util.HttpErr(http.StatusBadRequest, "video model has no default resolution")
	}

	width, height := inferenceVideoParseSize(request.Size.Value)
	for _, resolution := range model.VideoResolutions {
		if resolution.Width == width && resolution.Height == height {
			return resolution.Tier, nil
		}
	}

	shortSide := width
	if height < shortSide {
		shortSide = height
	}
	nominalTier := inferenceVideoTierForShortSide(shortSide)
	for _, resolution := range model.VideoResolutions {
		if resolution.Tier == nominalTier {
			return nominalTier, nil
		}
	}
	tier := inferenceVideoNearestConfiguredTier(model.VideoResolutions, nominalTier)
	if inferenceVideoTierValid(tier) {
		return tier, nil
	}
	if inferenceVideoTierValid(model.DefaultVideoTier) {
		return model.DefaultVideoTier, nil
	}
	return "", util.HttpErr(http.StatusBadRequest, "video model has no default resolution")
}

func inferenceVideoTierForShortSide(shortSide int) InferenceVideoTier {
	switch {
	case shortSide <= 360:
		return InferenceVideoTier360p
	case shortSide <= 480:
		return InferenceVideoTier480p
	case shortSide <= 540:
		return InferenceVideoTier540p
	case shortSide <= 720:
		return InferenceVideoTier720p
	case shortSide <= 768:
		return InferenceVideoTier768p
	case shortSide <= 1080:
		return InferenceVideoTier1080p
	case shortSide <= 1440:
		return InferenceVideoTier2k
	default:
		return InferenceVideoTier4k
	}
}

func inferenceVideoNearestConfiguredTier(resolutions []InferenceVideoResolution, nominalTier InferenceVideoTier) InferenceVideoTier {
	nominalRank := inferenceVideoTierRank(nominalTier)
	bestAbove := InferenceVideoTier("")
	bestBelow := InferenceVideoTier("")
	for _, resolution := range resolutions {
		rank := inferenceVideoTierRank(resolution.Tier)
		if rank < 0 {
			continue
		}
		if rank >= nominalRank {
			if bestAbove == "" || rank < inferenceVideoTierRank(bestAbove) {
				bestAbove = resolution.Tier
			}
		} else if bestBelow == "" || rank > inferenceVideoTierRank(bestBelow) {
			bestBelow = resolution.Tier
		}
	}
	if bestAbove != "" {
		return bestAbove
	}
	return bestBelow
}

func inferenceVideoParseSize(raw string) (int, int) {
	parts := strings.Split(strings.ToLower(strings.TrimSpace(raw)), "x")
	if len(parts) != 2 {
		return 0, 0
	}
	width, widthErr := strconv.Atoi(parts[0])
	height, heightErr := strconv.Atoi(parts[1])
	if widthErr != nil || heightErr != nil {
		return 0, 0
	}
	return width, height
}

func inferenceVideoJobSeconds(object map[string]json.RawMessage) (int64, bool) {
	raw, ok := object["seconds"]
	if !ok {
		return 0, false
	}

	var text string
	if json.Unmarshal(raw, &text) == nil {
		text = strings.TrimSuffix(strings.TrimSpace(text), "s")
		seconds, err := strconv.ParseInt(text, 10, 64)
		if err == nil && seconds > 0 {
			return seconds, true
		}
		if secondsFloat, floatErr := strconv.ParseFloat(text, 64); floatErr == nil && secondsFloat > 0 {
			return int64(secondsFloat + 0.5), true
		}
	}

	var number float64
	if json.Unmarshal(raw, &number) == nil && number > 0 {
		return int64(number + 0.5), true
	}
	return 0, false
}

func inferenceVideoBuildBackendBody(request InferenceVideoCreateRequest) ([]byte, string, *util.HttpError) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	writeField := func(key string, value string) {
		if value != "" {
			_ = writer.WriteField(key, value)
		}
	}
	writeField("prompt", request.Prompt)
	writeField("model", request.Model.GetOrDefault(""))
	writeField("seconds", request.Seconds.GetOrDefault(""))
	writeField("size", request.Size.GetOrDefault(""))
	writeField("user", request.User.GetOrDefault(""))

	if request.InputReference != nil {
		if err := inferenceWriteImageEditPart(writer, "input_reference", *request.InputReference); err != nil {
			return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
		}
	}

	if err := writer.Close(); err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	body := buf.Bytes()
	if len(body) > inferenceMaxVideoCreateRequestBytes {
		return nil, "", util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	return body, writer.FormDataContentType(), nil
}

func inferenceVideoUpstreamJob(body []byte) (string, string, map[string]json.RawMessage, *util.HttpError) {
	var object map[string]json.RawMessage
	if err := json.Unmarshal(body, &object); err != nil {
		return "", "", nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	backendId := ""
	if raw, ok := object["id"]; ok {
		_ = json.Unmarshal(raw, &backendId)
	}
	backendId = strings.TrimSpace(backendId)
	if backendId == "" {
		return "", "", nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}

	status := ""
	if raw, ok := object["status"]; ok {
		_ = json.Unmarshal(raw, &status)
	}

	return backendId, inferenceVideoStatus(status), object, nil
}

func inferenceVideoPublicJob(object map[string]json.RawMessage, publicId string, publicModel string) ([]byte, *util.HttpError) {
	object["id"], _ = json.Marshal(publicId)
	object["model"], _ = json.Marshal(publicModel)
	if _, ok := object["object"]; !ok {
		object["object"] = json.RawMessage(`"video"`)
	}

	result, err := json.Marshal(object)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid response")
	}
	return result, nil
}

func InferenceVideoRetrieve(ctx context.Context, owner apm.WalletOwner, id string) ([]byte, *util.HttpError) {
	job, httpErr := inferenceVideoJobAuthorize(owner, id)
	if httpErr != nil {
		return nil, httpErr
	}
	if job.DeletedAt.Valid {
		return nil, util.HttpErr(http.StatusNotFound, "video not found")
	}
	if job.SettledAt.Valid && !job.BilledAt.Valid {
		return nil, util.HttpErr(http.StatusGone, "video is no longer available")
	}

	model, ok := InferenceCatalogModelByName(job.Model)
	if !ok {
		return nil, util.HttpErr(http.StatusNotFound, "video not found")
	}

	respBody, httpErr := inferenceBackendJSONRequestWithLimit(
		ctx,
		model.Endpoint.BasePath,
		http.MethodGet,
		inferenceVideoBackendPath(job.BackendId, ""),
		nil,
		"",
		inferenceMaxUpstreamJSONBytes,
	)
	if httpErr != nil {
		return nil, httpErr
	}

	_, status, object, httpErr := inferenceVideoUpstreamJob(respBody)
	if httpErr != nil {
		return nil, httpErr
	}
	if status != job.Status {
		inferenceVideoJobUpdateStatus(job.Id, status)
	}

	return inferenceVideoPublicJob(object, job.Id, model.Name)
}

func inferenceVideoContentRespond(ctx context.Context, w http.ResponseWriter, r *http.Request, owner apm.WalletOwner, id string, variant string) *util.HttpError {
	if httpErr := inferenceValidateVideoVariant(variant); httpErr != nil {
		return httpErr
	}

	job, httpErr := inferenceVideoJobAuthorize(owner, id)
	if httpErr != nil {
		return httpErr
	}
	if job.DeletedAt.Valid {
		return util.HttpErr(http.StatusNotFound, "video not found")
	}
	if job.SettledAt.Valid && !job.BilledAt.Valid {
		return util.HttpErr(http.StatusGone, "video is no longer available")
	}

	model, ok := InferenceCatalogModelByName(job.Model)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "video not found")
	}

	path := inferenceVideoBackendPath(job.BackendId, "content")
	if variant != "" {
		path += "?variant=" + url.QueryEscape(variant)
	}

	headers := http.Header{}
	if rangeHeader := r.Header.Get("Range"); rangeHeader != "" {
		headers.Set("Range", rangeHeader)
	}
	if ifRange := r.Header.Get("If-Range"); ifRange != "" {
		headers.Set("If-Range", ifRange)
	}

	resp, httpErr := inferenceBackendRequestWithHeaders(
		ctx,
		model.Endpoint.BasePath,
		http.MethodGet,
		path,
		nil,
		"",
		headers,
	)
	if httpErr != nil {
		return httpErr
	}
	defer util.SilentClose(resp.Body)

	contentType := resp.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "video/mp4"
	}
	w.Header().Set("Content-Type", contentType)
	if resp.ContentLength >= 0 {
		w.Header().Set("Content-Length", strconv.FormatInt(resp.ContentLength, 10))
	}
	if disposition := resp.Header.Get("Content-Disposition"); disposition != "" {
		w.Header().Set("Content-Disposition", disposition)
	}
	if cacheControl := resp.Header.Get("Cache-Control"); cacheControl != "" {
		w.Header().Set("Cache-Control", cacheControl)
	}
	if contentRange := resp.Header.Get("Content-Range"); contentRange != "" {
		w.Header().Set("Content-Range", contentRange)
	}
	if acceptRanges := resp.Header.Get("Accept-Ranges"); acceptRanges != "" {
		w.Header().Set("Accept-Ranges", acceptRanges)
	}
	if resp.StatusCode == http.StatusPartialContent {
		w.WriteHeader(http.StatusPartialContent)
	} else {
		w.WriteHeader(http.StatusOK)
	}

	_, copyErr := io.Copy(w, resp.Body)
	if copyErr != nil && ctx.Err() == nil {
		log.Warn("Inference video content stream failed: video=%s err=%v", job.Id, copyErr)
	}
	return nil
}

func InferenceVideoDelete(ctx context.Context, owner apm.WalletOwner, id string) (InferenceVideoDeleteResponse, *util.HttpError) {
	job, httpErr := inferenceVideoJobAuthorize(owner, id)
	if httpErr != nil {
		return InferenceVideoDeleteResponse{}, httpErr
	}

	response := InferenceVideoDeleteResponse{
		Id:      job.Id,
		Object:  "video.deleted",
		Deleted: true,
	}

	model, modelFound := InferenceCatalogModelByName(job.Model)

	if job.DeletedAt.Valid {
		if job.SettledAt.Valid {
			if modelFound {
				inferenceVideoBackendDeleteBestEffort(model, job.BackendId)
			}
			inferenceVideoJobDelete(job.Id)
		}
		return response, nil
	}

	if job.SettledAt.Valid {
		if modelFound {
			resp, deleteErr := inferenceBackendRequest(
				ctx,
				model.Endpoint.BasePath,
				http.MethodDelete,
				inferenceVideoBackendPath(job.BackendId, ""),
				nil,
				"",
			)
			if deleteErr != nil && deleteErr.StatusCode != http.StatusNotFound && deleteErr.StatusCode != http.StatusGone {
				return InferenceVideoDeleteResponse{}, deleteErr
			}
			if resp != nil {
				util.SilentClose(resp.Body)
			}
		}
		inferenceVideoJobDelete(job.Id)
		return response, nil
	}

	inferenceVideoJobMarkDeleted(job.Id)
	job.DeletedAt = sql.NullTime{Time: time.Now(), Valid: true}
	if modelFound {
		inferenceVideoSettlementJob(job)
	} else {
		inferenceVideoSettleJob(job, InferenceModel{}, "failed", 0, false)
	}

	updatedJob, found := inferenceVideoJobLoad(job.Id)
	if found && updatedJob.SettledAt.Valid {
		if modelFound {
			inferenceVideoBackendDeleteBestEffort(model, job.BackendId)
		}
		inferenceVideoJobDelete(job.Id)
	}

	return response, nil
}

func inferenceVideoParsePath(r *http.Request) (string, string, *util.HttpError) {
	path := strings.TrimPrefix(r.URL.Path, "/v1/videos/")
	path = strings.Trim(path, "/")
	if path == "" {
		return "", "", util.HttpErr(http.StatusNotFound, "video not found")
	}

	parts := strings.Split(path, "/")
	switch len(parts) {
	case 1:
		return parts[0], "", nil
	case 2:
		if parts[1] == "content" {
			return parts[0], "content", nil
		}
	}

	return "", "", util.HttpErr(http.StatusNotFound, "video not found")
}

func inferenceValidateVideoVariant(variant string) *util.HttpError {
	switch variant {
	case "", "video", "thumbnail", "spritesheet":
		return nil
	default:
		return util.HttpErr(http.StatusBadRequest, "invalid video variant")
	}
}

func inferenceVideoBackendPath(backendId string, action string) string {
	path := "/videos/" + url.PathEscape(backendId)
	if action != "" {
		path += "/" + action
	}
	return path
}

func inferenceVideoJobAuthorize(owner apm.WalletOwner, id string) (inferenceVideoJob, *util.HttpError) {
	job, ok := inferenceVideoJobLoad(id)
	if !ok || job.Owner != owner.Reference() {
		return inferenceVideoJob{}, util.HttpErr(http.StatusNotFound, "video not found")
	}
	return job, nil
}

func inferenceVideoStatus(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return "queued"
	}
	return util.Utf8Truncate(value, 64)
}

func inferenceVideoActiveJobCount(owner string) int64 {
	return db.NewTx(func(tx *db.Transaction) int64 {
		row, _ := db.Get[struct {
			Count int64
		}](
			tx,
			`
				select count(*) as count
				from inference_video_job
				where owner = :owner and settled_at is null
			`,
			db.Params{
				"owner": owner,
			},
		)
		return row.Count
	})
}

func inferenceVideoJobInsert(job inferenceVideoJob) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		db.Exec(
			tx,
			`
				select pg_advisory_xact_lock(hashtextextended(cast(:owner as text), 0))
			`,
			db.Params{
				"owner": job.Owner,
			},
		)
		row, _ := db.Get[struct {
			Count int64
		}](
			tx,
			`
				select count(*) as count
				from inference_video_job
				where owner = :owner and settled_at is null
			`,
			db.Params{
				"owner": job.Owner,
			},
		)
		if row.Count >= inferenceMaxActiveVideoJobsPerOwner {
			return false
		}

		db.Exec(
			tx,
			`
				insert into inference_video_job(
					id,
					owner,
					username,
					model,
					backend_id,
					status,
					video_tier,
					video_seconds
				)
				values (
					:id,
					:owner,
					:username,
					:model,
					:backend_id,
					:status,
					:video_tier,
					:video_seconds
				)
			`,
			db.Params{
				"id":            job.Id,
				"owner":         job.Owner,
				"username":      job.Username,
				"model":         job.Model,
				"backend_id":    job.BackendId,
				"status":        job.Status,
				"video_tier":    string(job.VideoTier),
				"video_seconds": job.VideoSeconds,
			},
		)
		return true
	})
}

func inferenceVideoBackendDeleteBestEffort(model InferenceModel, backendId string) {
	ctx, cancel := context.WithTimeout(context.Background(), inferenceVideoSettlementTimeout)
	defer cancel()
	resp, httpErr := inferenceBackendRequest(
		ctx,
		model.Endpoint.BasePath,
		http.MethodDelete,
		inferenceVideoBackendPath(backendId, ""),
		nil,
		"",
	)
	if httpErr != nil {
		return
	}
	if resp != nil {
		util.SilentClose(resp.Body)
	}
}

func inferenceVideoJobLoad(id string) (inferenceVideoJob, bool) {
	return db.NewTx2(func(tx *db.Transaction) (inferenceVideoJob, bool) {
		row, ok := db.Get[struct {
			Id           string
			Owner        string
			Username     string
			Model        string
			BackendId    string
			Status       string
			VideoTier    string
			VideoSeconds int64
			CreatedAt    time.Time
			UpdatedAt    time.Time
			LastPolledAt time.Time
			SettledAt    sql.NullTime
			BilledAt     sql.NullTime
			DeletedAt    sql.NullTime
		}](
			tx,
			`
				select
					id,
					owner,
					username,
					model,
					backend_id,
					status,
					video_tier,
					video_seconds,
					created_at,
					updated_at,
					last_polled_at,
					settled_at,
					billed_at,
					deleted_at
				from
					inference_video_job
				where
					id = :id
			`,
			db.Params{
				"id": id,
			},
		)
		if !ok {
			return inferenceVideoJob{}, false
		}
		return inferenceVideoJob{
			Id:           row.Id,
			Owner:        row.Owner,
			Username:     row.Username,
			Model:        row.Model,
			BackendId:    row.BackendId,
			Status:       row.Status,
			VideoTier:    InferenceVideoTier(row.VideoTier),
			VideoSeconds: row.VideoSeconds,
			CreatedAt:    row.CreatedAt,
			UpdatedAt:    row.UpdatedAt,
			LastPolledAt: row.LastPolledAt,
			SettledAt:    row.SettledAt,
			BilledAt:     row.BilledAt,
			DeletedAt:    row.DeletedAt,
		}, true
	})
}

func inferenceVideoJobUpdateStatus(id string, status string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update
					inference_video_job
				set
					status = :status,
					updated_at = now()
				where
					id = :id and settled_at is null
			`,
			db.Params{
				"id":     id,
				"status": status,
			},
		)
	})
}

func inferenceVideoJobMarkPolled(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update inference_video_job
				set last_polled_at = now()
				where id = :id and settled_at is null
			`,
			db.Params{
				"id": id,
			},
		)
	})
}

func inferenceVideoJobMarkDeleted(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update inference_video_job
				set deleted_at = now(), updated_at = now()
				where id = :id and settled_at is null
			`,
			db.Params{
				"id": id,
			},
		)
	})
}

func inferenceVideoJobDelete(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from inference_video_job
				where id = :id
			`,
			db.Params{
				"id": id,
			},
		)
	})
}

func inferenceVideoSettlementLoop() {
	inferenceVideoSettlementTick()
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		inferenceVideoSettlementTick()
	}
}

func inferenceVideoSettlementTick() {
	jobs := inferenceVideoPendingJobs(16)
	for _, job := range jobs {
		inferenceVideoSettlementJob(job)
	}
}

func inferenceVideoSettlementJob(job inferenceVideoJob) {
	model, found := InferenceCatalogModelByName(job.Model)
	if !found {
		inferenceVideoSettleJob(job, InferenceModel{}, "failed", 0, false)
		return
	}

	inferenceVideoJobMarkPolled(job.Id)

	ctx, cancel := context.WithTimeout(context.Background(), inferenceVideoSettlementTimeout)
	defer cancel()

	respBody, httpErr := inferenceBackendJSONRequestWithLimit(
		ctx,
		model.Endpoint.BasePath,
		http.MethodGet,
		inferenceVideoBackendPath(job.BackendId, ""),
		nil,
		"",
		inferenceMaxUpstreamJSONBytes,
	)
	if httpErr != nil {
		if inferenceVideoStatusCompleted(job.Status) {
			inferenceVideoSettleJob(job, model, job.Status, inferenceVideoBilledSeconds(job, nil), true)
			return
		}
		if time.Since(job.CreatedAt) > inferenceVideoSettlementExpiry {
			inferenceVideoSettleJob(job, model, "failed", 0, false)
		}
		return
	}

	_, status, object, httpErr := inferenceVideoUpstreamJob(respBody)
	if httpErr != nil {
		if inferenceVideoStatusCompleted(job.Status) {
			inferenceVideoSettleJob(job, model, job.Status, inferenceVideoBilledSeconds(job, nil), true)
			return
		}
		if time.Since(job.CreatedAt) > inferenceVideoSettlementExpiry {
			inferenceVideoSettleJob(job, model, "failed", 0, false)
		}
		return
	}
	status = inferenceVideoStatus(status)
	if status != job.Status {
		inferenceVideoJobUpdateStatus(job.Id, status)
	}

	inferenceVideoSettleKnownStatus(job, model, status, object)
	if !inferenceVideoStatusCompleted(status) && !inferenceVideoStatusFailed(status) &&
		time.Since(job.CreatedAt) > inferenceVideoSettlementExpiry {
		inferenceVideoSettleJob(job, model, "expired", 0, false)
	}
}

func inferenceVideoStatusCompleted(status string) bool {
	switch status {
	case "completed", "succeeded", "success":
		return true
	default:
		return false
	}
}

func inferenceVideoStatusFailed(status string) bool {
	switch status {
	case "failed", "error", "cancelled", "canceled":
		return true
	default:
		return false
	}
}

func inferenceVideoSettleKnownStatus(job inferenceVideoJob, model InferenceModel, status string, object map[string]json.RawMessage) {
	switch {
	case inferenceVideoStatusCompleted(status):
		inferenceVideoSettleJob(job, model, status, inferenceVideoBilledSeconds(job, object), true)
	case inferenceVideoStatusFailed(status):
		inferenceVideoSettleJob(job, model, status, 0, false)
	}
}

func inferenceVideoBilledSeconds(job inferenceVideoJob, object map[string]json.RawMessage) int64 {
	seconds, ok := inferenceVideoJobSeconds(object)
	if !ok || seconds <= 0 {
		seconds = job.VideoSeconds
	}
	if seconds <= 0 {
		seconds = 4
	}
	if seconds > inferenceMaxVideoSeconds {
		seconds = inferenceMaxVideoSeconds
	}
	return seconds
}

func inferenceVideoSettleJob(job inferenceVideoJob, model InferenceModel, status string, seconds int64, bill bool) {
	owner := apm.WalletOwnerFromReference(job.Owner)
	billed := false
	billedTier := job.VideoTier
	if _, ok := model.PricePerVideoSecond[billedTier]; !ok {
		if _, defaultOk := model.PricePerVideoSecond[model.DefaultVideoTier]; defaultOk {
			billedTier = model.DefaultVideoTier
		}
	}
	if bill {
		if _, ok := model.PricePerVideoSecond[billedTier]; !ok {
			log.Warn("Video job has no configured price: job=%s model=%s tier=%s", job.Id, model.Name, billedTier)
		}
	}

	db.NewTx0(func(tx *db.Transaction) {
		billed = false
		row, ok := db.Get[struct {
			SettledAt sql.NullTime
		}](
			tx,
			`
				select settled_at
				from inference_video_job
				where id = :id
				for update
			`,
			db.Params{
				"id": job.Id,
			},
		)
		if !ok || row.SettledAt.Valid {
			return
		}

		if bill {
			inferenceReportBillingTx(tx, owner, job.Username, model, InferenceBillingUsage{
				VideoSeconds: seconds,
				VideoTier:    billedTier,
			})
			db.Exec(
				tx,
				`
					update inference_video_job
					set
						status = :status,
						video_seconds = :video_seconds,
						billed_at = now(),
						settled_at = now(),
						updated_at = now()
					where id = :id
				`,
				db.Params{
					"id":            job.Id,
					"status":        status,
					"video_seconds": seconds,
				},
			)
			billed = true
		} else {
			db.Exec(
				tx,
				`
					update inference_video_job
					set
						status = :status,
						settled_at = now(),
						updated_at = now()
					where id = :id
				`,
				db.Params{
					"id":     job.Id,
					"status": status,
				},
			)
		}
	})

	if billed {
		inferenceRecordBillingMetrics(model, InferenceBillingUsage{
			VideoSeconds: seconds,
			VideoTier:    billedTier,
		})
		inferenceWakeUsageFlush()
		inferenceAuditRecord(
			context.Background(),
			"inference.video.settle",
			owner,
			job.Username,
			time.Now(),
			mustMarshal(map[string]any{
				"videoId":        job.Id,
				"model":          model.Name,
				"tier":           string(billedTier),
				"seconds":        seconds,
				"pricePerSecond": model.PricePerVideoSecond[billedTier],
			}),
			"success",
		)
	}
}

func inferenceVideoPendingJobs(limit int) []inferenceVideoJob {
	return db.NewTx(func(tx *db.Transaction) []inferenceVideoJob {
		rows := db.Select[struct {
			Id           string
			Owner        string
			Username     string
			Model        string
			BackendId    string
			Status       string
			VideoTier    string
			VideoSeconds int64
			CreatedAt    time.Time
			UpdatedAt    time.Time
			LastPolledAt time.Time
			SettledAt    sql.NullTime
			BilledAt     sql.NullTime
			DeletedAt    sql.NullTime
		}](
			tx,
			`
				select
					id,
					owner,
					username,
					model,
					backend_id,
					status,
					video_tier,
					video_seconds,
					created_at,
					updated_at,
					last_polled_at,
					settled_at,
					billed_at,
					deleted_at
				from
					inference_video_job
				where
					settled_at is null
				order by
					last_polled_at,
					created_at
				limit :limit
			`,
			db.Params{
				"limit": limit,
			},
		)
		jobs := make([]inferenceVideoJob, 0, len(rows))
		for _, row := range rows {
			jobs = append(jobs, inferenceVideoJob{
				Id:           row.Id,
				Owner:        row.Owner,
				Username:     row.Username,
				Model:        row.Model,
				BackendId:    row.BackendId,
				Status:       row.Status,
				VideoTier:    InferenceVideoTier(row.VideoTier),
				VideoSeconds: row.VideoSeconds,
				CreatedAt:    row.CreatedAt,
				UpdatedAt:    row.UpdatedAt,
				LastPolledAt: row.LastPolledAt,
				SettledAt:    row.SettledAt,
				BilledAt:     row.BilledAt,
				DeletedAt:    row.DeletedAt,
			})
		}
		return jobs
	})
}

func inferenceVideoCleanupLoop() {
	inferenceVideoCleanup()
	ticker := time.NewTicker(time.Hour)
	defer ticker.Stop()
	for range ticker.C {
		inferenceVideoCleanup()
	}
}

func inferenceVideoCleanup() {
	cutoff := time.Now().Add(-inferenceVideoSettlementCleanupAge)
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from inference_video_job
				where settled_at is not null and settled_at < :cutoff
			`,
			db.Params{
				"cutoff": cutoff,
			},
		)
	})
}

func inferenceAuditVideoBody(
	model string,
	request InferenceVideoCreateRequest,
	tier InferenceVideoTier,
	seconds int64,
	status string,
	reason string,
) json.RawMessage {
	type auditVideoBody struct {
		Model             string             `json:"model"`
		Tier              InferenceVideoTier `json:"tier,omitempty"`
		Status            string             `json:"status,omitempty"`
		Reason            string             `json:"reason,omitempty"`
		Seconds           int64              `json:"seconds,omitempty"`
		Size              string             `json:"size,omitempty"`
		HasInputReference bool               `json:"hasInputReference"`
	}

	return mustMarshal(auditVideoBody{
		Model:             model,
		Tier:              tier,
		Status:            status,
		Reason:            reason,
		Seconds:           seconds,
		Size:              request.Size.GetOrDefault(""),
		HasInputReference: request.InputReference != nil,
	})
}
