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
	PeakUsage                int64                       `json:"peakUsage"`
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
	PeakUsage                int64                       `json:"peakUsage"`
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
	HasData           bool                       `json:"hasData"`
	UnitGroupKey      string                     `json:"unitGroupKey"`
	Title             string                     `json:"title"`
	AccountingModel   AccountingModel            `json:"accountingModel"`
	ProductType       ProductType                `json:"productType"`
	UnitAndFrequency  AccountingUnitAndFrequency `json:"unitAndFrequency"`
	ProductsCovered   []ProductCategory          `json:"productsCovered"`
	BucketDurationMs  int64                      `json:"bucketDurationMs"`
	ValidFrom         fnd.Timestamp              `json:"validFrom"`
	ValidUntil        fnd.Timestamp              `json:"validUntil"`
	Kpis              UsageDetailKpis            `json:"kpis"`
	Timeline          []UsageTimelineBucket      `json:"timeline"`
	DailyActivity     []UsageDailyActivityPoint  `json:"dailyActivity"`
	Events            []UsageLifecycleEvent      `json:"events"`
	SubProjectSummary UsageSubProjectSummary     `json:"subProjectSummary"`
	Children          []UsageChildRow            `json:"children"`
}

type UsageDetailKpis struct {
	PeriodUsage               int64                       `json:"periodUsage"`
	PeriodLocalUsage          int64                       `json:"periodLocalUsage"`
	PeriodChildrenUsage       int64                       `json:"periodChildrenUsage"`
	ActiveUsageAtStart        int64                       `json:"activeUsageAtStart"`
	ActiveUsageAtEnd          int64                       `json:"activeUsageAtEnd"`
	ActiveQuotaAtStart        int64                       `json:"activeQuotaAtStart"`
	ActiveQuotaAtEnd          int64                       `json:"activeQuotaAtEnd"`
	RetiredUsageAtStart       int64                       `json:"retiredUsageAtStart"`
	RetiredUsageAtEnd         int64                       `json:"retiredUsageAtEnd"`
	TotalUsageAtStart         int64                       `json:"totalUsageAtStart"`
	TotalUsageAtEnd           int64                       `json:"totalUsageAtEnd"`
	QuotaAtStart              int64                       `json:"quotaAtStart"`
	QuotaAtEnd                int64                       `json:"quotaAtEnd"`
	LocalUsageAtStart         int64                       `json:"localUsageAtStart"`
	LocalUsageAtEnd           int64                       `json:"localUsageAtEnd"`
	MaxUsableAtEnd            int64                       `json:"maxUsableAtEnd"`
	ActiveAllocatedAtEnd      int64                       `json:"activeAllocatedAtEnd"`
	TotalAllocatedAtEnd       int64                       `json:"totalAllocatedAtEnd"`
	PeakUsage                 int64                       `json:"peakUsage"`
	AvgUsage                  int64                       `json:"avgUsage"`
	UpstreamCapped            bool                        `json:"upstreamCapped"`
	NextMeaningfulExpiration  util.Option[fnd.Timestamp]  `json:"nextMeaningfulExpiration"`
	NextQuotaDrop             util.Option[UsageQuotaDrop] `json:"nextQuotaDrop"`
	DownstreamActiveAllocated int64                       `json:"downstreamActiveAllocated"`
}

type UsageSeriesValue struct {
	Key    string `json:"key"`
	Change int64  `json:"change"`
	Usage  int64  `json:"usage"`
}

type UsageTimelineBucket struct {
	Timestamp        fnd.Timestamp      `json:"timestamp"`
	EndTimestamp     fnd.Timestamp      `json:"endTimestamp"`
	UsageChange      int64              `json:"usageChange"`
	LocalUsageChange int64              `json:"localUsageChange"`
	FlowShift        int64              `json:"flowShift"`
	ActiveUsage      int64              `json:"activeUsage"`
	RetiredUsage     int64              `json:"retiredUsage"`
	TotalUsage       int64              `json:"totalUsage"`
	LocalUsage       int64              `json:"localUsage"`
	ActiveQuota      int64              `json:"activeQuota"`
	Quota            int64              `json:"quota"`
	MaxUsable        int64              `json:"maxUsable"`
	PeakUsage        int64              `json:"peakUsage"`
	ByProduct        []UsageSeriesValue `json:"byProduct"`
	ByChild          []UsageSeriesValue `json:"byChild"`
}

type UsageDailyActivityPoint struct {
	Timestamp   fnd.Timestamp `json:"timestamp"`
	UsageChange int64         `json:"usageChange"`
}

type UsageLifecycleEvent struct {
	Timestamp  fnd.Timestamp `json:"timestamp"`
	Type       string        `json:"type"`
	QuotaDelta int64         `json:"quotaDelta"`
	Count      int           `json:"count"`
	ProductKey string        `json:"productKey"`
}

type UsageHistogramBucket struct {
	Key              string `json:"key"`
	Label            string `json:"label"`
	Count            int    `json:"count"`
	TotalPeriodUsage int64  `json:"totalPeriodUsage"`
	TotalActiveQuota int64  `json:"totalActiveQuota"`
}

type UsageSubProjectSummary struct {
	TotalCount         int                    `json:"totalCount"`
	SubAllocatorCount  int                    `json:"subAllocatorCount"`
	UtilizationBuckets []UsageHistogramBucket `json:"utilizationBuckets"`
}

type UsageSubAllocatorAggregate struct {
	ActiveDownstreamCount     int     `json:"activeDownstreamCount"`
	DownstreamActiveAllocated int64   `json:"downstreamActiveAllocated"`
	ReallocatedPercent        float64 `json:"reallocatedPercent"`
	DirectUsage               int64   `json:"directUsage"`
	DownstreamUsage           int64   `json:"downstreamUsage"`
}

type UsageChildRow struct {
	Child              string                                  `json:"child"`
	IsLocal            bool                                    `json:"isLocal"`
	IsSubAllocator     bool                                    `json:"isSubAllocator"`
	PeriodUsage        int64                                   `json:"periodUsage"`
	CurrentUsage       int64                                   `json:"currentUsage"`
	PeakUsage          int64                                   `json:"peakUsage"`
	ActiveUsage        int64                                   `json:"activeUsage"`
	ActiveQuota        int64                                   `json:"activeQuota"`
	ActiveQuotaKnown   bool                                    `json:"activeQuotaKnown"`
	Quota              int64                                   `json:"quota"`
	QuotaKnown         bool                                    `json:"quotaKnown"`
	RetiredUsage       int64                                   `json:"retiredUsage"`
	TotalUsage         int64                                   `json:"totalUsage"`
	UtilizationPercent float64                                 `json:"utilizationPercent"`
	UtilizationBucket  string                                  `json:"utilizationBucket"`
	NextExpiration     util.Option[fnd.Timestamp]              `json:"nextExpiration"`
	NextQuotaDrop      util.Option[UsageQuotaDrop]             `json:"nextQuotaDrop"`
	SubAllocator       util.Option[UsageSubAllocatorAggregate] `json:"subAllocator"`
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
