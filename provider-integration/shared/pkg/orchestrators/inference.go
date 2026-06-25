package orchestrators

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const inferenceBaseContext = "jobs/inference"

type InferenceOpenPlaygroundRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
}

type InferenceOpenPlaygroundResponse struct {
	ConnectTo    string `json:"connectTo"`
	SessionToken string `json:"sessionToken"`
}

type InferenceCapability string

const (
	InferenceTextGeneration InferenceCapability = "TextGeneration"
	InferenceTextToImage    InferenceCapability = "TextToImage"
	InferenceSpeechToText   InferenceCapability = "SpeechToText"
)

type InferenceModel struct {
	Name            string                `json:"name"`
	Title           string                `json:"title"`
	Capabilities    []InferenceCapability `json:"capabilities"`
	PriceMultiplier InferencePricing      `json:"priceMultiplier"`
	Endpoint        InferenceEndpoint     `json:"endpoint"`
	Availability    InferenceAvailability `json:"availability"`
	ContextWindow   *int                  `json:"contextWindow,omitempty"`
	ChatSettings    InferenceChatSettings `json:"chatSettings"`
}

type InferenceChatSettings struct {
	Temperature         float64 `json:"temperature"`
	TopP                float64 `json:"topP"`
	MaxCompletionTokens int     `json:"maxCompletionTokens"`
	SystemPrompt        *string `json:"systemPrompt,omitempty"`
}

type InferencePricing struct {
	CachedInput int `json:"cachedInput"`
	Input       int `json:"input"`
	Output      int `json:"output"`
}

type InferenceEndpoint struct {
	BasePath         string `json:"basePath"`
	BackendModelName string `json:"backendModelName"`
}

type InferenceAvailability struct {
	Public      bool     `json:"public"`
	AvailableTo []string `json:"availableTo"`
}

type InferenceListModelsRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
}

type InferenceListModelsResponse struct {
	Models     []InferenceModel `json:"models"`
	IsAdmin    bool             `json:"isAdmin"`
	ProviderId string           `json:"providerId"`
}

type InferenceUpdateModelRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
	OldName    string              `json:"oldName"`
	Model      InferenceModel      `json:"model"`
}

var InferenceOpenPlayground = rpc.Call[InferenceOpenPlaygroundRequest, InferenceOpenPlaygroundResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesEndUser,
}

var InferenceListModels = rpc.Call[InferenceListModelsRequest, InferenceListModelsResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "models",
	Roles:       rpc.RolesEndUser,
}

var InferenceUpdateModel = rpc.Call[InferenceUpdateModelRequest, util.Empty]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "model",
	Roles:       rpc.RolesEndUser,
}

const inferenceProviderBaseContext = "ucloud/" + rpc.ProviderPlaceholder + "/jobs/inference"

type InferenceOpenPlaygroundProviderRequest struct {
	Owner ResourceOwner `json:"owner"`
}

type InferenceOpenPlaygroundProviderResponse struct {
	ConnectTo    string `json:"connectTo"`
	SessionToken string `json:"sessionToken"`
}

var InferenceOpenPlaygroundProvider = rpc.Call[InferenceOpenPlaygroundProviderRequest, InferenceOpenPlaygroundProviderResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesService,
}

type InferenceListModelsProviderRequest struct {
	Owner ResourceOwner `json:"owner"`
}

type InferenceUpdateModelProviderRequest struct {
	Owner   ResourceOwner  `json:"owner"`
	OldName string         `json:"oldName"`
	Model   InferenceModel `json:"model"`
}

var InferenceListModelsProvider = rpc.Call[InferenceListModelsProviderRequest, InferenceListModelsResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "listModels",
	Roles:       rpc.RolesService,
}

var InferenceUpdateModelProvider = rpc.Call[InferenceUpdateModelProviderRequest, util.Empty]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "model",
	Roles:       rpc.RolesService,
}
