package k8s

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"runtime"
	"strings"
	"sync/atomic"
	"time"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var inferenceGlobals struct {
	Ready               atomic.Bool
	BackendServer       string
	MockImageGeneration bool
	Product             apm.ProductV2
}

const (
	inferenceDevelopmentProviderLocalAI = "localai"

	// Fallback accounting when image-generation usage is missing from backend responses.
	// Tokens are billed proportionally to generated megapixels (1 megapixel = 1,000,000 pixels).
	inferenceImageGenerationTokensPerMegaPixel = 1000.0
)

type inferenceCompletion struct {
	Id      string                      `json:"id"`
	Object  string                      `json:"object"`
	Created int64                       `json:"created"`
	Choices []inferenceChoice           `json:"choices"`
	Usage   util.Option[inferenceUsage] `json:"usage"`
}

type inferenceChoice struct {
	Index   int `json:"index"`
	Message struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	} `json:"message"`
	FinishReason string `json:"finish_reason"`
}

type inferenceUsage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
}

func initInference() {
	inferenceCfg := &shared.ServiceConfig.Compute.Inference
	if !inferenceCfg.Enabled {
		return
	}

	inferenceGlobals.BackendServer = strings.TrimRight(inferenceCfg.BackendServer, "/")
	if inferenceGlobals.BackendServer == "" {
		panic("inference backend server is not configured")
	}

	inferenceGlobals.MockImageGeneration = util.DevelopmentModeEnabled() && runtime.GOARCH == "arm64"
	if inferenceGlobals.MockImageGeneration {
		log.Info("Enabling mock image generation endpoint for development on arm64")
	}

	if util.DevelopmentModeEnabled() && inferenceCfg.DevelopmentProvider == inferenceDevelopmentProviderLocalAI {
		err := inferenceAutoConfigureLocalAI()
		if err != nil {
			panic(fmt.Sprintf("could not initialize localai: %s", err))
		}
	}

	inferenceGlobals.Ready.Store(true)

	inferenceGlobals.Product = apm.ProductV2{
		Type: apm.ProductTypeCLicense, // TODO faking it a bit for now
		Category: apm.ProductCategory{
			Name:        "inference",
			Provider:    cfg.Provider.Id,
			ProductType: apm.ProductTypeLicense,
			AccountingUnit: apm.AccountingUnit{
				Name:       "Token",
				NamePlural: "Tokens",
			},
			AccountingFrequency: apm.AccountingFrequencyOnce,
			FreeToUse:           false,
			AllowSubAllocations: true,
		},
		Name:                      "inference",
		Description:               "Inference tokens",
		ProductType:               apm.ProductTypeLicense,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	controller.ProductsRegister([]apm.ProductV2{inferenceGlobals.Product})

	authority := fmt.Sprintf("chat%s", shared.ServiceConfig.Compute.Web.Suffix) // TODO Change for prod
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: authority,
			Type:         gateway.RouteTypeIngress,
		},
	})

	controller.Mux.HandleFunc(authority+"/v1/models", func(w http.ResponseWriter, r *http.Request) {
		_, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, authority)
	})

	controller.Mux.HandleFunc(authority+"/v1/models/", func(w http.ResponseWriter, r *http.Request) {
		_, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, authority)
	})

	controller.Mux.HandleFunc(authority+"/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		body, err := io.ReadAll(r.Body) // TODO limit
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		var parsedRequest map[string]json.RawMessage
		err = json.Unmarshal(body, &parsedRequest)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		stream := false
		streamOpt, ok := parsedRequest["stream"]
		if ok {
			_ = json.Unmarshal(streamOpt, &stream)

			if stream {
				fixedOpts, _ := json.Marshal(map[string]any{
					"include_usage": true,
				})
				parsedRequest["stream_options"] = fixedOpts
			}
		}

		fixedRequest, _ := json.Marshal(parsedRequest)
		proxyReq, err := http.NewRequest(
			http.MethodPost,
			fmt.Sprintf("%s/chat/completions", inferenceGlobals.BackendServer),
			bytes.NewBuffer(fixedRequest),
		)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		proxyReq.Header.Add("Authorization", "Bearer notused")
		proxyReq.Header.Add("Content-Type", "application/json")

		resp, err := http.DefaultClient.Do(proxyReq)
		if err != nil {
			// TODO
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}
		defer util.SilentClose(resp.Body)

		for k, values := range resp.Header {
			for _, v := range values {
				w.Header().Add(k, v)
			}
		}
		w.WriteHeader(resp.StatusCode)

		if stream {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			reader := bufio.NewReader(resp.Body)
			var buf bytes.Buffer

			flushBuffer := func() {
				if buf.Len() > 0 {
					respData := buf.Bytes()
					_, writeErr := w.Write(respData)
					if writeErr == nil {
						// Client is likely dead, but keep going to get usage numbers
						flusher.Flush()
					}

					respString, _ := strings.CutPrefix(string(respData), "data: ")
					var completion inferenceCompletion
					_ = json.Unmarshal([]byte(respString), &completion)
					if usage := completion.Usage; usage.Present && len(completion.Choices) == 0 {
						inferenceReportUsage(apiKeyOwner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
					}

					buf.Reset()
				}
			}

			for {
				line, readErr := reader.ReadBytes('\n')
				if len(line) > 0 {
					buf.Write(line)

					// SSE events are separated by a blank line: "\n\n".
					if bytes.HasSuffix(buf.Bytes(), []byte("\n\n")) {
						flushBuffer()
					}
				}

				if readErr != nil {
					if readErr == io.EOF {
						flushBuffer()
					}
					break
				}
			}
		} else {
			respData, err := io.ReadAll(resp.Body)
			if err == nil {
				_, _ = w.Write(respData)

				var completion inferenceCompletion
				_ = json.Unmarshal(respData, &completion)
				if usage := completion.Usage; usage.Present {
					inferenceReportUsage(apiKeyOwner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
				}
			}
		}
	})

	controller.Mux.HandleFunc(authority+"/v1/audio/transcriptions", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		requestBody, err := io.ReadAll(r.Body)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		respData, httpErr := inferenceTranscriptionResponse(requestBody, r.Header.Get("Content-Type"))
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)

		promptTokens, completionTokens := inferenceUsageFromTranscriptionResponse(respData)
		inferenceReportUsage(apiKeyOwner, promptTokens, completionTokens)
	})

	controller.Mux.HandleFunc(authority+"/v1/images/generations", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		requestBody, err := io.ReadAll(r.Body)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		if inferenceGlobals.MockImageGeneration {
			respData, httpErr := inferenceGenerateMockImageResponse(requestBody)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}

			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)

			promptTokens, completionTokens := inferenceUsageFromImageGenerationResponse(requestBody, respData)
			inferenceReportUsage(apiKeyOwner, promptTokens, completionTokens)
			return
		}

		proxyReq, err := http.NewRequest(
			http.MethodPost,
			fmt.Sprintf("%s/images/generations", inferenceGlobals.BackendServer),
			bytes.NewBuffer(requestBody),
		)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		proxyReq.Header.Add("Authorization", "Bearer notused")
		proxyReq.Header.Add("Content-Type", "application/json")

		resp, err := http.DefaultClient.Do(proxyReq)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}
		defer util.SilentClose(resp.Body)

		for k, values := range resp.Header {
			for _, v := range values {
				w.Header().Add(k, v)
			}
		}
		w.WriteHeader(resp.StatusCode)

		respData, err := io.ReadAll(resp.Body)
		if err == nil {
			_, _ = w.Write(respData)

			if resp.StatusCode >= 200 && resp.StatusCode < 300 {
				promptTokens, completionTokens := inferenceUsageFromImageGenerationResponse(requestBody, respData)
				inferenceReportUsage(apiKeyOwner, promptTokens, completionTokens)
			}
		}
	})
}

func inferenceAuthenticateRequest(r *http.Request) (apm.WalletOwner, *util.HttpError) {
	authHeader := r.Header.Get("Authorization")
	apiKey, _ := strings.CutPrefix(authHeader, "Bearer ")
	return inferenceApiKeyValidate(apiKey)
}

func inferenceProxyModelsRequest(w http.ResponseWriter, r *http.Request, authority string) {
	path := strings.TrimPrefix(r.URL.Path, authority+"/v1")
	if path == "" {
		path = "/models"
	}

	backendUrl := fmt.Sprintf("%s%s", inferenceGlobals.BackendServer, path)
	backendUrl = strings.ReplaceAll(backendUrl, "/v1/v1", "/v1")
	if r.URL.RawQuery != "" {
		backendUrl += "?" + r.URL.RawQuery
	}

	proxyReq, err := http.NewRequest(r.Method, backendUrl, nil)
	if err != nil {
		http.Error(w, "invalid request", http.StatusBadRequest)
		return
	}

	proxyReq.Header.Add("Authorization", "Bearer notused")

	resp, err := http.DefaultClient.Do(proxyReq)
	if err != nil {
		http.Error(w, "invalid request", http.StatusBadRequest)
		return
	}
	defer util.SilentClose(resp.Body)

	for k, values := range resp.Header {
		for _, v := range values {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(resp.StatusCode)

	respData, err := io.ReadAll(resp.Body)
	if err == nil {
		_, _ = w.Write(respData)
	}
}

// Development mode initializer code (LocalAI)
// =====================================================================================================================

type inferenceLocalAIApplyRequest struct {
	Id   string `json:"id,omitempty"`
	Name string `json:"name,omitempty"`
}

func inferenceAutoConfigureLocalAI() error {
	base := strings.TrimRight(inferenceGlobals.BackendServer, "/")
	managementBase := strings.TrimSuffix(base, "/v1")

	err := inferenceWaitForModelEndpoint(fmt.Sprintf("%s/models", base))
	if err != nil {
		return err
	}

	inferenceApplyLocalAIFallbackModels(managementBase, "chat", []string{"localai@qwen3-0.6b"})
	inferenceApplyLocalAIFallbackModels(managementBase, "transcription", []string{"localai@whisper-1"})
	inferenceApplyLocalAIFallbackModels(managementBase, "image-generation", []string{"localai@sd-1.5-ggml"})

	return nil
}

func inferenceApplyLocalAIFallbackModels(base string, capability string, candidates []string) {
	for _, modelId := range candidates {
		if err := inferenceLocalAIApplyModel(base, modelId); err != nil {
			log.Warn("Could not auto-apply LocalAI model %s for %s: %v", modelId, capability, err)
			continue
		}

		log.Info("Auto-applied LocalAI model %s for %s", modelId, capability)
		return
	}

	log.Warn("No LocalAI models could be auto-applied for %s", capability)
}

func inferenceWaitForModelEndpoint(endpoint string) error {
	for i := 0; i < 60; i++ {
		resp, err := http.Get(endpoint)
		if err == nil {
			_ = resp.Body.Close()
			if resp.StatusCode >= 200 && resp.StatusCode < 500 {
				return nil
			}
		}

		time.Sleep(1 * time.Second)
	}

	return fmt.Errorf("timed out waiting for inference backend model endpoint")
}

func inferenceLocalAIApplyModel(base string, modelId string) error {
	requestVariants := []inferenceLocalAIApplyRequest{
		{Id: modelId},
		{Name: modelId},
	}

	for _, request := range requestVariants {
		payload, _ := json.Marshal(request)
		resp, err := http.Post(
			fmt.Sprintf("%s/models/apply", strings.TrimRight(base, "/")),
			"application/json",
			bytes.NewBuffer(payload),
		)
		if err != nil {
			continue
		}

		_, _ = io.Copy(io.Discard, resp.Body)
		_ = resp.Body.Close()
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return nil
		}
	}

	return fmt.Errorf("model apply endpoint did not accept request")
}

// Inference usage helpers
// =====================================================================================================================
// inferenceUsageFrom* centralizes our current accounting policy for multimodal inference.
//
// The backend is treated as a black-box. If it returns OpenAI-style `usage`, those values are used directly.
// If usage is absent (common for CPU-only mock setups), we fall back to deterministic estimates:
// - audio transcription: transcript-length based estimate (characters/4)
// - image generation: tokens proportional to generated megapixels
//
// This keeps pricing predictable and auditable during development, while allowing us to switch backends through
// configuration without changing the external API surface.

func inferenceUsageFromTranscriptionResponse(responseBody []byte) (promptTokens int, completionTokens int) {
	var payload struct {
		Text  string                      `json:"text"`
		Usage util.Option[inferenceUsage] `json:"usage"`
	}

	_ = json.Unmarshal(responseBody, &payload)
	if payload.Usage.Present {
		return payload.Usage.Value.PromptTokens, payload.Usage.Value.CompletionTokens
	}

	return 0, inferenceEstimateTokensFromText(payload.Text)
}

func inferenceUsageFromImageGenerationResponse(requestBody []byte, responseBody []byte) (promptTokens int, completionTokens int) {
	var payload struct {
		Data  []json.RawMessage           `json:"data"`
		Usage util.Option[inferenceUsage] `json:"usage"`
	}

	_ = json.Unmarshal(responseBody, &payload)
	if payload.Usage.Present {
		return payload.Usage.Value.PromptTokens, payload.Usage.Value.CompletionTokens
	}

	imageCount := len(payload.Data)
	if imageCount == 0 {
		imageCount = inferenceImageRequestCount(requestBody)
	}
	width, height := inferenceImageRequestSize(requestBody)

	megaPixels := float64(width*height) / 1_000_000.0
	completionTokens = int(math.Round(float64(imageCount) * megaPixels * inferenceImageGenerationTokensPerMegaPixel))
	if completionTokens < 1 && imageCount > 0 {
		completionTokens = 1
	}

	return 0, completionTokens
}

func inferenceImageRequestCount(requestBody []byte) int {
	var payload struct {
		N int `json:"n"`
	}

	_ = json.Unmarshal(requestBody, &payload)
	if payload.N <= 0 {
		return 1
	}

	return payload.N
}

func inferenceImageRequestSize(requestBody []byte) (int, int) {
	var payload struct {
		Size string `json:"size"`
	}

	_ = json.Unmarshal(requestBody, &payload)
	return inferenceParseImageSize(payload.Size)
}

func inferenceEstimateTokensFromText(text string) int {
	if text == "" {
		return 0
	}

	return (len([]rune(text)) + 3) / 4
}

type inferenceImageGenerationRequest struct {
	Model          string `json:"model"`
	Prompt         string `json:"prompt"`
	N              int    `json:"n"`
	Size           string `json:"size"`
	ResponseFormat string `json:"response_format"`
}

type inferenceImageGenerationResponse struct {
	Created int64                                `json:"created"`
	Data    []inferenceImageGenerationResponseEl `json:"data"`
}

type inferenceImageGenerationResponseEl struct {
	URL     string `json:"url,omitempty"`
	B64JSON string `json:"b64_json,omitempty"`
}

func inferenceParseImageSize(raw string) (int, int) {
	if raw == "" {
		return 512, 512
	}

	w := 512
	h := 512
	_, _ = fmt.Sscanf(raw, "%dx%d", &w, &h)

	if w < 128 {
		w = 128
	}
	if h < 128 {
		h = 128
	}
	if w > 1024 {
		w = 1024
	}
	if h > 1024 {
		h = 1024
	}

	return w, h
}

func inferenceReportUsage(owner apm.WalletOwner, promptTokens int, completionTokens int) {
	log.Info("Prompt: %v | Completion: %v", promptTokens, completionTokens)

	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         owner,
				IsDeltaCharge: true,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     inferenceGlobals.Product.Category.Name,
					Provider: inferenceGlobals.Product.Category.Provider,
				},
				Usage:       int64(completionTokens),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

// API tokens
// =====================================================================================================================

var inferenceApiKeysCache = util.NewCache[string, string](1 * time.Hour)
var inferenceTokenIdToKey = util.NewCache[string, string](1 * time.Hour)

func inferenceApiKeyValidate(key string) (apm.WalletOwner, *util.HttpError) {
	tokenId, secret, ok := inferenceParseToken(key)
	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	ownerRef, ok := inferenceApiKeysCache.Get(key, func() (string, error) {
		type rowType struct {
			Owner     string
			TokenHash []byte
			TokenSalt []byte
		}
		row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
			return db.Get[rowType](
				tx,
				`
					select owner, token_hash, token_salt
					from inference_api_keys
					where token_id = :token_id and now() <= expires_at
				`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		if !ok || !util.CheckPassword(row.TokenHash, row.TokenSalt, secret) {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update inference_api_keys set last_used_at = now() where token_id = :token_id`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		if !ok {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		} else {
			inferenceTokenIdToKey.Set(tokenId, key)
			return row.Owner, nil
		}
	})

	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	inferenceTokenIdToKey.Set(tokenId, key)

	owner := apm.WalletOwnerFromReference(ownerRef)
	if controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name) {
		return apm.WalletOwner{}, util.HttpErr(http.StatusPaymentRequired, "no more resources available")
	} else {
		return owner, nil
	}
}

func inferenceInitApiTokens() controller.ApiTokenService {
	return controller.ApiTokenService{
		Create:          inferenceCreateApiToken,
		Revoke:          inferenceRevokeApiToken,
		RetrieveOptions: inferenceRetrieveApiTokenOptions,
	}
}

func inferenceCreateApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info

	if !inferenceGlobals.Ready.Load() {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusServiceUnavailable, "inference service is not available")
	}

	if request.Specification.ExpiresAt.Time().Before(time.Now()) {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	if err := inferenceValidateRequestedPermissions(request.Specification.RequestedPermissions); err != nil {
		return orcapi.ApiTokenStatus{}, err
	}

	secret := util.SecureToken()
	hashedToken := util.HashPassword(secret, util.GenSalt())

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into inference_api_keys(token_id, owner, token_hash, token_salt, expires_at)
				values (:token_id, :owner, :token_hash, :token_salt, :expires_at)
				on conflict (token_id) do update
				set
					owner = excluded.owner,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at
			`,
			db.Params{
				"token_id":   request.Id,
				"owner":      request.Owner.Project.GetOrDefault(request.Owner.CreatedBy),
				"token_hash": hashedToken.HashedPassword,
				"token_salt": hashedToken.Salt,
				"expires_at": request.Specification.ExpiresAt.Time(),
			},
		)
	})

	status := orcapi.ApiTokenStatus{Server: inferenceServerBase()}
	status.Token.Set(fmt.Sprintf("uci-%s-%s", request.Id, secret))
	return status, nil
}

func inferenceServerBase() string {
	scheme, _, ok := strings.Cut(cfg.Provider.Hosts.SelfPublic.ToURL(), "://")
	if !ok {
		scheme = "https"
	}

	return fmt.Sprintf("%s://chat%s/v1", scheme, shared.ServiceConfig.Compute.Web.Suffix)
}

func inferenceParseToken(raw string) (tokenId string, secret string, ok bool) {
	payload, hasPrefix := strings.CutPrefix(raw, "uci-")
	if !hasPrefix {
		return "", "", false
	}

	tokenId, secret, ok = strings.Cut(payload, "-")
	if !ok || tokenId == "" || secret == "" {
		return "", "", false
	}

	return tokenId, secret, true
}

func inferenceValidateRequestedPermissions(perms []orcapi.ApiTokenPermission) *util.HttpError {
	for _, perm := range perms {
		if perm.Name != "inference" {
			return util.HttpErr(
				http.StatusBadRequest,
				"invalid token requested, %s/%s is not available",
				perm.Name,
				perm.Action,
			)
		}
	}

	return nil
}

func inferenceRevokeApiToken(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
	log.Info("Revoking inference API token: tokenId=%s user=%s", request.Id, info.Actor.Username)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
			delete from inference_api_keys where token_id = :token_id
		    `,
			db.Params{
				"token_id": request.Id,
			},
		)
	})

	if cacheKey, ok := inferenceTokenIdToKey.GetNow(request.Id); ok {
		inferenceApiKeysCache.Invalidate(cacheKey)
	}

	inferenceTokenIdToKey.Invalidate(request.Id)

	return util.Empty{}, nil
}

func inferenceRetrieveApiTokenOptions(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError) {
	_ = info
	_ = request

	return orcapi.ApiTokenOptions{
		AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{
			{
				Name:        "inference",
				Title:       "Inference",
				Description: "API token required for inference services",
				Actions: map[string]string{
					"use": "Use",
				},
			},
		},
	}, nil
}
