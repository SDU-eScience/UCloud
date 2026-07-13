package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
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

type InferencePlaygroundThread struct {
	Id        string `json:"id"`
	Title     string `json:"title"`
	UpdatedAt int64  `json:"updatedAt"`
}

type InferenceListPlaygroundThreadsRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
}

type InferenceListPlaygroundThreadsResponse struct {
	Threads []InferencePlaygroundThread `json:"threads"`
}

type InferenceCapability string

const (
	InferenceTextGeneration InferenceCapability = "TextGeneration"
	InferenceTextToImage    InferenceCapability = "TextToImage"
	InferenceSpeechToText   InferenceCapability = "SpeechToText"
	InferenceVision         InferenceCapability = "Vision"
	InferenceVideoVision    InferenceCapability = "VideoVision"
	InferenceAudio          InferenceCapability = "Audio"
)

type InferenceModel struct {
	Name            string                `json:"name"`
	Title           string                `json:"title"`
	TitleModelName  string                `json:"titleModelName"`
	Capabilities    []InferenceCapability `json:"capabilities"`
	PriceMultiplier InferencePricing      `json:"priceMultiplier"`
	Endpoint        InferenceEndpoint     `json:"endpoint"`
	Availability    InferenceAvailability `json:"availability"`
	ContextWindow   *int                  `json:"contextWindow,omitempty"`
	ChatSettings    InferenceChatSettings `json:"chatSettings"`
	Page            *InferenceModelPage   `json:"page,omitempty"`
}

type InferenceModelPage struct {
	ShortDescription string                      `json:"shortDescription,omitempty"`
	DocumentationUrl string                      `json:"documentationUrl,omitempty"`
	ReleaseDate      *fnd.Timestamp              `json:"releaseDate,omitempty"`
	About            InferenceModelPageAbout     `json:"about,omitempty"`
	BenchmarkScores  map[string]string           `json:"benchmarkScores,omitempty"`
	Datasheet        InferenceModelPageDatasheet `json:"datasheet,omitempty"`
}

type InferenceModelPageAbout struct {
	Description string                  `json:"description,omitempty"`
	Highlights  []string                `json:"highlights,omitempty"`
	KeyStats    []InferenceModelKeyStat `json:"keyStats,omitempty"`
}

type InferenceModelKeyStat struct {
	Label       string `json:"label"`
	Value       string `json:"value"`
	Description string `json:"description,omitempty"`
}

type InferenceModelPageDatasheet struct {
	Parameters          string `json:"parameters,omitempty"`
	ActivatedParameters string `json:"activatedParameters,omitempty"`
	Quantization        string `json:"quantization,omitempty"`
}

type InferenceBenchmark struct {
	Id             string   `json:"id"`
	Title          string   `json:"title"`
	Description    string   `json:"description,omitempty"`
	HigherIsBetter bool     `json:"higherIsBetter"`
	ModelNames     []string `json:"modelNames"`
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
	Models     []InferenceModel     `json:"models"`
	Benchmarks []InferenceBenchmark `json:"benchmarks"`
	IsAdmin    bool                 `json:"isAdmin"`
	ProviderId string               `json:"providerId"`
	Server     string               `json:"server"`
}

type InferenceUpdateModelRequest struct {
	ProviderId util.Option[string] `json:"providerId"`
	OldName    string              `json:"oldName"`
	Model      InferenceModel      `json:"model"`
}

type InferenceUpdateBenchmarksRequest struct {
	ProviderId util.Option[string]  `json:"providerId"`
	Benchmarks []InferenceBenchmark `json:"benchmarks"`
}

var InferenceOpenPlayground = rpc.Call[InferenceOpenPlaygroundRequest, InferenceOpenPlaygroundResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesEndUser,
}

var InferenceListPlaygroundThreads = rpc.Call[InferenceListPlaygroundThreadsRequest, InferenceListPlaygroundThreadsResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "playgroundThreads",
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

var InferenceUpdateBenchmarks = rpc.Call[InferenceUpdateBenchmarksRequest, util.Empty]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "benchmarks",
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

type InferenceListPlaygroundThreadsProviderRequest struct {
	Owner ResourceOwner `json:"owner"`
}

var InferenceOpenPlaygroundProvider = rpc.Call[InferenceOpenPlaygroundProviderRequest, InferenceOpenPlaygroundProviderResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "openPlayground",
	Roles:       rpc.RolesService,
}

var InferenceListPlaygroundThreadsProvider = rpc.Call[InferenceListPlaygroundThreadsProviderRequest, InferenceListPlaygroundThreadsResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "listPlaygroundThreads",
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

type InferenceUpdateBenchmarksProviderRequest struct {
	Owner      ResourceOwner        `json:"owner"`
	Benchmarks []InferenceBenchmark `json:"benchmarks"`
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

var InferenceUpdateBenchmarksProvider = rpc.Call[InferenceUpdateBenchmarksProviderRequest, util.Empty]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "benchmarks",
	Roles:       rpc.RolesService,
}
