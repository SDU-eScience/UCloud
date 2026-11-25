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
	InferenceTextGeneration            InferenceCapability = "TextGeneration"
	InferenceTextToImage               InferenceCapability = "TextToImage"
	InferenceImageToImage              InferenceCapability = "ImageToImage"
	InferenceTextToVideo               InferenceCapability = "TextToVideo"
	InferenceImageToVideo              InferenceCapability = "ImageToVideo"
	InferenceSpeechToText              InferenceCapability = "SpeechToText"
	InferenceSpeechToTextTimestamps    InferenceCapability = "SpeechToTextTimestamps"
	InferenceSpeechToTextSpeakerLabels InferenceCapability = "SpeechToTextSpeakerLabels"
	InferenceVision                    InferenceCapability = "Vision"
	InferenceVideoVision               InferenceCapability = "VideoVision"
	InferenceAudio                     InferenceCapability = "Audio"
)

type InferenceModel struct {
	Name                   string                       `json:"name"`
	Title                  string                       `json:"title"`
	Capabilities           []InferenceCapability        `json:"capabilities"`
	DefaultFor             []InferenceCapability        `json:"defaultFor"`
	ReasoningEfforts       []InferenceModelOption       `json:"reasoningEfforts,omitempty"`
	DefaultReasoningEffort string                       `json:"defaultReasoningEffort,omitempty"`
	PricePerMillion        InferencePricing             `json:"pricePerMillion"`
	PricePerMegaPixel      int64                        `json:"pricePerMegaPixel"`
	PricePerAudioMinute    int64                        `json:"pricePerAudioMinute"`
	PricePerVideoSecond    map[InferenceVideoTier]int64 `json:"pricePerVideoSecond,omitempty"`
	VideoResolutions       []InferenceVideoResolution   `json:"videoResolutions,omitempty"`
	DefaultVideoTier       InferenceVideoTier           `json:"defaultVideoTier,omitempty"`
	FileUrlRoot            string                       `json:"fileUrlRoot,omitempty"`
	Endpoint               InferenceEndpoint            `json:"endpoint"`
	Availability           InferenceAvailability        `json:"availability"`
	ContextWindow          *int                         `json:"contextWindow,omitempty"`
	ChatSettings           InferenceChatSettings        `json:"chatSettings"`
	Page                   *InferenceModelPage          `json:"page,omitempty"`
}

type InferenceVideoTier string

const (
	InferenceVideoTier360p  InferenceVideoTier = "360p"
	InferenceVideoTier480p  InferenceVideoTier = "480p"
	InferenceVideoTier540p  InferenceVideoTier = "540p"
	InferenceVideoTier720p  InferenceVideoTier = "720p"
	InferenceVideoTier768p  InferenceVideoTier = "768p"
	InferenceVideoTier1080p InferenceVideoTier = "1080p"
	InferenceVideoTier2k    InferenceVideoTier = "2k"
	InferenceVideoTier4k    InferenceVideoTier = "4k"
)

type InferenceVideoResolution struct {
	Width  int                `json:"width"`
	Height int                `json:"height"`
	Tier   InferenceVideoTier `json:"tier"`
}

type InferenceModelOption struct {
	Name  string `json:"name"`
	Value string `json:"value"`
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
	DisableTools        bool    `json:"disableTools"`
}

type InferencePricing struct {
	CachedInput int64 `json:"cachedInput"`
	Input       int64 `json:"input"`
	Output      int64 `json:"output"`
}

const InferencePriceScale int64 = 1_000_000

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

type InferenceUsageDailyWeight struct {
	Owner  string  `json:"owner"`
	Day    string  `json:"day"`
	Weight float64 `json:"weight"`
}

type InferenceUsageBreakdownInternalRequest struct {
	ProviderId      string                      `json:"providerId"`
	StartDate       string                      `json:"startDate"`
	EndDate         string                      `json:"endDate"`
	ExclusiveOwners []string                    `json:"exclusiveOwners"`
	DailyWeights    []InferenceUsageDailyWeight `json:"dailyWeights"`
}

type InferenceUsageBreakdownProviderRequest struct {
	StartDate       string                      `json:"startDate"`
	EndDate         string                      `json:"endDate"`
	ExclusiveOwners []string                    `json:"exclusiveOwners"`
	DailyWeights    []InferenceUsageDailyWeight `json:"dailyWeights"`
}

type InferenceUsageBreakdownDailyRow struct {
	Day                         string `json:"day"`
	Model                       string `json:"model"`
	ModelTitle                  string `json:"modelTitle"`
	CachedInputTokens           int64  `json:"cachedInputTokens"`
	InputTokens                 int64  `json:"inputTokens"`
	OutputTokens                int64  `json:"outputTokens"`
	ImageCount                  int64  `json:"imageCount"`
	ImagePixels                 int64  `json:"imagePixels"`
	AudioMilliseconds           int64  `json:"audioMilliseconds"`
	VideoSeconds                int64  `json:"videoSeconds"`
	EstimatedCachedInputCost    int64  `json:"estimatedCachedInputCost"`
	EstimatedInputCost          int64  `json:"estimatedInputCost"`
	EstimatedOutputCost         int64  `json:"estimatedOutputCost"`
	EstimatedImageCost          int64  `json:"estimatedImageCost"`
	EstimatedAudioCost          int64  `json:"estimatedAudioCost"`
	EstimatedVideoCost          int64  `json:"estimatedVideoCost"`
	ModelPriced                 bool   `json:"modelPriced"`
	VideoUsedDefaultTierPricing bool   `json:"videoUsedDefaultTierPricing"`
}

type InferenceUsageBreakdownResponse struct {
	Rows                   []InferenceUsageBreakdownDailyRow `json:"rows"`
	CoFundedWorkspaceCount int                               `json:"coFundedWorkspaceCount"`
}

type InferenceUsageSeedCharge struct {
	Owner        string `json:"owner"`
	Day          string `json:"day"`
	MicroCredits int64  `json:"microCredits"`
}

type InferenceUsageBreakdownSeedInternalRequest struct {
	ProviderId string                     `json:"providerId"`
	Charges    []InferenceUsageSeedCharge `json:"charges"`
}

type InferenceUsageBreakdownSeedProviderRequest struct {
	Charges []InferenceUsageSeedCharge `json:"charges"`
}

var InferenceUsageBreakdownInternal = rpc.Call[InferenceUsageBreakdownInternalRequest, InferenceUsageBreakdownResponse]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "usageBreakdown",
	Roles:       rpc.RolesService,
}

var InferenceUsageBreakdownSeedInternal = rpc.Call[InferenceUsageBreakdownSeedInternalRequest, util.Empty]{
	BaseContext: inferenceBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "seedUsageBreakdown",
	Roles:       rpc.RolesService,
}

var InferenceUpdateBenchmarksProvider = rpc.Call[InferenceUpdateBenchmarksProviderRequest, util.Empty]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "benchmarks",
	Roles:       rpc.RolesService,
}

var InferenceUsageBreakdownProvider = rpc.Call[InferenceUsageBreakdownProviderRequest, InferenceUsageBreakdownResponse]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "usageBreakdown",
	Roles:       rpc.RolesService,
}

var InferenceUsageBreakdownSeedProvider = rpc.Call[InferenceUsageBreakdownSeedProviderRequest, util.Empty]{
	BaseContext: inferenceProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "seedUsageBreakdown",
	Roles:       rpc.RolesService,
}
