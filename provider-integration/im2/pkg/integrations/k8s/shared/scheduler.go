package shared

import (
	"fmt"
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
var scheduledEntries []*orc.Job = nil

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
	scheduleLock.Lock()
	scheduledEntries = append(scheduledEntries, entry)
	scheduleLock.Unlock()
}

func SwapScheduleQueue() []*orc.Job {
	scheduleLock.Lock()
	result := scheduledEntries
	scheduledEntries = nil
	scheduleLock.Unlock()
	return result
}

type SchedulerDimensions struct {
	CpuMillis     int
	MemoryInBytes int
	Gpu           int
}

func (dims SchedulerDimensions) String() string {
	return fmt.Sprintf("{CpuMillis=%v,MemoryInBytes=%v,Gpu=%v}", dims.CpuMillis, dims.MemoryInBytes, dims.Gpu)
}

func (dims *SchedulerDimensions) Add(other SchedulerDimensions) {
	dims.CpuMillis += other.CpuMillis
	dims.MemoryInBytes += other.MemoryInBytes
	dims.Gpu += other.Gpu
}

func (dims *SchedulerDimensions) Subtract(other SchedulerDimensions) {
	dims.CpuMillis -= other.CpuMillis
	dims.MemoryInBytes -= other.MemoryInBytes
	dims.Gpu -= other.Gpu
}

func (dims *SchedulerDimensions) AddImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{}
	result.CpuMillis = dims.CpuMillis + other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes + other.MemoryInBytes
	result.Gpu = dims.Gpu + other.Gpu
	return result
}

func (dims *SchedulerDimensions) SubtractImmutable(other SchedulerDimensions) SchedulerDimensions {
	result := SchedulerDimensions{}
	result.CpuMillis = dims.CpuMillis - other.CpuMillis
	result.MemoryInBytes = dims.MemoryInBytes - other.MemoryInBytes
	result.Gpu = dims.Gpu - other.Gpu
	return result
}

func (dims *SchedulerDimensions) Satisfies(request SchedulerDimensions) bool {
	return dims.CpuMillis >= request.CpuMillis && dims.Gpu >= request.Gpu && dims.MemoryInBytes >= request.MemoryInBytes
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
	dims := SchedulerDimensions{
		CpuMillis:     NodeCpuMillisReserved(prod),
		MemoryInBytes: prod.MemoryInGigs * (1000 * 1000 * 1000),
		Gpu:           prod.Gpu,
	}

	return dims
}
