package inference

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"math/bits"
	"net/http"
	"runtime"
	"slices"
	"strings"
	"sync"
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

var inferenceUsageFlushMu sync.Mutex
var inferenceUsageWake = make(chan struct{}, 1)

var inferenceAdmission = struct {
	sync.Mutex
	Total  int
	Owners map[string]int
}{Owners: map[string]int{}}

type inferenceUsageRow struct {
	Owner         string
	Scope         string
	Usage         int64
	ReportedUsage int64
}

const inferenceMaxConcurrent = 512
const inferenceMaxConcurrentPerOwner = 8

func inferenceAcquire(owner apm.WalletOwner) (func(), *util.HttpError) {
	ownerRef := owner.Reference()
	inferenceAdmission.Lock()
	defer inferenceAdmission.Unlock()
	if inferenceAdmission.Total >= inferenceMaxConcurrent || inferenceAdmission.Owners[ownerRef] >= inferenceMaxConcurrentPerOwner {
		return nil, util.HttpErr(http.StatusTooManyRequests, "too many concurrent inference requests")
	}
	inferenceAdmission.Total++
	inferenceAdmission.Owners[ownerRef]++
	return func() {
		inferenceAdmission.Lock()
		inferenceAdmission.Total--
		inferenceAdmission.Owners[ownerRef]--
		if inferenceAdmission.Owners[ownerRef] == 0 {
			delete(inferenceAdmission.Owners, ownerRef)
		}
		inferenceAdmission.Unlock()
	}, nil
}

const (
	inferenceDevelopmentProviderLocalAI = "localai"

	// Fallback accounting when image-generation usage is missing from backend responses.
	// Tokens are billed proportionally to generated megapixels (1 megapixel = 1,000,000 pixels).
	inferenceImageGenerationTokensPerMegaPixel = 1000.0
	inferenceMaxJSONRequestBytes               = 4 << 20
	inferenceMaxTranscriptionRequestBytes      = 64 << 20
	inferenceRequestTimeout                    = 30 * time.Minute
	inferenceStreamWriteTimeout                = 30 * time.Second
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

func Init() {
	initCli()
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
			Models:     make([]orcapi.InferenceModel, 0, len(models)),
			Benchmarks: inferenceBenchmarksToOrc(InferenceBenchmarkList()),
			IsAdmin:    isAdmin,
			Server:     inferenceServerBase(),
		}
		for _, model := range models {
			inferenceModel := orcapi.InferenceModel{
				Name:           model.Name,
				Title:          model.Title,
				TitleModelName: model.TitleModelName,
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
				ChatSettings: orcapi.InferenceChatSettings{
					Temperature:         model.ChatSettings.Temperature,
					TopP:                model.ChatSettings.TopP,
					MaxCompletionTokens: model.ChatSettings.MaxCompletionTokens,
					SystemPrompt:        model.ChatSettings.SystemPrompt,
					DisableTools:        model.ChatSettings.DisableTools,
				},
				Page: inferencePageToOrc(model.Page),
			}

			if !isAdmin {
				inferenceModel.Endpoint.BasePath = ""
				inferenceModel.Endpoint.BackendModelName = ""
				inferenceModel.ChatSettings.SystemPrompt = nil
				inferenceModel.Availability.AvailableTo = nil
				inferenceModel.TitleModelName = ""
			}

			result.Models = append(result.Models, inferenceModel)
		}
		return result, nil
	})

	orcapi.InferenceListPlaygroundThreadsProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceListPlaygroundThreadsProviderRequest) (orcapi.InferenceListPlaygroundThreadsResponse, *util.HttpError) {
		return orcapi.InferenceListPlaygroundThreadsResponse{
			Threads: InferencePlaygroundThreadSummaries(request.Owner.CreatedBy, request.Owner.Project),
		}, nil
	})

	orcapi.InferenceUpdateModelProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceUpdateModelProviderRequest) (util.Empty, *util.HttpError) {
		_ = info
		if !inferenceIsAdminOwner(request.Owner) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}

		model := InferenceModel{
			Name:           request.Model.Name,
			Title:          request.Model.Title,
			TitleModelName: request.Model.TitleModelName,
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
			ChatSettings: InferenceChatSettings{
				Temperature:         request.Model.ChatSettings.Temperature,
				TopP:                request.Model.ChatSettings.TopP,
				MaxCompletionTokens: request.Model.ChatSettings.MaxCompletionTokens,
				SystemPrompt:        request.Model.ChatSettings.SystemPrompt,
				DisableTools:        request.Model.ChatSettings.DisableTools,
			},
			Page: inferencePageFromOrc(request.Model.Page),
		}

		oldName := strings.TrimSpace(request.OldName)
		if oldName != "" && oldName != strings.TrimSpace(model.Name) {
			if strings.TrimSpace(model.TitleModelName) == "" || strings.TrimSpace(model.TitleModelName) == oldName {
				model.TitleModelName = model.Name
			}
			if err := InferenceModelRename(oldName, model.Name); err != nil {
				return util.Empty{}, err
			}
		}
		if err := InferenceModelUpsert(model); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	orcapi.InferenceUpdateBenchmarksProvider.Handler(func(info rpc.RequestInfo, request orcapi.InferenceUpdateBenchmarksProviderRequest) (util.Empty, *util.HttpError) {
		if !inferenceIsAdminOwner(request.Owner) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "forbidden")
		}
		if err := InferenceBenchmarkReplace(inferenceBenchmarksFromOrc(request.Benchmarks)); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

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
	go inferenceUsageFlushLoop()

	authority := fmt.Sprintf("chat%s", shared.ServiceConfig.Compute.Web.Suffix) // TODO Change for prod
	AttachmentInit()
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: authority,
			Type:         gateway.RouteTypeIngress,
		},
	})

	controller.Mux.HandleFunc(authority+"/v1/models", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		owner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, owner)
	})

	controller.Mux.HandleFunc(authority+"/v1/models/", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		owner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, owner)
	})

	controller.Mux.HandleFunc(authority+"/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxJSONRequestBytes {
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		var request InferenceChatRequest
		if !inferenceDecodeJSON(w, r, inferenceMaxJSONRequestBytes, &request) {
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()

		if request.Stream {
			chunks, httpErr := InferenceChatStreaming(ctx, apiKeyOwner, request)
			if httpErr != nil {
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
				if err := inferenceWriteSSE(w, append(append([]byte("data: "), chunkData...), '\n', '\n')); err != nil {
					cancel()
					for range chunks {
					}
					return
				}
			}

			_ = inferenceWriteSSE(w, []byte("data: [DONE]\n\n"))
			return
		}

		resp, httpErr := InferenceChat(ctx, apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		respData, err := json.Marshal(resp)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
	})

	controller.Mux.HandleFunc(authority+"/v1/responses", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxJSONRequestBytes {
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		var request OaiResponseCreateRequest
		if !inferenceDecodeJSON(w, r, inferenceMaxJSONRequestBytes, &request) {
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()

		if request.Stream {
			events, httpErr := InferenceResponseCreateStreaming(ctx, apiKeyOwner, request)
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

				payload := make([]byte, 0, len(event.Type)+len(data)+16)
				payload = append(payload, "event: "...)
				payload = append(payload, event.Type...)
				payload = append(payload, "\ndata: "...)
				payload = append(payload, data...)
				payload = append(payload, '\n', '\n')
				if err := inferenceWriteSSE(w, payload); err != nil {
					cancel()
					for range events {
					}
					return
				}
			}
			return
		}

		resp, httpErr := InferenceResponseCreate(ctx, apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		respData, err := json.Marshal(resp)
		if err != nil {
			http.Error(w, "invalid response", http.StatusBadGateway)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
	})

	controller.Mux.HandleFunc(authority+"/v1/responses/", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		path := strings.TrimPrefix(r.URL.Path, "/v1/responses/")
		path = strings.Trim(path, "/")
		if path == "" {
			http.Error(w, "response not found", http.StatusNotFound)
			return
		}

		if strings.HasSuffix(path, "/cancel") {
			if r.Method != http.MethodPost {
				http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
				return
			}
			id := strings.TrimSuffix(path, "/cancel")
			id = strings.Trim(id, "/")
			resp, httpErr := InferenceResponseCancel(apiKeyOwner, id)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
			return
		}

		switch r.Method {
		case http.MethodGet:
			resp, httpErr := InferenceResponsePoll(apiKeyOwner, path)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
		case http.MethodDelete:
			resp, httpErr := InferenceResponseDelete(apiKeyOwner, path)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
		default:
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	})

	controller.Mux.HandleFunc(authority+"/v1/audio/transcriptions", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxTranscriptionRequestBytes {
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		request, httpErr := InferenceTranscriptionParseRequest(w, r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()
		if request.Stream {
			events, httpErr := InferenceTranscribeStreaming(ctx, apiKeyOwner, request)
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

				if err := inferenceWriteSSE(w, append(append([]byte("data: "), data...), '\n', '\n')); err != nil {
					cancel()
					for range events {
					}
					return
				}
			}

			_ = inferenceWriteSSE(w, []byte("data: [DONE]\n\n"))
			return
		}

		resp, httpErr := InferenceTranscribe(ctx, apiKeyOwner, request)
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
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxJSONRequestBytes {
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		var request InferenceImageGenerationRequest
		if !inferenceDecodeJSON(w, r, inferenceMaxJSONRequestBytes, &request) {
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()

		if request.Stream.GetOrDefault(false) {
			events, httpErr := InferenceGenerateImageStreaming(ctx, apiKeyOwner, request)
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

				if err := inferenceWriteSSE(w, append(append([]byte("data: "), data...), '\n', '\n')); err != nil {
					cancel()
					for range events {
					}
					return
				}
			}

			_ = inferenceWriteSSE(w, []byte("data: [DONE]\n\n"))
			return
		}

		respData, httpErr := inferenceGenerateImageResponse(ctx, apiKeyOwner, request)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(respData)
	})
	inferenceGlobals.Ready.Store(true)
}

func inferenceIsAdminOwner(owner orcapi.ResourceOwner) bool {
	if !owner.Project.Present {
		return false
	}
	return slices.Contains(shared.ServiceConfig.Compute.Inference.Access.Administrators, owner.Project.Value)
}

func inferenceAuthenticateRequest(r *http.Request) (apm.WalletOwner, *util.HttpError) {
	authHeader := r.Header.Get("Authorization")
	apiKey, ok := strings.CutPrefix(authHeader, "Bearer ")
	if !ok || apiKey == "" {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}
	return inferenceApiKeyValidate(apiKey)
}

func inferenceDecodeJSON(w http.ResponseWriter, r *http.Request, limit int64, dst any) bool {
	if r.ContentLength > limit {
		http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, limit)
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(dst); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
		} else {
			http.Error(w, "invalid request", http.StatusBadRequest)
		}
		return false
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		http.Error(w, "invalid request", http.StatusBadRequest)
		return false
	}
	return true
}

func inferenceWriteSSE(w http.ResponseWriter, payload []byte) error {
	controller := http.NewResponseController(w)
	if err := controller.SetWriteDeadline(time.Now().Add(inferenceStreamWriteTimeout)); err != nil && !errors.Is(err, http.ErrNotSupported) {
		return err
	}
	if _, err := w.Write(payload); err != nil {
		return err
	}
	return controller.Flush()
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
	inferenceDiscoverModelsFromEndpoint(base, shared.ServiceConfig.Compute.Inference.Access.Testers, true)

	return nil
}

func inferenceDiscoverModelsFromEndpoint(base string, availableTo []string, disableTools bool) {
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

	body, err := io.ReadAll(io.LimitReader(resp.Body, inferenceMaxJSONRequestBytes+1))
	if err != nil {
		log.Warn("Could not read inference model discovery response from %s: %v", base, err)
		return
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		log.Warn("Inference model discovery response from %s exceeded the size limit", base)
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
			Name:           name,
			Title:          name,
			TitleModelName: name,
			Capabilities:   []InferenceCapability{InferenceTextGeneration},
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
			ChatSettings: InferenceChatSettings{
				Temperature:         0.8,
				TopP:                0.1,
				MaxCompletionTokens: 65536,
				DisableTools:        disableTools,
			},
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

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	services, err := shared.K8sClient.CoreV1().Services(namespace).List(ctx, metav1.ListOptions{})
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
		inferenceDiscoverModelsFromEndpoint(base, inferenceCfg.Access.Testers, false)
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
	client := http.Client{Timeout: 15 * time.Second}
	for i := 0; i < 60; i++ {
		resp, err := client.Get(endpoint)
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
	client := http.Client{Timeout: 30 * time.Second}
	requestVariants := []inferenceLocalAIApplyRequest{
		{Id: modelId},
		{Name: modelId},
	}

	for _, request := range requestVariants {
		payload, _ := json.Marshal(request)
		resp, err := client.Post(
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

	usageNonNormalized := inferenceUsageMultiply(cachedTokens, model.PriceMultiplier.CachedInput)
	usageNonNormalized = inferenceUsageAdd(usageNonNormalized, inferenceUsageMultiply(inputTokens, model.PriceMultiplier.Input))
	usageNonNormalized = inferenceUsageAdd(usageNonNormalized, inferenceUsageMultiply(outputTokens, model.PriceMultiplier.Output))
	usage := inferenceNormalizeUsage(usageNonNormalized)

	metricInferenceCachedInputTokens.WithLabelValues(model.Name).Add(float64(cachedTokens))
	metricInferenceInputTokens.WithLabelValues(model.Name).Add(float64(inputTokens))
	metricInferenceOutputTokens.WithLabelValues(model.Name).Add(float64(outputTokens))
	metricInferenceRequests.WithLabelValues(model.Name).Inc()

	if usage == 0 {
		return
	}

	scope := fmt.Sprintf("inference-%s-%s-%s", inferenceGlobals.Product.Category.Provider, inferenceGlobals.Product.Category.Name, util.SecureToken())
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into inference_usage(owner, scope, usage)
				values (:owner, :scope, :usage)
				on conflict (owner) do update set
					usage = case
						when inference_usage.usage > 9223372036854775807 - excluded.usage then 9223372036854775807
						else inference_usage.usage + excluded.usage
					end,
					updated_at = now()
			`,
			db.Params{"owner": owner.Reference(), "scope": scope, "usage": usage},
		)
	})
	select {
	case inferenceUsageWake <- struct{}{}:
	default:
	}
}

func inferenceUsageMultiply(tokens int, multiplier int) int64 {
	if tokens <= 0 || multiplier <= 0 {
		return 0
	}
	high, low := bits.Mul64(uint64(tokens), uint64(multiplier))
	if high != 0 || low > math.MaxInt64 {
		return math.MaxInt64
	}
	return int64(low)
}

func inferenceUsageAdd(a int64, b int64) int64 {
	if a > math.MaxInt64-b {
		return math.MaxInt64
	}
	return a + b
}

func inferenceNormalizeUsage(usage int64) int64 {
	if usage <= 0 {
		return 0
	}
	result := usage / 1000
	if usage%1000 != 0 {
		result++
	}
	return result
}

func inferenceUsageFlushLoop() {
	inferenceFlushUsage()
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
		case <-inferenceUsageWake:
		}
		inferenceFlushUsage()
	}
}

func inferenceFlushUsage() {
	inferenceUsageFlushMu.Lock()
	defer inferenceUsageFlushMu.Unlock()

	rows := db.NewTx(func(tx *db.Transaction) []inferenceUsageRow {
		return db.Select[inferenceUsageRow](
			tx,
			`select owner, scope, usage, reported_usage from inference_usage where usage > reported_usage order by owner`,
			db.Params{},
		)
	})
	for _, row := range rows {
		owner := apm.WalletOwnerFromReference(row.Owner)
		if row.Owner == "" || (owner.Username == "" && owner.ProjectId == "") {
			log.Warn("Could not report inference usage for invalid owner reference")
			continue
		}
		_, httpErr := apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{Items: []apm.ReportUsageRequest{{
			Owner:         owner,
			IsDeltaCharge: false,
			CategoryIdV2: apm.ProductCategoryIdV2{
				Name:     inferenceGlobals.Product.Category.Name,
				Provider: inferenceGlobals.Product.Category.Provider,
			},
			Usage: row.Usage,
			Description: apm.ChargeDescription{
				Scope: util.OptValue(row.Scope),
			},
		}}})
		if httpErr != nil {
			log.Warn("Could not report inference usage: owner=%s usage=%d err=%v", row.Owner, row.Usage, httpErr)
			continue
		}
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update inference_usage set reported_usage = greatest(reported_usage, :usage) where owner = :owner`,
				db.Params{"owner": row.Owner, "usage": row.Usage},
			)
		})
	}
}

func inferenceIsLocked(owner apm.WalletOwner) bool {
	return controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked
}
func inferenceServerBase() string {
	scheme, _, ok := strings.Cut(cfg.Provider.Hosts.SelfPublic.ToURL(), "://")
	if !ok {
		scheme = "https"
	}

	return fmt.Sprintf("%s://chat%s/v1", scheme, shared.ServiceConfig.Compute.Web.Suffix)
}

func inferencePageToOrc(page *InferenceModelPage) *orcapi.InferenceModelPage {
	if page == nil {
		return nil
	}
	data, err := json.Marshal(page)
	if err != nil {
		return nil
	}
	var result orcapi.InferenceModelPage
	if err := json.Unmarshal(data, &result); err != nil {
		return nil
	}
	return &result
}

func inferencePageFromOrc(page *orcapi.InferenceModelPage) *InferenceModelPage {
	if page == nil {
		return nil
	}
	data, err := json.Marshal(page)
	if err != nil {
		return nil
	}
	var result InferenceModelPage
	if err := json.Unmarshal(data, &result); err != nil {
		return nil
	}
	return &result
}

func inferenceBenchmarksToOrc(benchmarks []InferenceBenchmark) []orcapi.InferenceBenchmark {
	result := make([]orcapi.InferenceBenchmark, 0, len(benchmarks))
	for _, benchmark := range benchmarks {
		result = append(result, orcapi.InferenceBenchmark{
			Id:             benchmark.Id,
			Title:          benchmark.Title,
			Description:    benchmark.Description,
			HigherIsBetter: benchmark.HigherIsBetter,
			ModelNames:     append([]string{}, benchmark.ModelNames...),
		})
	}
	return result
}

func inferenceBenchmarksFromOrc(benchmarks []orcapi.InferenceBenchmark) []InferenceBenchmark {
	result := make([]InferenceBenchmark, 0, len(benchmarks))
	for _, benchmark := range benchmarks {
		result = append(result, InferenceBenchmark{
			Id:             benchmark.Id,
			Title:          benchmark.Title,
			Description:    benchmark.Description,
			HigherIsBetter: benchmark.HigherIsBetter,
			ModelNames:     append([]string{}, benchmark.ModelNames...),
		})
	}
	return result
}
