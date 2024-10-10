package controller

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"

	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

// This file contains an in-memory representation of active jobs. Services can use this to manage and track active jobs.
// This database is intended to be used by the server mode. The server must initialize the system by invoking
// InitJobDatabase().

var activeJobs = make(map[string]*orc.Job)
var activeJobsMutex = sync.Mutex{}

type trackRequestType struct {
	JobId      string
	ProviderId string
}

var trackRequest = ipc.NewCall[trackRequestType, util.Empty]("jobdb.track")
var retrieveRequest = ipc.NewCall[string, *orc.Job]("jobdb.retrieve")

type JobUpdateBatch struct {
	entries               []orc.ResourceUpdateAndId[orc.JobUpdate]
	trackedDirtyStates    map[string]orc.JobState
	trackedNodeAllocation map[string][]string
	failed                bool
}

func InitJobDatabase() {
	if cfg.Mode != cfg.ServerModeServer {
		return
	}

	activeJobsMutex.Lock()
	defer activeJobsMutex.Unlock()

	trackRequest.Handler(func(r *ipc.Request[trackRequestType]) ipc.Response[util.Empty] {
		job, err := orc.RetrieveJob(r.Payload.JobId,
			orc.BrowseJobsFlags{
				IncludeParameters:  true,
				IncludeApplication: true,
				IncludeProduct:     true,
			},
		)

		if !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
				Payload:    util.Empty{},
			}
		}

		if err == nil {
			job.ProviderGeneratedId = r.Payload.ProviderId
			TrackNewJob(job)
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
			Payload:    util.Empty{},
		}
	})

	retrieveRequest.Handler(func(r *ipc.Request[string]) ipc.Response[*orc.Job] {
		job, ok := RetrieveJob(r.Payload)
		if !ok {
			return ipc.Response[*orc.Job]{
				StatusCode: http.StatusNotFound,
				Payload:    nil,
			}
		}

		if !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
			return ipc.Response[*orc.Job]{
				StatusCode: http.StatusNotFound,
				Payload:    nil,
			}
		}
		return ipc.Response[*orc.Job]{
			StatusCode: http.StatusOK,
			Payload:    job,
		}
	})

	fetchAllJobs(orc.JobStateInQueue)
	fetchAllJobs(orc.JobStateSuspended)
	fetchAllJobs(orc.JobStateRunning)
}

func TrackNewJob(job orc.Job) {
	// NOTE(Dan): The job is supposed to be copied into this function. Do not change it to accept a pointer.

	if cfg.Mode == cfg.ServerModeServer {
		activeJobsMutex.Lock()
		activeJobs[job.Id] = &job
		activeJobsMutex.Unlock()

		trackJobUpdateServer(&job)
	}

	if cfg.Mode == cfg.ServerModeUser {
		_, _ = trackRequest.Invoke(trackRequestType{job.Id, job.ProviderGeneratedId})
	}
}

func trackJobUpdateServer(job *orc.Job) {
	jsonified, _ := json.Marshal(job)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_jobs(job_id, created_by, project_id, product_id, product_category, state, resource)
				values (:job_id, :created_by, :project_id, :product_id, :product_category, :state, :resource)
				on conflict (job_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id,
					product_id = excluded.product_id,
					product_category = excluded.product_category,
					state = excluded.state
			`,
			db.Params{
				"job_id":           job.Id,
				"created_by":       job.Owner.CreatedBy,
				"project_id":       job.Owner.Project,
				"product_id":       job.Specification.Product.Id,
				"product_category": job.Specification.Product.Category,
				"resource":         string(jsonified),
				"state":            job.Status.State,
			},
		)
	})
}

func RetrieveJob(jobId string) (*orc.Job, bool) {
	if cfg.Mode == cfg.ServerModeServer {
		activeJobsMutex.Lock()
		job, ok := activeJobs[jobId]
		activeJobsMutex.Unlock()
		return job, ok
	} else {
		job, err := retrieveRequest.Invoke(jobId)
		return job, err == nil
	}
}

func BeginJobUpdates() *JobUpdateBatch {
	activeJobsMutex.Lock()
	return &JobUpdateBatch{
		trackedDirtyStates:    make(map[string]orc.JobState),
		trackedNodeAllocation: make(map[string][]string),
	}
}

func (b *JobUpdateBatch) GetJobs() map[string]*orc.Job {
	return activeJobs
}

func (b *JobUpdateBatch) AddUpdate(update orc.ResourceUpdateAndId[orc.JobUpdate]) {
	b.entries = append(b.entries, update)
	if len(b.entries) >= 100 {
		b.flush()
	}
}

func (b *JobUpdateBatch) TrackState(jobId string, state orc.JobState, status util.Option[string]) bool {
	b.trackedDirtyStates[jobId] = state

	currentJob, ok := activeJobs[jobId]
	if ok && currentJob.Status.State != state {
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

		return true
	}
	return false
}

func (b *JobUpdateBatch) TrackAssignedNodes(jobId string, nodes []string) {
	b.trackedNodeAllocation[jobId] = nodes
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
	if b.failed {
		return
	}

	if len(b.entries) == 0 {
		return
	}

	err := orc.UpdateJobs(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{
		Items: b.entries,
	})

	if err != nil {
		b.failed = true
		log.Warn("Failed to flush updated jobs: %v", err)
		return
	}

	var nodeAllocJobIds []string
	var nodeAllocNodeIds []string

	for _, entry := range b.entries {
		u := &entry.Update
		job, ok := activeJobs[entry.Id]

		if !ok {
			continue
		}

		if u.ExpectedState.IsSet() {
			if u.ExpectedState.Get() != job.Status.State {
				continue
			}
		}

		if u.ExpectedDifferentState.Get() {
			if u.ExpectedDifferentState.Get() && job.Status.State == u.State.Get() {
				continue
			}
		}

		if u.State.IsSet() {
			job.Status.State = u.State.Get()

			if u.State.Get() == orc.JobStateRunning {
				job.Status.StartedAt.Set(fnd.Timestamp(time.Now()))

				alloc := job.Specification.TimeAllocation
				if alloc.IsSet() {
					job.Status.ExpiresAt.Set(fnd.Timestamp(
						job.Status.StartedAt.Get().Time().Add(time.Duration(alloc.Get().ToMillis()) * time.Millisecond),
					))
				}
			}
		}

		if u.NewTimeAllocation.IsSet() {
			newDuration := orc.SimpleDurationFromMillis(u.NewTimeAllocation.Get())
			job.Specification.TimeAllocation.Set(newDuration)
			if job.Status.StartedAt.IsSet() {
				job.Status.ExpiresAt.Set(fnd.Timestamp(
					job.Status.StartedAt.Get().Time().Add(time.Duration(newDuration.ToMillis()) * time.Millisecond),
				))
			}
		}

		if u.AllowRestart.IsSet() {
			job.Status.AllowRestart = u.AllowRestart.Get()
		}

		if u.OutputFolder.IsSet() {
			job.Output.OutputFolder.Set(u.OutputFolder.Get())
		}

		// NOTE(Dan): Mounts are not tracked here since we do not know if they should be added or not. Instead, we rely
		// on UCloud/Core sending the job one more time which should cause TrackNewJob() to be called again (correcting
		// the state).

		job.Updates = append(job.Updates, *u)

		alloc, ok := b.trackedNodeAllocation[job.Id]
		if ok {
			for _, node := range alloc {
				nodeAllocJobIds = append(nodeAllocJobIds, job.Id)
				nodeAllocNodeIds = append(nodeAllocNodeIds, node)
			}
		}

		trackJobUpdateServer(job)
		if u.State.IsSet() && u.State.Get().IsFinal() {
			delete(activeJobs, entry.Id)
		}
	}

	if len(nodeAllocJobIds) > 0 {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					with
						node_allocations_data as (
							select
								unnest(cast(:job_ids as text[])) as job_id,
								unnest(cast(:nodes as text[])) as node
						),
						node_allocations as (
							select
								job_id,
								array_agg(node) as nodes
							from
								node_allocations_data
							group by job_id
						)
					update tracked_jobs j
					set 
						allocated_nodes = a.nodes
					from
						node_allocations a
					where
						j.job_id = a.job_id
			    `,
				db.Params{
					"job_ids": nodeAllocJobIds,
					"nodes":   nodeAllocNodeIds,
				},
			)
		})
	}

	b.entries = b.entries[:0]
}

func (b *JobUpdateBatch) End() {
	defer activeJobsMutex.Unlock()

	if b.failed {
		return
	}

	var jobsWithUnknownState []string
	now := time.Now()
	for activeJobId, job := range activeJobs {
		if job.Status.State == orc.JobStateInQueue && now.Sub(job.CreatedAt.Time()) < 5*time.Minute {
			// Do not immediately terminated jobs that we do not get an update for, it might not be on the list yet.
			// If they do not turn up within 5 minutes, consider it failed.
			continue
		}

		_, ok := b.trackedDirtyStates[activeJobId]
		if !ok {
			jobsWithUnknownState = append(jobsWithUnknownState, activeJobId)
		}
	}

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

func JobsListServer() []*orc.Job {
	var result []*orc.Job
	activeJobsMutex.Lock()
	for _, job := range activeJobs {
		result = append(result, job)
	}
	defer activeJobsMutex.Unlock()
	return result
}
