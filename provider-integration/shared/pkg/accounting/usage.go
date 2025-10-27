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

type UsageRetrieveRequest struct {
	Start uint64 `json:"start"`
	End   uint64 `json:"end"`
}

type UsageRetrieveResponse struct {
	Reports []UsageReport `json:"reports"`
}

type UsageReport struct {
	Title            string                     `json:"title"`
	ProductsCovered  []ProductCategoryIdV2      `json:"productsCovered"`
	UnitAndFrequency AccountingUnitAndFrequency `json:"unitAndFrequency"`

	ValidFrom        fnd.Timestamp               `json:"validFrom"`
	ValidUntil       fnd.Timestamp               `json:"validUntil"`
	Kpis             UsageReportKpis             `json:"kpis"`
	SubProjectHealth UsageReportSubProjectHealth `json:"subProjectHealth"`
	UsageOverTime    UsageReportOverTime         `json:"usageOverTime"`
}

type UsageReportDeltaDataPoint struct {
	Timestamp fnd.Timestamp `json:"timestamp"`
	Change    int64         `json:"change"`

	// Not present for local use. "Other" means projects not named. Otherwise, the content will be a project ID.
	Child util.Option[string] `json:"child"`
}

type UsageReportAbsoluteDataPoint struct {
	Timestamp             fnd.Timestamp `json:"timestamp"`
	Usage                 int64         `json:"usage"`
	UtilizationPercent100 float64       `json:"utilizationPercent100"`
}

type UsageReportOverTime struct {
	Delta    []UsageReportDeltaDataPoint    `json:"delta"`
	Absolute []UsageReportAbsoluteDataPoint `json:"absolute"`
}

type UsageReportKpis struct {
	QuotaAtStart       int64 `json:"quotaAtStart"`       // At creation: combined quota from all allocations which contribute quota
	ActiveQuotaAtStart int64 `json:"activeQuotaAtStart"` // At creation: combined quota from all allocations that are active
	QuotaAtEnd         int64 `json:"quotaAtEnd"`         // Latest (in period): combined quota from all allocations which contribute quota
	ActiveQuotaAtEnd   int64 `json:"activeQuotaAtEnd"`   // Latest (in period): combined quota from all allocations that are active

	MaxUsableAtStart  int64 `json:"maxUsableAtStart"`
	MaxUsableAtEnd    int64 `json:"maxUsableAtEnd"`
	LocalUsageAtStart int64 `json:"localUsageAtStart"`
	LocalUsageAtEnd   int64 `json:"localUsageAtEnd"`
	TotalUsageAtStart int64 `json:"totalUsageAtStart"`
	TotalUsageAtEnd   int64 `json:"totalUsageAtEnd"`
	// Active usage can be derived by determining retired usage from the inactive allocations

	TotalAllocatedAtStart int64 `json:"totalAllocatedAtStart"`
	TotalAllocatedAtEnd   int64 `json:"totalAllocatedAtEnd"`

	NextMeaningfulExpiration util.Option[fnd.Timestamp] `json:"nextMeaningfulExpiration"`
}

type UsageReportSubProjectHealth struct {
	SubProjectCount int `json:"subProjectCount"`

	// Ok, UnderUtilized, AtRisk will sum to SubProjectCount

	Ok            int `json:"ok"`
	UnderUtilized int `json:"underUtilized"`
	AtRisk        int `json:"atRisk"`

	// 0 >= Idle <= SubProjectCount
	Idle int `json:"idle"`
}

var UsageRetrieve = rpc.Call[UsageRetrieveRequest, UsageRetrieveResponse]{
	BaseContext: usageContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}
