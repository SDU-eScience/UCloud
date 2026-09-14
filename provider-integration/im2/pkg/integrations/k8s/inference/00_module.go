package inference

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"math/bits"
	"net/http"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
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

// AI Platform
// =====================================================================================================================
// This file contains the main entrypoint to UCloud's AI Platform. The UCloud AI platform is generally written to be
// a middleware that connects the UCloud core abstractions, such as projects and quotas, with an upstream inference
// provider. UCloud supports automatic discovery of inference providers via NVIDIA Dynamo and LocalAI (for ./launcher
// based development). It is possible to manually configure other inference backends. In addition to doing this, the
// UCloud AI Platform adds auditing and a chat interface.
//
// Regardless of how you access the AI platform, a request always moves through these stages:
//
// 1. Attach the wallet owner, user identity, and audit context.
// 2. Resolve an available catalog model and validate model-specific options.
// 3. Acquire global and per-user admission capacity.
// 4. Send the translated request to the configured backend.
// 5. Adapt the backend response to the requested public protocol.
// 6. Record token usage in durable storage and schedule its accounting report.
// 7. Complete the audit record and release admission capacity.
//
// The module keeps admission state in memory because each slot represents work in this provider process. It keeps
// usage state in the database because a process restart must not lose charges that have not reached accounting.
//
// File guide:
//
// - `01_middleware.go` coordinates model validation, admission, backend calls, usage, and audit completion.
// - `adapter_oai_responses.go` adapts OpenAI Responses requests, events, and results.
// - `adapter_oai_responses_store.go` stores Responses and conversation state.
// - `catalog.go` owns models, access rules, pricing, defaults, and benchmarks.
// - `attachments.go` stores temporary request files and converts text attachments to Markdown.
// - `audit.go` creates audit context and records request completion or rejection.
// - `playground.go` implements playground requests and thread operations.
// - `playground_threads_fs.go` persists playground threads in the file system.
// - `api_tokens.go` creates and validates inference API keys.
// - `discovery.go` discovers backend models and updates their catalog entries.
// - `playground_prompt.go` builds the playground system prompt.
// - `playground_tools.go` defines and dispatches playground tools.
// - `cli.go` provides inference administration commands.

// Core configuration and state
// ---------------------------------------------------------------------------------------------------------------------
// This section defines process limits and state shared by all inference routes. Request limits bound memory use and
// stalled connections. The global admission limit protects the backend from excess concurrent work. The per-user
// limit preserves fairness when one user sends many requests. Admission uses the username as its key. It falls back to
// the wallet owner when authentication does not provide a username (legacy API keys).
//
// The admission queue absorbs short bursts instead of rejecting every request that arrives while all slots are in
// use. The release channel only signals that state changed. Callers always check the counters while holding the mutex.
// Usage wakeups follow the same notification pattern and let the database remain the source of pending charges.
//
// The local usage row represents durable accounting state. `InferenceUsage` is the common token count used by inference
// APIs in this package.

const (
	inferenceMaxConcurrent         = 4096
	inferenceMaxConcurrentPerOwner = 8
	inferenceAdmissionQueueTimeout = 60 * time.Second

	inferenceMaxJSONRequestBytes = 1024 * 1024 * 16
	inferenceRequestTimeout      = 30 * time.Minute
	inferenceStreamWriteTimeout  = 30 * time.Second

	inferenceDevelopmentProviderLocalAI = "localai"
)

var inferenceGlobals struct {
	Ready         atomic.Bool
	BackendServer string
	Product       apm.ProductV2
}

var inferenceUsageFlushMu sync.Mutex
var inferenceUsageWake = make(chan struct{}, 1)

var inferenceAdmission = struct {
	sync.Mutex
	Total  int
	Owners map[string]int
}{Owners: map[string]int{}}

var inferenceAdmissionRelease = make(chan struct{}, 1)

type inferenceUsageRow struct {
	Owner         string
	Scope         string
	Usage         int64
	Remainder     int64
	ReportedUsage int64
}

type InferenceUsage struct {
	PromptTokens        int                                    `json:"prompt_tokens"`
	CompletionTokens    int                                    `json:"completion_tokens"`
	PromptTokensDetails util.Option[InferenceChatTokenDetails] `json:"prompt_tokens_details,omitempty"`
}

// Initialization and routes
// ---------------------------------------------------------------------------------------------------------------------
// When this feature is enabled, it will resolve the provider configuration, load the catalog and start model discovery.
// RPCs are registered and products are registered with the Core.
//
// Given that this covers an API that does not follow UCloud's RPC system, the API is implemented directly on the HTTP
// multiplexer. This means that parsing and similar mechanisms are all handled directly in this file instead of relying
// on the normal RPC system. In particular, streaming requests are quite different from how UCloud's normal RPC works.

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
				Name:  model.Name,
				Title: model.Title,
				Capabilities: func() []orcapi.InferenceCapability {
					capabilities := make([]orcapi.InferenceCapability, 0, len(model.Capabilities))
					for _, capability := range model.Capabilities {
						capabilities = append(capabilities, orcapi.InferenceCapability(capability))
					}
					return capabilities
				}(),
				ReasoningEfforts: func() []orcapi.InferenceModelOption {
					efforts := make([]orcapi.InferenceModelOption, 0, len(model.ReasoningEfforts))
					for _, effort := range model.ReasoningEfforts {
						efforts = append(efforts, orcapi.InferenceModelOption{Name: effort.Name, Value: effort.Value})
					}
					return efforts
				}(),
				DefaultReasoningEffort: model.DefaultReasoningEffort,
				PricePerMillion: orcapi.InferencePricing{
					CachedInput: model.PricePerMillion.CachedInput,
					Input:       model.PricePerMillion.Input,
					Output:      model.PricePerMillion.Output,
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
			Name:  request.Model.Name,
			Title: request.Model.Title,
			Capabilities: func() []InferenceCapability {
				capabilities := make([]InferenceCapability, 0, len(request.Model.Capabilities))
				for _, capability := range request.Model.Capabilities {
					capabilities = append(capabilities, InferenceCapability(capability))
				}
				return capabilities
			}(),
			ReasoningEfforts: func() []InferenceModelOption {
				efforts := make([]InferenceModelOption, 0, len(request.Model.ReasoningEfforts))
				for _, effort := range request.Model.ReasoningEfforts {
					efforts = append(efforts, InferenceModelOption{Name: effort.Name, Value: effort.Value})
				}
				return efforts
			}(),
			DefaultReasoningEffort: request.Model.DefaultReasoningEffort,
			PricePerMillion: InferencePricing{
				CachedInput: request.Model.PricePerMillion.CachedInput,
				Input:       request.Model.PricePerMillion.Input,
				Output:      request.Model.PricePerMillion.Output,
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
				Name:                   "Credit",
				NamePlural:             "Credits",
				FloatingPoint:          true,
				DisplayFrequencySuffix: false,
			},
			AccountingFrequency: apm.AccountingFrequencyOnce,
			FreeToUse:           false,
			AllowSubAllocations: true,
		},
		Name:                      "inference",
		Description:               "Inference credits",
		ProductType:               apm.ProductTypeInference,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	controller.ProductsRegister([]apm.ProductV2{inferenceGlobals.Product})
	go inferenceUsageFlushLoop()

	authority := shared.ServiceConfig.Compute.Inference.Authority
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
		owner, _, _, httpErr := inferenceAuthenticateRequest(r)
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
		owner, _, _, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		inferenceProxyModelsRequest(w, r, owner)
	})

	controller.Mux.HandleFunc(authority+"/v1/chat/completions", inferenceAuditMiddleware("inference.chat", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			inferenceAuditReject(r.Context(), "method not allowed")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxJSONRequestBytes {
			inferenceAuditReject(r.Context(), "request body too large")
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, apiKeyUsername, tokenId, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			inferenceAuditReject(r.Context(), httpErr.Why)
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		inferenceAuditIdentity(r.Context(), apiKeyOwner, apiKeyUsername, tokenId)

		var request InferenceChatRequest
		if !inferenceDecodeJSON(w, r, inferenceMaxJSONRequestBytes, &request) {
			inferenceAuditReject(r.Context(), "invalid request body")
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()

		if request.Stream {
			chunks, httpErr := InferenceChatStreaming(ctx, apiKeyOwner, apiKeyUsername, request)
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
		} else {
			resp, httpErr := InferenceChat(ctx, apiKeyOwner, apiKeyUsername, request)
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
		}
	}))

	controller.Mux.HandleFunc(authority+"/v1/responses", inferenceAuditMiddleware("inference.responses", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			inferenceAuditReject(r.Context(), "method not allowed")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if r.ContentLength > inferenceMaxJSONRequestBytes {
			inferenceAuditReject(r.Context(), "request body too large")
			http.Error(w, "request body too large", http.StatusRequestEntityTooLarge)
			return
		}
		apiKeyOwner, createdBy, tokenId, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			inferenceAuditReject(r.Context(), httpErr.Why)
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		inferenceAuditIdentity(r.Context(), apiKeyOwner, createdBy, tokenId)
		if createdBy == "" {
			inferenceAuditReject(r.Context(), "token has no associated user")
			http.Error(w, "token has no associated user", http.StatusForbidden)
			return
		}
		var request OaiResponseCreateRequest
		if !inferenceDecodeJSON(w, r, inferenceMaxJSONRequestBytes, &request) {
			inferenceAuditReject(r.Context(), "invalid request body")
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), inferenceRequestTimeout)
		defer cancel()

		if request.Stream {
			events, httpErr := InferenceResponseCreateStreaming(ctx, apiKeyOwner, createdBy, request)
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
		} else {
			resp, httpErr := InferenceResponseCreate(ctx, apiKeyOwner, createdBy, request)
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
		}
	}))

	controller.Mux.HandleFunc(authority+"/v1/responses/", inferenceAuditMiddleware("inference.responses", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, createdBy, tokenId, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			inferenceAuditReject(r.Context(), httpErr.Why)
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		inferenceAuditIdentity(r.Context(), apiKeyOwner, createdBy, tokenId)
		if createdBy == "" {
			inferenceAuditReject(r.Context(), "token has no associated user")
			http.Error(w, "token has no associated user", http.StatusForbidden)
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
				inferenceAuditReject(r.Context(), "method not allowed")
				http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
				return
			}
			id := strings.TrimSuffix(path, "/cancel")
			id = strings.Trim(id, "/")
			resp, httpErr := InferenceResponseCancel(apiKeyOwner, createdBy, id)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
			return
		} else if r.Method == http.MethodGet {
			resp, httpErr := InferenceResponsePoll(apiKeyOwner, createdBy, path)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
		} else if r.Method == http.MethodDelete {
			resp, httpErr := InferenceResponseDelete(apiKeyOwner, createdBy, path)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			respData, _ := json.Marshal(resp)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(respData)
		} else {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	}))

	controller.Mux.HandleFunc(authority+"/v1/conversations/", inferenceAuditMiddleware("inference.conversations", func(w http.ResponseWriter, r *http.Request) {
		apiKeyOwner, createdBy, tokenId, httpErr := inferenceAuthenticateRequest(r)
		if httpErr != nil {
			inferenceAuditReject(r.Context(), httpErr.Why)
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}
		inferenceAuditIdentity(r.Context(), apiKeyOwner, createdBy, tokenId)
		if createdBy == "" {
			inferenceAuditReject(r.Context(), "token has no associated user")
			http.Error(w, "token has no associated user", http.StatusForbidden)
			return
		}

		id := strings.TrimPrefix(r.URL.Path, "/v1/conversations/")
		id = strings.Trim(id, "/")
		if id == "" {
			http.Error(w, "conversation not found", http.StatusNotFound)
			return
		}

		switch r.Method {
		case http.MethodGet:
			conversation, httpErr := InferenceConversationRetrieve(apiKeyOwner, createdBy, id)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			conversationData, _ := json.Marshal(conversation)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(conversationData)
		case http.MethodDelete:
			result, httpErr := InferenceConversationDelete(apiKeyOwner, createdBy, id)
			if httpErr != nil {
				http.Error(w, httpErr.Why, httpErr.StatusCode)
				return
			}
			inferenceAuditRecord(
				r.Context(),
				"inference.conversations.delete",
				apiKeyOwner,
				createdBy,
				time.Now(),
				mustMarshal(map[string]string{"conversationId": id}),
				"",
			)
			resultData, _ := json.Marshal(result)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(resultData)
		default:
			inferenceAuditReject(r.Context(), "method not allowed")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	}))

	inferenceGlobals.Ready.Store(true)
}

// HTTP request boundary
// ---------------------------------------------------------------------------------------------------------------------
// This section contains HTTP boundary helpers shared by the registered routes. Authentication converts a bearer key
// into a wallet owner, username, and token identifier. Admin checks use the configured project list. Wallet checks use
// the registered inference product category.
//
// Important helpers include `inferenceAuthenticateRequest`, `inferenceDecodeJSON`, `inferenceWriteSSE`, and
// `inferenceProxyModelsRequest`.

func inferenceIsAdminOwner(owner orcapi.ResourceOwner) bool {
	if !owner.Project.Present {
		return false
	}
	return slices.Contains(shared.ServiceConfig.Compute.Inference.Access.Administrators, owner.Project.Value)
}

func inferenceIsLocked(owner apm.WalletOwner) bool {
	return controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name).Locked
}

func inferenceAuthenticateRequest(r *http.Request) (apm.WalletOwner, string, string, *util.HttpError) {
	authHeader := r.Header.Get("Authorization")
	apiKey, ok := strings.CutPrefix(authHeader, "Bearer ")
	if !ok || apiKey == "" {
		return apm.WalletOwner{}, "", "", util.HttpErr(http.StatusForbidden, "invalid key")
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
	ctrl := http.NewResponseController(w)
	err := ctrl.SetWriteDeadline(time.Now().Add(inferenceStreamWriteTimeout))
	if err != nil && !errors.Is(err, http.ErrNotSupported) {
		return err
	}
	if _, err := w.Write(payload); err != nil {
		return err
	}
	return ctrl.Flush()
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

// Admission control
// ---------------------------------------------------------------------------------------------------------------------
// `inferenceAcquire` reserves one global slot and one per-user slot as one operation. The global limit protects backend
// capacity. The per-user limit preserves fairness. The username is the admission key when it is available. The wallet
// owner reference is the fallback key, so requests without a username still share a bounded allocation.
//
// A full request waits for a release notification until its context ends or the queue timeout expires. This queue
// absorbs bursts that are shorter than active inference work. A notification does not grant a slot because several
// waiters can observe changing capacity. Each waiter returns to the locked counter check before admission.
//
// The returned release function removes both reservations, deletes empty user state, updates the in-flight gauge, and
// wakes one waiter. A request canceled before admission never reaches the backend and does not create billable work.

func inferenceAcquire(ctx context.Context, owner apm.WalletOwner, username string) (func(), *util.HttpError) {
	admissionOwnerRef := owner.Reference()
	if username != "" {
		admissionOwnerRef = username
	}

	// Try to grab a slot before waiting for a release notification.
	deadline := time.NewTimer(inferenceAdmissionQueueTimeout)
	defer deadline.Stop()
	for {
		inferenceAdmission.Lock()
		if inferenceAdmission.Total < inferenceMaxConcurrent && inferenceAdmission.Owners[admissionOwnerRef] < inferenceMaxConcurrentPerOwner {
			inferenceAdmission.Total++
			inferenceAdmission.Owners[admissionOwnerRef]++
			inferenceAdmission.Unlock()
			metricInferenceRequestsInFlight.Inc()
			return func() {
				inferenceAdmission.Lock()
				inferenceAdmission.Total--
				inferenceAdmission.Owners[admissionOwnerRef]--
				if inferenceAdmission.Owners[admissionOwnerRef] == 0 {
					delete(inferenceAdmission.Owners, admissionOwnerRef)
				}
				metricInferenceRequestsInFlight.Dec()
				inferenceAdmission.Unlock()
				select {
				case inferenceAdmissionRelease <- struct{}{}:
				default:
				}
			}, nil
		}

		globalFull := inferenceAdmission.Total >= inferenceMaxConcurrent
		inferenceAdmission.Unlock()

		// Slow path: the caller's bucket is full. Wait for a slot to free up rather than dropping
		// the request immediately.
		select {
		case <-ctx.Done():
			// The client gave up while waiting in the queue.
			metricInferenceRequestsRejected.WithLabelValues("cancelled").Inc()
			return nil, util.HttpErr(499, "client disconnected while waiting for an inference slot")
		case <-deadline.C:
			// Timed out and the bucket is still full.
			if globalFull {
				metricInferenceRequestsRejected.WithLabelValues("global").Inc()
			} else {
				metricInferenceRequestsRejected.WithLabelValues("owner").Inc()
			}
			return nil, util.HttpErr(http.StatusTooManyRequests, "too many concurrent inference requests")
		case <-inferenceAdmissionRelease:
		}
	}
}

// Usage accounting
// ---------------------------------------------------------------------------------------------------------------------
// This section converts token counts into accounting usage. Prices use fixed-point units per million tokens. Cached
// input, uncached input, and output are multiplied separately, then combined before division. This keeps fractional
// remainders across requests instead of losing them during each price calculation.
//
// `inferenceReportUsage` writes daily per-model token totals and cumulative owner charges in one transaction. It then
// wakes the flush loop. `inferenceFlushUsage` sends the latest cumulative charge to accounting and advances the
// reported value only after success. A failed report leaves the row ahead of its reported value, so a later flush
// retries it. A mutex prevents timer and wake events from running overlapping flushes.
//
// The arithmetic helpers saturate fixed-point multiplication and addition so an invalid extreme value cannot wrap into
// a lower charge.
//
// Important entry points include `inferenceReportUsage` and `inferenceFlushUsage`.

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

	weightedUsage := inferenceUsageMultiply(cachedTokens, model.PricePerMillion.CachedInput)
	weightedUsage = inferenceUsageAdd(weightedUsage, inferenceUsageMultiply(inputTokens, model.PricePerMillion.Input))
	weightedUsage = inferenceUsageAdd(weightedUsage, inferenceUsageMultiply(outputTokens, model.PricePerMillion.Output))
	usage := weightedUsage / InferencePriceScale
	remainder := weightedUsage % InferencePriceScale

	metricInferenceCachedInputTokens.WithLabelValues(model.Name).Add(float64(cachedTokens))
	metricInferenceInputTokens.WithLabelValues(model.Name).Add(float64(inputTokens))
	metricInferenceOutputTokens.WithLabelValues(model.Name).Add(float64(outputTokens))
	metricInferenceRequests.WithLabelValues(model.Name).Inc()

	scope := fmt.Sprintf("inference-%s-%s-%s", inferenceGlobals.Product.Category.Provider, inferenceGlobals.Product.Category.Name, util.SecureToken())
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into inference_usage_by_model(
					owner,
					model,
					usage_day,
					cached_input_tokens,
					input_tokens,
					output_tokens
				)
				values (
					:owner,
					:model,
					cast((now() at time zone 'utc') as date),
					:cached_input_tokens,
					:input_tokens,
					:output_tokens
				)
				on conflict (owner, model, usage_day) do update set
					cached_input_tokens = cast((
						cast(inference_usage_by_model.cached_input_tokens as numeric) + excluded.cached_input_tokens
					) as bigint),
					input_tokens = cast((
						cast(inference_usage_by_model.input_tokens as numeric) + excluded.input_tokens
					) as bigint),
					output_tokens = cast((
						cast(inference_usage_by_model.output_tokens as numeric) + excluded.output_tokens
					) as bigint),
					updated_at = now()
			`,
			db.Params{
				"owner":               owner.Reference(),
				"model":               model.Name,
				"cached_input_tokens": int64(cachedTokens),
				"input_tokens":        int64(inputTokens),
				"output_tokens":       int64(outputTokens),
			},
		)
		db.Exec(
			tx,
			`
				insert into inference_usage(owner, scope, usage, remainder)
				values (:owner, :scope, :usage, :remainder)
				on conflict (owner) do update set
					usage = cast((
						cast(inference_usage.usage as numeric)
							+ excluded.usage
							+ (inference_usage.remainder + excluded.remainder) / 1000000
					) as bigint),
					remainder = (inference_usage.remainder + excluded.remainder) % 1000000,
					updated_at = now()
			`,
			db.Params{
				"owner":     owner.Reference(),
				"scope":     scope,
				"usage":     usage,
				"remainder": remainder,
			},
		)
	})
	select {
	case inferenceUsageWake <- struct{}{}:
	default:
	}
}

func inferenceUsageMultiply(tokens int, encodedPrice int64) int64 {
	if tokens <= 0 || encodedPrice <= 0 {
		return 0
	}
	high, low := bits.Mul64(uint64(tokens), uint64(encodedPrice))
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
			`
				select
					owner,
					scope,
					usage,
					remainder,
					reported_usage
				from inference_usage
				where
					usage > reported_usage
				order by owner
			`,
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
				db.Params{
					"owner": row.Owner,
					"usage": row.Usage,
				},
			)
		})
	}
}

// Observability
// ---------------------------------------------------------------------------------------------------------------------
// These metrics describe backend demand, admission pressure, request outcomes, token volume, and stream behavior.
// Counters separate cached input, uncached input, and output because each class can have a different price. Request
// histograms show payload size and latency independently from total token counters.
//
// Stream metrics measure first output latency, last output latency, output rate, and the interval between output
// deltas. Admission metrics expose active requests and classify rejections by global capacity, user capacity, or
// cancellation while queued. Model labels support comparisons without exposing wallet or user identity.
//
// The reporting functions record request duration, outcome, stream timing, token counts, and cache ratio.

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
		Help:      "Total inference requests with usage reported by model.",
	}, []string{"model"})

	metricInferenceTimeToFirstToken = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "time_to_first_token_seconds",
		Help:      "Time from starting an inference stream to the first non-empty output delta by model.",
		Buckets:   prometheus.ExponentialBuckets(0.005, 2, 18),
	}, []string{"model"})

	metricInferenceOutputTokensPerSecond = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "output_tokens_per_second",
		Help:      "Output tokens per second from the first non-empty output delta until an inference stream completes by model.",
		Buckets:   prometheus.ExponentialBuckets(1, 2, 20),
	}, []string{"model"})

	metricInferenceRequestDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "request_duration_seconds",
		Help:      "Inference request duration by model.",
		Buckets:   prometheus.ExponentialBuckets(0.005, 2, 18),
	}, []string{"model"})

	metricInferenceRequestResults = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "request_results_total",
		Help:      "Inference request results by model and outcome.",
	}, []string{"model", "outcome"})

	metricInferenceRequestsInFlight = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "requests_in_flight",
		Help:      "Number of inference requests currently admitted for processing.",
	})

	metricInferenceRequestsRejected = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "requests_rejected_total",
		Help:      "Inference requests rejected by admission limit.",
	}, []string{"reason"})

	metricInferenceInputTokensPerRequest = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "input_tokens_per_request",
		Help:      "Input tokens observed per chat or Responses request by model.",
		Buckets:   prometheus.ExponentialBuckets(1, 2, 20),
	}, []string{"model"})

	metricInferenceOutputTokensPerRequest = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "output_tokens_per_request",
		Help:      "Output tokens observed per chat or Responses request by model.",
		Buckets:   prometheus.ExponentialBuckets(1, 2, 20),
	}, []string{"model"})

	metricInferenceCachedInputRatio = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "cached_input_ratio",
		Help:      "Ratio of cached input tokens to total input tokens per chat or Responses request by model.",
		Buckets:   []float64{0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.97, 0.98, 0.99, 0.995, 0.9975, 0.999, 0.9995, 1},
	}, []string{"model"})

	metricInferenceTimeToLastToken = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "time_to_last_token_seconds",
		Help:      "Time from starting an inference stream to its last observed output delta by model.",
		Buckets:   prometheus.ExponentialBuckets(0.005, 2, 18),
	}, []string{"model"})

	metricInferenceOutputDeltaInterval = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "ucloud_im",
		Subsystem: "inference",
		Name:      "output_delta_interval_seconds",
		Help:      "Time between non-empty output deltas in an inference stream by model.",
		Buckets:   prometheus.ExponentialBuckets(0.001, 2, 17),
	}, []string{"model"})
)

func inferenceReportChatStreamingMetrics(model string, startedAt time.Time, firstTokenAt time.Time, lastTokenAt time.Time, completedAt time.Time, outputTokens int) {
	if firstTokenAt.IsZero() {
		return
	}

	metricInferenceTimeToFirstToken.WithLabelValues(model).Observe(firstTokenAt.Sub(startedAt).Seconds())
	metricInferenceTimeToLastToken.WithLabelValues(model).Observe(lastTokenAt.Sub(startedAt).Seconds())

	if outputTokens <= 0 {
		return
	}
	outputDuration := completedAt.Sub(firstTokenAt).Seconds()
	if outputDuration <= 0 {
		return
	}
	metricInferenceOutputTokensPerSecond.WithLabelValues(model).Observe(float64(outputTokens) / outputDuration)
}

func inferenceReportChatRequestMetrics(model string, outcome string, startedAt time.Time, completedAt time.Time) {
	metricInferenceRequestDuration.WithLabelValues(model).Observe(completedAt.Sub(startedAt).Seconds())
	metricInferenceRequestResults.WithLabelValues(model, outcome).Inc()
}

func inferenceReportChatUsageMetrics(model string, cachedTokens int, inputTokens int, outputTokens int) {
	totalInputTokens := cachedTokens + inputTokens
	metricInferenceInputTokensPerRequest.WithLabelValues(model).Observe(float64(totalInputTokens))
	metricInferenceOutputTokensPerRequest.WithLabelValues(model).Observe(float64(outputTokens))
	if totalInputTokens > 0 {
		metricInferenceCachedInputRatio.WithLabelValues(model).Observe(float64(cachedTokens) / float64(totalInputTokens))
	}
}

// Provider conversion
// ---------------------------------------------------------------------------------------------------------------------
// This section adapts internal catalog values to provider API values.

func inferenceServerBase() string {
	scheme, _, ok := strings.Cut(cfg.Provider.Hosts.SelfPublic.ToURL(), "://")
	if !ok {
		scheme = "https"
	}

	return fmt.Sprintf("%s://%s/v1", scheme, shared.ServiceConfig.Compute.Inference.Authority)
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
