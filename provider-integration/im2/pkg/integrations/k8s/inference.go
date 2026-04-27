package k8s

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
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

type InferenceUsage struct {
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

		var request InferenceChatRequest
		if err := json.Unmarshal(body, &request); err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		if request.Stream {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for chunk := range InferenceChatStreaming(apiKeyOwner, request) {
				chunkData, err := json.Marshal(chunk)
				if err != nil {
					continue
				}

				_, _ = w.Write([]byte("data: "))
				_, _ = w.Write(chunkData)
				_, _ = w.Write([]byte("\n\n"))
				flusher.Flush()
			}

			_, _ = w.Write([]byte("data: [DONE]\n\n"))
			flusher.Flush()
			return
		}

		resp := InferenceChat(apiKeyOwner, request)
		respData, err := json.Marshal(resp)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
	})

	controller.Mux.HandleFunc(authority+"/v1/audio/transcriptions", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		request, httpErr := InferenceTranscriptionParseRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		if request.Stream {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for event := range InferenceTranscribeStreaming(apiKeyOwner, request) {
				data, err := json.Marshal(event)
				if err != nil {
					continue
				}

				_, _ = w.Write([]byte("data: "))
				_, _ = w.Write(data)
				_, _ = w.Write([]byte("\n\n"))
				flusher.Flush()
			}

			_, _ = w.Write([]byte("data: [DONE]\n\n"))
			flusher.Flush()
			return
		}

		resp, httpErr := InferenceTranscribe(apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		var respData []byte
		if resp.VerboseJson != nil {
			respData, _ = json.Marshal(resp.VerboseJson)
		} else if resp.DiarizedJson != nil {
			respData, _ = json.Marshal(resp.DiarizedJson)
		} else if resp.Json != nil {
			respData, _ = json.Marshal(resp.Json)
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
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

		var request InferenceImageGenerationRequest
		if err := json.Unmarshal(requestBody, &request); err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		if request.Stream.GetOrDefault(false) {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for event := range InferenceGenerateImageStreaming(apiKeyOwner, request) {
				data, err := json.Marshal(event)
				if err != nil {
					continue
				}

				_, _ = w.Write([]byte("data: "))
				_, _ = w.Write(data)
				_, _ = w.Write([]byte("\n\n"))
				flusher.Flush()
			}

			_, _ = w.Write([]byte("data: [DONE]\n\n"))
			flusher.Flush()
			return
		}

		respData, httpErr := inferenceGenerateImageResponse(apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
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
	var respData []byte
	var httpErr *util.HttpError

	if path == "/models" || path == "/models/" {
		var models InferenceModelsResponse
		models, httpErr = InferenceModels()
		if httpErr == nil {
			respData, _ = json.Marshal(models)
		}
	} else {
		modelId := strings.TrimPrefix(path, "/models/")
		var model InferenceModel
		model, httpErr = InferenceModelByID(modelId)
		if httpErr == nil {
			respData, _ = json.Marshal(model)
		}
	}

	if httpErr != nil {
		http.Error(w, httpErr.Why, httpErr.StatusCode)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(respData)
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

func inferenceIsLocked(owner apm.WalletOwner) bool {
	return controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name)
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
