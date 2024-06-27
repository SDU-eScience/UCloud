package controller

import (
	"sync"
	"time"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

// This file contains an in-memory representation of active jobs. Services can use this to manage and track active jobs.
// This database is intended to be used by the server mode. The server must initialize the system by invoking
// InitJobDatabase().

var activeJobs map[string]*orc.Job
var activeJobsMutex = sync.Mutex{}

type JobUpdateBatch struct {
	entries                      []orc.ResourceUpdateAndId[orc.JobUpdate]
	trackedDirtyStates           map[string]orc.JobState
	jobIdsToRemoveFromActiveList []string
}

func InitJobDatabase() {
	activeJobsMutex.Lock()
	defer activeJobsMutex.Unlock()

	fetchAllJobs(orc.JobStateInQueue)
	fetchAllJobs(orc.JobStateSuspended)
	fetchAllJobs(orc.JobStateRunning)
}

func TrackNewJob(job orc.Job) {
	activeJobsMutex.Lock()
	defer activeJobsMutex.Unlock()

	// NOTE(Dan): A copy is intended here.
	activeJobs[job.Id] = &job
}

func BeginJobUpdates() *JobUpdateBatch {
	activeJobsMutex.Lock()
	return &JobUpdateBatch{
		trackedDirtyStates: make(map[string]orc.JobState),
	}
}

func (b *JobUpdateBatch) AddUpdate(update orc.ResourceUpdateAndId[orc.JobUpdate]) {
	b.entries = append(b.entries, update)
	if len(b.entries) >= 100 {
		b.flush()
	}
}

func (b *JobUpdateBatch) TrackState(jobId string, state orc.JobState, status util.Option[string]) {
	b.trackedDirtyStates[jobId] = state

	currentJob, ok := activeJobs[jobId]
	if !ok || currentJob.Status.State != state {
		message := ""
		if status.IsSet() {
			message = status.Get()
		} else {
			message = b.jobUpdateMessage(state)
		}

		b.AddUpdate(orc.ResourceUpdateAndId[orc.JobUpdate]{
			Id: jobId,
			Update: orc.JobUpdate{
				State:  util.OptValue(state),
				Status: util.OptValue(message),
			},
		})
	}
}

func (b *JobUpdateBatch) jobUpdateMessage(state orc.JobState) string {
	switch state {
	case orc.JobStateInQueue:
		return "Your job is currently in the queue."
	case orc.JobStateRunning:
		return "Your job is now running."
	case orc.JobStateSuccess:
		return "Your job has successfully completed."
	case orc.JobStateFailure:
		return "Your job has failed."
	case orc.JobStateExpired:
		return "Your job has expired."
	case orc.JobStateSuspended:
		return "Your job has been temporarily suspended and can be turned on again later."
	default:
		return "Unknown job state. This should not happen: " + string(state)
	}
}

func (b *JobUpdateBatch) flush() {
	if len(b.entries) == 0 {
		return
	}

	for _, entry := range b.entries {
		newState := entry.Update.State
		if newState.IsSet() && newState.Get().IsFinal() {
			b.jobIdsToRemoveFromActiveList = append(b.jobIdsToRemoveFromActiveList, entry.Id)
		}
	}
}

func (b *JobUpdateBatch) End() {
	defer activeJobsMutex.Unlock()

	var jobsWithUnknownState []string
	for activeJobId, _ := range activeJobs {
		_, ok := b.trackedDirtyStates[activeJobId]
		if !ok {
			jobsWithUnknownState = append(jobsWithUnknownState, activeJobId)
		}
	}

	now := time.Now()
	for _, jobIdToTerminate := range jobsWithUnknownState {
		terminationState := orc.JobStateSuccess

		job, _ := activeJobs[jobIdToTerminate]
		expiresAt := job.Status.ExpiresAt
		if expiresAt.IsSet() && now.After(expiresAt.Get().Time()) {
			terminationState = orc.JobStateExpired
		}

		b.AddUpdate(orc.ResourceUpdateAndId[orc.JobUpdate]{
			Id: jobIdToTerminate,
			Update: orc.JobUpdate{
				State:  util.OptValue(terminationState),
				Status: util.OptValue(b.jobUpdateMessage(terminationState)),
			},
		})
	}

	b.flush()
}

func fetchAllJobs(state orc.JobState) {
	next := ""
	for {
		page, err := orc.BrowseJobs(next, orc.BrowseJobsFlags{
			FilterState:        util.OptValue(state),
			IncludeParameters:  true,
			IncludeApplication: true,
			IncludeProduct:     true,
		})

		if err != nil {
			log.Warn("Failed to fetch jobs: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			job := &page.Items[i]
			activeJobs[job.Id] = job
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}
