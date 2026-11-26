package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const jobUsageBaseContext = "jobs"

type JobUsageCategoryFilter struct {
	Provider        string                `json:"provider"`
	Category        string                `json:"category"`
	ExclusiveOwners []string              `json:"exclusiveOwners"`
	DailyWeights    []JobUsageDailyWeight `json:"dailyWeights"`
}

type JobUsageDailyWeight struct {
	Owner  string  `json:"owner"`
	Day    string  `json:"day"`
	Weight float64 `json:"weight"`
}

type JobUsageBreakdownInternalRequest struct {
	Start         fnd.Timestamp            `json:"start"`
	End           fnd.Timestamp            `json:"end"`
	BucketStart   fnd.Timestamp            `json:"bucketStart"`
	BucketSeconds int64                    `json:"bucketSeconds"`
	BucketCount   int                      `json:"bucketCount"`
	Categories    []JobUsageCategoryFilter `json:"categories"`
}

type JobUsageBreakdownDailyRow struct {
	Day            string  `json:"day"`
	Application    string  `json:"application"`
	Product        string  `json:"product"`
	Provider       string  `json:"provider"`
	Outcome        string  `json:"outcome"`
	JobCount       float64 `json:"jobCount"`
	EstimatedHours float64 `json:"estimatedHours"`
	RequestedHours float64 `json:"requestedHours"`
}

type JobUsageBucketQueueRow struct {
	BucketIndex     int     `json:"bucketIndex"`
	SampleCount     int64   `json:"sampleCount"`
	AvgQueueSeconds float64 `json:"avgQueueSeconds"`
	P50QueueSeconds float64 `json:"p50QueueSeconds"`
	P90QueueSeconds float64 `json:"p90QueueSeconds"`
}

type JobUsageProductQueueRow struct {
	Product         string  `json:"product"`
	Provider        string  `json:"provider"`
	Cpu             int     `json:"cpu"`
	Gpu             int     `json:"gpu"`
	MemoryInGigs    int     `json:"memoryInGigs"`
	SampleCount     int64   `json:"sampleCount"`
	AvgQueueSeconds float64 `json:"avgQueueSeconds"`
	P50QueueSeconds float64 `json:"p50QueueSeconds"`
	P90QueueSeconds float64 `json:"p90QueueSeconds"`
	MaxQueueSeconds float64 `json:"maxQueueSeconds"`
}

type JobUsageBreakdownResponse struct {
	DailyRows              []JobUsageBreakdownDailyRow `json:"dailyRows"`
	BucketQueueRows        []JobUsageBucketQueueRow    `json:"bucketQueueRows"`
	ProductQueueRows       []JobUsageProductQueueRow   `json:"productQueueRows"`
	CoFundedWorkspaceCount int                         `json:"coFundedWorkspaceCount"`
}

type JobUsageSeedCharge struct {
	Provider     string  `json:"provider"`
	Category     string  `json:"category"`
	IsGpu        bool    `json:"isGpu"`
	Owner        string  `json:"owner"`
	Day          string  `json:"day"`
	ComputeHours float64 `json:"computeHours"`
}

type JobUsageBreakdownSeedInternalRequest struct {
	Charges []JobUsageSeedCharge `json:"charges"`
}

var JobUsageBreakdownInternal = rpc.Call[JobUsageBreakdownInternalRequest, JobUsageBreakdownResponse]{
	BaseContext: jobUsageBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "usageBreakdown",
	Roles:       rpc.RolesService,
}

var JobUsageBreakdownSeedInternal = rpc.Call[JobUsageBreakdownSeedInternalRequest, util.Empty]{
	BaseContext: jobUsageBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "seedUsageBreakdown",
	Roles:       rpc.RolesService,
}
