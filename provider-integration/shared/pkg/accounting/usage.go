package apm

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const usageContext = "accounting/v2/usage"

type UsageGenConfig struct {
	Days               int   `json:"days"`
	BreadthPerLevel    []int `json:"breadthPerLevel"`
	Seed               int64 `json:"seed"`
	CheckpointInterval int   `json:"checkpointInterval"`
	Expiration         bool  `json:"expiration"`
	ReportingInterval  int   `json:"reportingInterval"`
}

var UsageGenerate = rpc.Call[UsageGenConfig, util.Empty]{
	BaseContext: usageContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "generate",
	Roles:       rpc.RoleAdmin,
}

type UsageOverviewRequest struct {
	Start               uint64              `json:"start"`
	End                 uint64              `json:"end"`
	InitialUnitGroupKey util.Option[string] `json:"initialUnitGroupKey"`
}

type UsageOverviewResponse struct {
	HasData       bool                             `json:"hasData"`
	UnitGroups    []UsageUnitGroup                 `json:"unitGroups"`
	InitialDetail util.Option[UsageDetailResponse] `json:"initialDetail"`
}

type UsageSparklinePoint struct {
	Timestamp fnd.Timestamp `json:"timestamp"`
	Value     int64         `json:"value"`
	Quota     int64         `json:"quota"`
}

type UsageQuotaDrop struct {
	Timestamp   fnd.Timestamp `json:"timestamp"`
	QuotaBefore int64         `json:"quotaBefore"`
	QuotaAfter  int64         `json:"quotaAfter"`
	WouldExceed bool          `json:"wouldExceed"`
}

type UsageUnitGroup struct {
	Key                      string                      `json:"key"`
	Title                    string                      `json:"title"`
	AccountingModel          AccountingModel             `json:"accountingModel"`
	ProductType              ProductType                 `json:"productType"`
	UnitAndFrequency         AccountingUnitAndFrequency  `json:"unitAndFrequency"`
	PeriodUsage              int64                       `json:"periodUsage"`
	ActiveUsage              int64                       `json:"activeUsage"`
	ActiveQuota              int64                       `json:"activeQuota"`
	RetiredUsage             int64                       `json:"retiredUsage"`
	Quota                    int64                       `json:"quota"`
	TotalUsage               int64                       `json:"totalUsage"`
	NextMeaningfulExpiration util.Option[fnd.Timestamp]  `json:"nextMeaningfulExpiration"`
	NextQuotaDrop            util.Option[UsageQuotaDrop] `json:"nextQuotaDrop"`
	Sparkline                []UsageSparklinePoint       `json:"sparkline"`
	Products                 []UsageProductSummary       `json:"products"`
}

type UsageProductSummary struct {
	Category                 ProductCategory             `json:"category"`
	PeriodUsage              int64                       `json:"periodUsage"`
	ActiveUsage              int64                       `json:"activeUsage"`
	ActiveQuota              int64                       `json:"activeQuota"`
	RetiredUsage             int64                       `json:"retiredUsage"`
	Quota                    int64                       `json:"quota"`
	TotalUsage               int64                       `json:"totalUsage"`
	NextMeaningfulExpiration util.Option[fnd.Timestamp]  `json:"nextMeaningfulExpiration"`
	NextQuotaDrop            util.Option[UsageQuotaDrop] `json:"nextQuotaDrop"`
	Sparkline                []UsageSparklinePoint       `json:"sparkline"`
}

type UsageDetailRequest struct {
	Start          uint64                `json:"start"`
	End            uint64                `json:"end"`
	UnitGroupKey   string                `json:"unitGroupKey"`
	Products       []ProductCategoryIdV2 `json:"products"`
	PinnedChildren []string              `json:"pinnedChildren"`
}

type UsageDetailResponse struct {
	HasData            bool                                 `json:"hasData"`
	UnitGroupKey       string                               `json:"unitGroupKey"`
	Title              string                               `json:"title"`
	AccountingModel    AccountingModel                      `json:"accountingModel"`
	ProductType        ProductType                          `json:"productType"`
	UnitAndFrequency   AccountingUnitAndFrequency           `json:"unitAndFrequency"`
	ProductsCovered    []ProductCategory                    `json:"productsCovered"`
	BucketDurationMs   int64                                `json:"bucketDurationMs"`
	ValidFrom          fnd.Timestamp                        `json:"validFrom"`
	ValidUntil         fnd.Timestamp                        `json:"validUntil"`
	Kpis               UsageDetailKpis                      `json:"kpis"`
	Timeline           []UsageTimelineBucket                `json:"timeline"`
	DailyActivity      []UsageDailyActivityPoint            `json:"dailyActivity"`
	Events             []UsageLifecycleEvent                `json:"events"`
	Children           []UsageChildRow                      `json:"children"`
	InferenceBreakdown util.Option[UsageInferenceBreakdown] `json:"inferenceBreakdown"`
	JobBreakdown       util.Option[UsageJobBreakdown]       `json:"jobBreakdown"`
}

type UsageJobOutcomeCounts struct {
	Completed int64 `json:"completed"`
	Failed    int64 `json:"failed"`
	Expired   int64 `json:"expired"`
	Active    int64 `json:"active"`
}

type UsageJobSliceBucketValue struct {
	Application    string                `json:"application"`
	ProductKey     string                `json:"productKey"`
	EstimatedHours float64               `json:"estimatedHours"`
	RequestedHours float64               `json:"requestedHours"`
	Outcomes       UsageJobOutcomeCounts `json:"outcomes"`
}

type UsageJobTimelineBucket struct {
	Timestamp       fnd.Timestamp              `json:"timestamp"`
	EndTimestamp    fnd.Timestamp              `json:"endTimestamp"`
	QueueSamples    int64                      `json:"queueSamples"`
	AvgQueueSeconds float64                    `json:"avgQueueSeconds"`
	P50QueueSeconds float64                    `json:"p50QueueSeconds"`
	P90QueueSeconds float64                    `json:"p90QueueSeconds"`
	BySlice         []UsageJobSliceBucketValue `json:"bySlice"`
}

type UsageJobApplicationSummary struct {
	Application    string                `json:"application"`
	EstimatedHours float64               `json:"estimatedHours"`
	Outcomes       UsageJobOutcomeCounts `json:"outcomes"`
	TopProductKeys []string              `json:"topProductKeys"`
}

type UsageJobProductSummary struct {
	Product         string  `json:"product"`
	Provider        string  `json:"provider"`
	Cpu             int     `json:"cpu"`
	Gpu             int     `json:"gpu"`
	MemoryInGigs    int     `json:"memoryInGigs"`
	Jobs            int64   `json:"jobs"`
	EstimatedHours  float64 `json:"estimatedHours"`
	RequestedHours  float64 `json:"requestedHours"`
	QueueSamples    int64   `json:"queueSamples"`
	AvgQueueSeconds float64 `json:"avgQueueSeconds"`
	P50QueueSeconds float64 `json:"p50QueueSeconds"`
	P90QueueSeconds float64 `json:"p90QueueSeconds"`
	MaxQueueSeconds float64 `json:"maxQueueSeconds"`
}

type UsageJobBreakdown struct {
	Available              bool                         `json:"available"`
	BucketDurationMs       int64                        `json:"bucketDurationMs,omitempty"`
	CoFundedWorkspaceCount int                          `json:"coFundedWorkspaceCount"`
	Applications           []UsageJobApplicationSummary `json:"applications"`
	Products               []UsageJobProductSummary     `json:"products"`
	Timeline               []UsageJobTimelineBucket     `json:"timeline"`
}

type UsageInferenceCostBreakdown struct {
	CachedInput int64 `json:"cachedInput"`
	Input       int64 `json:"input"`
	Output      int64 `json:"output"`
	Image       int64 `json:"image"`
	Audio       int64 `json:"audio"`
	Video       int64 `json:"video"`
}

type UsageInferenceTokenBreakdown struct {
	CachedInput int64 `json:"cachedInput"`
	Input       int64 `json:"input"`
	Output      int64 `json:"output"`
}

type UsageInferenceModelBucketValue struct {
	Model         string                       `json:"model"`
	EstimatedCost UsageInferenceCostBreakdown  `json:"estimatedCost"`
	Tokens        UsageInferenceTokenBreakdown `json:"tokens"`
}

type UsageInferenceTimelineBucket struct {
	Timestamp    fnd.Timestamp                    `json:"timestamp"`
	EndTimestamp fnd.Timestamp                    `json:"endTimestamp"`
	ByModel      []UsageInferenceModelBucketValue `json:"byModel"`
}

type UsageInferenceModelSummary struct {
	Model             string                       `json:"model"`
	Title             string                       `json:"title"`
	Priced            bool                         `json:"priced"`
	EstimatedCost     int64                        `json:"estimatedCost"`
	Tokens            UsageInferenceTokenBreakdown `json:"tokens"`
	ImageCount        int64                        `json:"imageCount"`
	ImagePixels       int64                        `json:"imagePixels"`
	AudioMilliseconds int64                        `json:"audioMilliseconds"`
	VideoSeconds      int64                        `json:"videoSeconds"`
}

type UsageInferenceBreakdown struct {
	Available              bool                           `json:"available"`
	BucketDurationMs       int64                          `json:"bucketDurationMs,omitempty"`
	CoFundedWorkspaceCount int                            `json:"coFundedWorkspaceCount"`
	HasUnpricedModels      bool                           `json:"hasUnpricedModels"`
	HasVideoEstimate       bool                           `json:"hasVideoEstimate"`
	Models                 []UsageInferenceModelSummary   `json:"models"`
	Timeline               []UsageInferenceTimelineBucket `json:"timeline"`
}

type UsageDetailKpis struct {
	PeriodUsage              int64                       `json:"periodUsage"`
	PeriodLocalUsage         int64                       `json:"periodLocalUsage"`
	PeriodChildrenUsage      int64                       `json:"periodChildrenUsage"`
	ActiveUsageAtEnd         int64                       `json:"activeUsageAtEnd"`
	ActiveQuotaAtEnd         int64                       `json:"activeQuotaAtEnd"`
	RetiredUsageAtEnd        int64                       `json:"retiredUsageAtEnd"`
	TotalUsageAtEnd          int64                       `json:"totalUsageAtEnd"`
	QuotaAtEnd               int64                       `json:"quotaAtEnd"`
	LocalUsageAtEnd          int64                       `json:"localUsageAtEnd"`
	MaxUsableAtEnd           int64                       `json:"maxUsableAtEnd"`
	ActiveAllocatedAtEnd     int64                       `json:"activeAllocatedAtEnd"`
	TotalAllocatedAtEnd      int64                       `json:"totalAllocatedAtEnd"`
	AvgUsage                 int64                       `json:"avgUsage"`
	UpstreamCapped           bool                        `json:"upstreamCapped"`
	NextMeaningfulExpiration util.Option[fnd.Timestamp]  `json:"nextMeaningfulExpiration"`
	NextQuotaDrop            util.Option[UsageQuotaDrop] `json:"nextQuotaDrop"`
}

type UsageSeriesValue struct {
	Key    string `json:"key"`
	Change int64  `json:"change"`
	Usage  int64  `json:"usage"`
}

type UsageTimelineBucket struct {
	Timestamp    fnd.Timestamp      `json:"timestamp"`
	EndTimestamp fnd.Timestamp      `json:"endTimestamp"`
	UsageChange  int64              `json:"usageChange"`
	FlowShift    int64              `json:"flowShift"`
	ActiveUsage  int64              `json:"activeUsage"`
	RetiredUsage int64              `json:"retiredUsage"`
	ActiveQuota  int64              `json:"activeQuota"`
	Quota        int64              `json:"quota"`
	MaxUsable    int64              `json:"maxUsable"`
	PeakUsage    int64              `json:"peakUsage"`
	ByProduct    []UsageSeriesValue `json:"byProduct"`
	ByChild      []UsageSeriesValue `json:"byChild"`
}

type UsageDailyActivityPoint struct {
	Timestamp   fnd.Timestamp `json:"timestamp"`
	UsageChange int64         `json:"usageChange"`
}

type UsageLifecycleEvent struct {
	Timestamp  fnd.Timestamp `json:"timestamp"`
	Type       string        `json:"type"`
	QuotaDelta int64         `json:"quotaDelta"`
}

type UsageSubAllocatorAggregate struct {
	DownstreamActiveAllocated int64 `json:"downstreamActiveAllocated"`
	DirectUsage               int64 `json:"directUsage"`
	DownstreamUsage           int64 `json:"downstreamUsage"`
}

type UsageQuotaExpiration struct {
	Timestamp fnd.Timestamp `json:"timestamp"`
	Quota     int64         `json:"quota"`
}

type UsageChildRow struct {
	Child               string                                  `json:"child"`
	IsLocal             bool                                    `json:"isLocal"`
	PeriodUsage         int64                                   `json:"periodUsage"`
	ActiveUsage         int64                                   `json:"activeUsage"`
	ActiveQuota         int64                                   `json:"activeQuota"`
	ActiveQuotaKnown    bool                                    `json:"activeQuotaKnown"`
	Quota               int64                                   `json:"quota"`
	QuotaKnown          bool                                    `json:"quotaKnown"`
	RetiredUsage        int64                                   `json:"retiredUsage"`
	TotalUsage          int64                                   `json:"totalUsage"`
	NextExpiration      util.Option[fnd.Timestamp]              `json:"nextExpiration"`
	NextQuotaDrop       util.Option[UsageQuotaDrop]             `json:"nextQuotaDrop"`
	UpcomingExpirations []UsageQuotaExpiration                  `json:"upcomingExpirations,omitempty"`
	SubAllocator        util.Option[UsageSubAllocatorAggregate] `json:"subAllocator"`
}

var UsageOverviewRetrieve = rpc.Call[UsageOverviewRequest, UsageOverviewResponse]{
	BaseContext: usageContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "overview",
	Roles:       rpc.RolesEndUser,
}

var UsageDetailRetrieve = rpc.Call[UsageDetailRequest, UsageDetailResponse]{
	BaseContext: usageContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "detail",
	Roles:       rpc.RolesEndUser,
}
