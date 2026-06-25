package k8s

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"runtime"
	"slices"
	"strings"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
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

type inferenceDiscoveredModel struct {
	Id            string `json:"id"`
	Object        string `json:"object"`
	ContextWindow *int   `json:"context_window,omitempty"`
}

type inferenceDiscoveredModelsResponse struct {
	Data []inferenceDiscoveredModel `json:"data"`
}

type InferenceUsage struct {
	PromptTokens        int                                    `json:"prompt_tokens"`
	CompletionTokens    int                                    `json:"completion_tokens"`
	PromptTokensDetails util.Option[InferenceChatTokenDetails] `json:"prompt_tokens_details,omitempty"`
}

var (
	metricInferenceCachedInputTokens = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "cached_input_tokens_total",
		Help:      "Total cached input tokens observed by inference model.",
	}, []string{"model"})

	metricInferenceInputTokens = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "input_tokens_total",
		Help:      "Total non-cached input tokens observed by inference model.",
	}, []string{"model"})

	metricInferenceOutputTokens = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "output_tokens_total",
		Help:      "Total output tokens observed by inference model.",
	}, []string{"model"})

	metricInferenceRequests = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "requests_total",
		Help:      "Total inference requests by model.",
	}, []string{"model"})
)

func initInference() {
	inferenceCfg := &shared.ServiceConfig.Compute.Inference
	if !inferenceCfg.Enabled {
		return
	}

	inferenceGlobals.BackendServer = strings.TrimRight(inferenceCfg.BackendServer, "/")
	if inferenceCfg.Provider == "" {
		inferenceCfg.Provider = cfg.KubernetesInferenceProviderDevelopment
	}
	if inferenceCfg.Provider == cfg.KubernetesInferenceProviderDevelopment && inferenceGlobals.BackendServer == "" {
		panic("inference backend server is not configured")
	}

	inferenceModelCatalogLoad()

	inferenceGlobals.MockImageGeneration = util.DevelopmentModeEnabled() && runtime.GOARCH == "arm64"
	if inferenceGlobals.MockImageGeneration {
		log.Info("Enabling mock image generation endpoint for development on arm64")
	}

	if inferenceCfg.Provider == cfg.KubernetesInferenceProviderDevelopment && util.DevelopmentModeEnabled() && inferenceCfg.DevelopmentProvider == inferenceDevelopmentProviderLocalAI {
		err := inferenceAutoConfigureLocalAI()
		if err != nil {
			panic(fmt.Sprintf("could not initialize localai: %s", err))
		}
	} else if inferenceCfg.Provider == cfg.KubernetesInferenceProviderDynamo {
		go func() {
			inferenceDiscoverDynamoModels()
			ticker := time.NewTicker(60 * time.Second)
			defer ticker.Stop()
			for range ticker.C {
				inferenceDiscoverDynamoModels()
			}
		}()
	}

	orcapi.InferenceListModelsProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceListModelsProviderRequest) (orcapi.InferenceListModelsResponse, *util.HttpError) {
		_ = info
		owner := apm.WalletOwnerUser(request.Owner.CreatedBy)
		if request.Owner.Project.Present {
			owner = apm.WalletOwnerProject(request.Owner.Project.Value)
		}
		isAdmin := inferenceIsAdminOwner(request.Owner)
		models := InferenceModelListForOwner(owner)
		if isAdmin {
			models = InferenceModelList()
		}

		result := orcapi.InferenceListModelsResponse{
			Models:  make([]orcapi.InferenceModel, 0, len(models)),
			IsAdmin: isAdmin,
		}
		for _, model := range models {
			result.Models = append(result.Models, orcapi.InferenceModel{
				Name:  model.Name,
				Title: model.Title,
				Capabilities: func() []orcapi.InferenceCapability {
					capabilities := make([]orcapi.InferenceCapability, 0, len(model.Capabilities))
					for _, capability := range model.Capabilities {
						capabilities = append(capabilities, orcapi.InferenceCapability(capability))
					}
					return capabilities
				}(),
				PriceMultiplier: orcapi.InferencePricing{
					CachedInput: model.PriceMultiplier.CachedInput,
					Input:       model.PriceMultiplier.Input,
					Output:      model.PriceMultiplier.Output,
				},
				Endpoint: orcapi.InferenceEndpoint{
					BasePath:         model.Endpoint.BasePath,
					BackendModelName: model.Endpoint.BackendModelName,
				},
				Availability: orcapi.InferenceAvailability{
					Public:      model.Availability.Public,
					AvailableTo: append([]string{}, model.Availability.AvailableTo...),
				},
				ContextWindow: model.ContextWindow,
			})
		}
		return result, nil
	})

	orcapi.InferenceUpdateModelProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceUpdateModelProviderRequest) (util.Empty, *util.HttpError) {
		_ = info
		if !inferenceIsAdminOwner(request.Owner) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		model := InferenceModel{
			Name:  request.Model.Name,
			Title: request.Model.Title,
			Capabilities: func() []InferenceCapability {
				capabilities := make([]InferenceCapability, 0, len(request.Model.Capabilities))
				for _, capability := range request.Model.Capabilities {
					capabilities = append(capabilities, InferenceCapability(capability))
				}
				return capabilities
			}(),
			PriceMultiplier: InferencePricing{
				CachedInput: request.Model.PriceMultiplier.CachedInput,
				Input:       request.Model.PriceMultiplier.Input,
				Output:      request.Model.PriceMultiplier.Output,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         request.Model.Endpoint.BasePath,
				BackendModelName: request.Model.Endpoint.BackendModelName,
			},
			Availability: InferenceAvailability{
				Public:      request.Model.Availability.Public,
				AvailableTo: append([]string{}, request.Model.Availability.AvailableTo...),
			},
			ContextWindow: request.Model.ContextWindow,
		}

		oldName := strings.TrimSpace(request.OldName)
		if oldName != "" && oldName != strings.TrimSpace(model.Name) {
			if err := InferenceModelRename(oldName, model.Name); err != nil {
				return util.Empty{}, err
			}
		}
		if err := InferenceModelUpsert(model); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	inferenceGlobals.Ready.Store(true)

	inferenceGlobals.Product = apm.ProductV2{
		Type: apm.ProductTypeCInference,
		Category: apm.ProductCategory{
			Name:        "inference",
			Provider:    cfg.Provider.Id,
			ProductType: apm.ProductTypeInference,
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
		ProductType:               apm.ProductTypeInference,
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
		owner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, owner)
	})

	controller.Mux.HandleFunc(authority+"/v1/models/", func(w http.ResponseWriter, r *http.Request) {
		owner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, owner)
	})

	controller.Mux.HandleFunc(authority+"/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		body, err := io.ReadAll(r.Body) // TODO limit
		if err != nil {
			log.Info("fail 1: %v", err)
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		var request InferenceChatRequest
		if err := json.Unmarshal(body, &request); err != nil {
			log.Info("fail 2: %v", err)
			log.Info("body was: %s", string(body))
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		log.Info("body: %s", string(body))

		if request.Stream {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			chunks, httpErr := InferenceChatStreaming(apiKeyOwner, request)
			if httpErr != nil {
				log.Info("fail 3: %v", httpErr)
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for chunk := range chunks {
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

		resp, httpErr := InferenceChat(apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		respData, err := json.Marshal(resp)
		if err != nil {
			log.Info("fail 4: %v", err)
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

			events, httpErr := InferenceTranscribeStreaming(apiKeyOwner, request)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for event := range events {
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

			events, httpErr := InferenceGenerateImageStreaming(apiKeyOwner, request)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}

			w.Header().Set("Content-Type", "text/event-stream")
			w.Header().Set("Cache-Control", "no-cache")
			w.Header().Set("Connection", "keep-alive")

			for event := range events {
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

func inferenceIsAdminOwner(owner orcapi.ResourceOwner) bool {
	if !owner.Project.Present {
		return false
	}
	return slices.Contains(shared.ServiceConfig.Compute.Inference.Access.Administrators, owner.Project.Value)
}

func inferenceAuthenticateRequest(r *http.Request) (apm.WalletOwner, *util.HttpError) {
	authHeader := r.Header.Get("Authorization")
	apiKey, _ := strings.CutPrefix(authHeader, "Bearer ")
	return inferenceApiKeyValidate(apiKey)
}

func inferenceProxyModelsRequest(w http.ResponseWriter, r *http.Request, owner apm.WalletOwner) {
	path := strings.TrimPrefix(r.URL.Path, "/v1")
	if path == "" {
		path = "/models"
	}
	var respData []byte
	var httpErr *util.HttpError

	if path == "/models" || path == "/models/" {
		var models OaiInferenceModelsResponse
		models, httpErr = OaiInferenceModels(owner)
		if httpErr == nil {
			respData, _ = json.Marshal(models)
		}
	} else {
		modelId := strings.TrimPrefix(path, "/models/")
		var model OaiInferenceModel
		model, httpErr = OaiInferenceModelByID(owner, modelId)
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
	inferenceDiscoverModelsFromEndpoint(base, shared.ServiceConfig.Compute.Inference.Access.Testers)

	return nil
}

func inferenceDiscoverModelsFromEndpoint(base string, availableTo []string) {
	base = strings.TrimRight(base, "/")
	client := http.Client{Timeout: 15 * time.Second}
	resp, err := client.Get(base + "/models")
	if err != nil {
		log.Warn("Could not discover inference models from %s: %v", base, err)
		return
	}
	defer util.SilentClose(resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Warn("Could not discover inference models from %s: status=%d", base, resp.StatusCode)
		return
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Warn("Could not read inference model discovery response from %s: %v", base, err)
		return
	}

	var models inferenceDiscoveredModelsResponse
	if err := json.Unmarshal(body, &models); err != nil {
		log.Warn("Could not parse inference model discovery response from %s: %v", base, err)
		return
	}

	for _, model := range models.Data {
		name := strings.TrimSpace(model.Id)
		if model.Object != "model" || name == "" {
			continue
		}

		catalogModel := inferenceModelNormalize(InferenceModel{
			Name:         name,
			Title:        name,
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PriceMultiplier: InferencePricing{
				CachedInput: 1000,
				Input:       1000,
				Output:      1000,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         base,
				BackendModelName: name,
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: availableTo,
			},
			ContextWindow: model.ContextWindow,
		})
		if inferenceModelValidate(catalogModel) != nil {
			continue
		}

		inserted := false
		modelGlobals.Mu.Lock()
		knownBackendName := ""
		for existingName, existing := range modelGlobals.Models {
			if existing.Endpoint.BackendModelName == catalogModel.Endpoint.BackendModelName {
				knownBackendName = existingName
				break
			}
		}
		existing, knownName := modelGlobals.Models[catalogModel.Name]
		if !knownName && knownBackendName != "" {
			existing = modelGlobals.Models[knownBackendName]
		}
		if !knownName && knownBackendName == "" {
			db.NewTx0(func(tx *db.Transaction) {
				inferenceModelUpsertTx(tx, catalogModel)
			})
			modelGlobals.Models[catalogModel.Name] = inferenceModelClone(catalogModel)
			inserted = true
		} else if catalogModel.ContextWindow != nil && (existing.ContextWindow == nil || *existing.ContextWindow != *catalogModel.ContextWindow) {
			existing.ContextWindow = catalogModel.ContextWindow
			db.NewTx0(func(tx *db.Transaction) {
				inferenceModelUpsertTx(tx, existing)
			})
			modelGlobals.Models[existing.Name] = inferenceModelClone(existing)
		}
		modelGlobals.Mu.Unlock()

		if inserted {
			log.Info("Discovered inference model %s at %s", name, base)
		}
	}
}

func inferenceDiscoverDynamoModels() {
	inferenceCfg := &shared.ServiceConfig.Compute.Inference
	namespace := strings.TrimSpace(inferenceCfg.Dynamo.Namespace)
	if namespace == "" {
		return
	}
	if shared.K8sClient == nil {
		log.Warn("Could not discover Dynamo inference models: Kubernetes client is not initialized")
		return
	}

	services, err := shared.K8sClient.CoreV1().Services(namespace).List(context.Background(), metav1.ListOptions{})
	if err != nil {
		log.Warn("Could not list Dynamo inference services in namespace %s: %v", namespace, err)
		return
	}

	for _, service := range services.Items {
		if !strings.HasSuffix(service.Name, "-frontend") {
			continue
		}
		if len(service.Spec.Ports) == 0 {
			continue
		}

		port := service.Spec.Ports[0].Port
		if port <= 0 {
			continue
		}

		base := fmt.Sprintf("http://%s.%s.svc.cluster.local:%d/v1", service.Name, service.Namespace, port)
		inferenceDiscoverModelsFromEndpoint(base, inferenceCfg.Access.Testers)
	}
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

func inferenceReportUsage(owner apm.WalletOwner, model InferenceModel, cachedTokens int, inputTokens int, outputTokens int) {
	if cachedTokens < 0 {
		cachedTokens = 0
	}
	if inputTokens < 0 {
		inputTokens = 0
	}
	if outputTokens < 0 {
		outputTokens = 0
	}

	usageNonNormalized := cachedTokens*model.PriceMultiplier.CachedInput + inputTokens*model.PriceMultiplier.Input + outputTokens*model.PriceMultiplier.Output
	usage := usageNonNormalized / 1000
	if usage == 0 && usageNonNormalized > 0 {
		usage = 1
	}

	metricInferenceCachedInputTokens.WithLabelValues(model.Name).Add(float64(cachedTokens))
	metricInferenceInputTokens.WithLabelValues(model.Name).Add(float64(inputTokens))
	metricInferenceOutputTokens.WithLabelValues(model.Name).Add(float64(outputTokens))
	metricInferenceRequests.WithLabelValues(model.Name).Inc()

	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         owner,
				IsDeltaCharge: true,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     inferenceGlobals.Product.Category.Name,
					Provider: inferenceGlobals.Product.Category.Provider,
				},
				Usage:       int64(usage),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

func inferenceIsLocked(owner apm.WalletOwner) bool {
	return controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked
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
	if controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked {
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
