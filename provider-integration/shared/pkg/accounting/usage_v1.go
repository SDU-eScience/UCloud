package apm

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type UsageRetrieveRequest struct {
	Start uint64 `json:"start"`
	End   uint64 `json:"end"`
}

type UsageRetrieveResponse struct {
	Reports []UsageReport `json:"reports"`
}

type UsageReport struct {
	AccountingModel  AccountingModel            `json:"accountingModel"`
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

type UsageReportAbsoluteChildrenDataPoint struct {
	Timestamp fnd.Timestamp `json:"timestamp"`
	Usage     int64         `json:"usage"`

	// Not present for local use. "Other" means projects not named. Otherwise, the content will be a project ID.
	Child util.Option[string] `json:"child"`
}

type UsageReportAbsoluteDataPoint struct {
	Timestamp             fnd.Timestamp `json:"timestamp"`
	Usage                 int64         `json:"usage"`
	UtilizationPercent100 float64       `json:"utilizationPercent100"`
}

type UsageReportOverTime struct {
	Delta            []UsageReportDeltaDataPoint            `json:"delta"`
	Absolute         []UsageReportAbsoluteDataPoint         `json:"absolute"`
	ChildrenAbsolute []UsageReportAbsoluteChildrenDataPoint `json:"childrenAbsolute"`
}

type UsageReportKpis struct {
	QuotaAtStart       int64 `json:"quotaAtStart"`
	ActiveQuotaAtStart int64 `json:"activeQuotaAtStart"`
	QuotaAtEnd         int64 `json:"quotaAtEnd"`
	ActiveQuotaAtEnd   int64 `json:"activeQuotaAtEnd"`

	MaxUsableAtStart  int64 `json:"maxUsableAtStart"`
	MaxUsableAtEnd    int64 `json:"maxUsableAtEnd"`
	LocalUsageAtStart int64 `json:"localUsageAtStart"`
	LocalUsageAtEnd   int64 `json:"localUsageAtEnd"`
	TotalUsageAtStart int64 `json:"totalUsageAtStart"`
	TotalUsageAtEnd   int64 `json:"totalUsageAtEnd"`

	TotalAllocatedAtStart int64 `json:"totalAllocatedAtStart"`
	TotalAllocatedAtEnd   int64 `json:"totalAllocatedAtEnd"`

	NextMeaningfulExpiration util.Option[fnd.Timestamp] `json:"nextMeaningfulExpiration"`
}

type UsageReportSubProjectHealth struct {
	SubProjectCount int `json:"subProjectCount"`

	Ok            int `json:"ok"`
	UnderUtilized int `json:"underUtilized"`
	AtRisk        int `json:"atRisk"`

	Idle int `json:"idle"`
}

var UsageRetrieve = rpc.Call[UsageRetrieveRequest, UsageRetrieveResponse]{
	BaseContext: usageContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}
