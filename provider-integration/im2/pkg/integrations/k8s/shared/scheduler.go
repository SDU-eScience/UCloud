package shared

import (
	"fmt"
	"maps"
	"slices"
	"strings"
	"sync"

	apm "ucloud.dk/shared/pkg/accounting"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type JobReplicaState struct {
	Id     string
	Rank   int
	State  orc.JobState
	Node   util.Option[string]
	Status util.Option[string]
}

type JobTracker interface {
	AddUpdate(id string, update orc.JobUpdate)
	TrackState(state JobReplicaState) bool
	RequestCleanup(id string)
}

var scheduleLock = sync.Mutex{}
var scheduledEntries = map[string]*orc.Job{}
var scheduledEntryOrder []string

var scheduleRemoveFromQueueLock sync.Mutex
var scheduleRemoveFromQueue []string

func RemoveFromQueue(jobId string) {
	scheduleRemoveFromQueueLock.Lock()
	scheduleRemoveFromQueue = append(scheduleRemoveFromQueue, jobId)
	scheduleRemoveFromQueueLock.Unlock()
}

func SwapScheduleRemoveFromQueue() []string {
	scheduleRemoveFromQueueLock.Lock()
	result := scheduleRemoveFromQueue
	scheduleRemoveFromQueue = nil
	scheduleRemoveFromQueueLock.Unlock()
	return result
}

func RequestSchedule(entry *orc.Job) {
	if entry == nil {
		return
	}

	scheduleLock.Lock()
	if _, ok := scheduledEntries[entry.Id]; !ok {
		scheduledEntryOrder = append(scheduledEntryOrder, entry.Id)
	}
	scheduledEntries[entry.Id] = entry
	scheduleLock.Unlock()
}

func SwapScheduleQueue() []*orc.Job {
	scheduleLock.Lock()
	if len(scheduledEntryOrder) == 0 {
		scheduleLock.Unlock()
		return nil
	}

	result := make([]*orc.Job, 0, len(scheduledEntryOrder))
	for _, jobId := range scheduledEntryOrder {
		entry, ok := scheduledEntries[jobId]
		if ok {
			result = append(result, entry)
		}
	}

	scheduledEntries = map[string]*orc.Job{}
	scheduledEntryOrder = nil
	scheduleLock.Unlock()
	return result
}

type SchedulerDimensions struct {
	CpuMillis     int
	MemoryInBytes int
	Resources     map[string]int
}

func (dims SchedulerDimensions) Clone() SchedulerDimensions {
	result := dims
	result.Resources = maps.Clone(dims.Resources)
	return result
}

func (dims SchedulerDimensions) String() string {
	resourceEntries := make([]string, 0, len(dims.Resources))
	resourceKeys := slices.Sorted(maps.Keys(dims.Resources))
	for _, key := range resourceKeys {
		resourceEntries = append(resourceEntries, fmt.Sprintf("%s=%d", key, dims.Resources[key]))
	}

	return fmt.Sprintf(
		"{CpuMillis=%v,MemoryInBytes=%v,Resources={%s}}",
		dims.CpuMillis,
		dims.MemoryInBytes,
		strings.Join(resourceEntries, ","),
	)
}

func (dims *SchedulerDimensions) Add(other SchedulerDimensions) {
	dims.CpuMillis += other.CpuMillis
	dims.MemoryInBytes += other.MemoryInBytes
	if len(other.Resources) > 0 {
		if dims.Resources == nil {
			dims.Resources = map[string]int{}
		}

		for key, value := range other.Resources {
			dims.Resources[key] += value
		}
	}
}

func (dims *SchedulerDimensions) Subtract(other SchedulerDimensions) {
	dims.CpuMillis -= other.CpuMillis
	dims.MemoryInBytes -= other.MemoryInBytes
	if len(other.Resources) > 0 {
		if dims.Resources == nil {
			dims.Resources = map[string]int{}
		}

		for key, value := range other.Resources {
			dims.Resources[key] -= value
		}
	}
}

func (dims *SchedulerDimensions) AddImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{Resources: maps.Clone(dims.Resources)}
	result.CpuMillis = dims.CpuMillis + other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes + other.MemoryInBytes
	if len(other.Resources) > 0 {
		if result.Resources == nil {
			result.Resources = map[string]int{}
		}

		for key, value := range other.Resources {
			result.Resources[key] += value
		}
	}
	return result
}

func (dims *SchedulerDimensions) SubtractImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{Resources: maps.Clone(dims.Resources)}
	result.CpuMillis = dims.CpuMillis - other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes - other.MemoryInBytes
	if len(other.Resources) > 0 {
		if result.Resources == nil {
			result.Resources = map[string]int{}
		}

		for key, value := range other.Resources {
			result.Resources[key] -= value
		}
	}
	return result
}

func (dims *SchedulerDimensions) Satisfies(request SchedulerDimensions) bool {
	if dims.CpuMillis < request.CpuMillis || dims.MemoryInBytes < request.MemoryInBytes {
		return false
	}

	if dims.Resources == nil {
		dims.Resources = map[string]int{}
	}

	for key, value := range request.Resources {
		if dims.Resources[key] < value {
			return false
		}
	}

	return true
}

type JobDimensionMapper func(job *orc.Job) SchedulerDimensions

var jobDimensionMappers = map[string]JobDimensionMapper{}

func RegisterJobDimensionMapper(categoryName string, mapper JobDimensionMapper) {
	jobDimensionMappers[categoryName] = mapper
}

func JobDimensions(job *orc.Job) SchedulerDimensions {
	prod := &job.Status.ResolvedProduct.Value
	mapper, hasMapper := jobDimensionMappers[prod.Category.Name]
	if hasMapper {
		return mapper(job)
	} else {
		return JobDimensionsFromProductOnly(prod)
	}
}

func JobDimensionsFromProductOnly(prod *apm.ProductV2) SchedulerDimensions {
	nodeCat, _ := NodeCategoryAndConfiguration(prod)
	dims := SchedulerDimensions{
		CpuMillis:     NodeCpuMillisNormalizedWithReserved(prod),
		MemoryInBytes: prod.MemoryInGigs * (1000 * 1000 * 1000),
		Resources:     map[string]int{},
	}

	if prod.Gpu > 0 {
		gpuType := GpuResourceTypeOrDefault(nodeCat.GpuResourceType)
		dims.Resources = map[string]int{gpuType: prod.Gpu}
	}

	return dims
}
