package controller

import (
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/ipc"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// This file contains an in-memory representation of active jobs. Services can use this to manage and track active jobs.
// This database is intended to be used by the server mode. The server must initialize the system by invoking
// InitJobDatabase().

var activeJobs = make(map[string]*orc.Job)
var activeJobsMutex = sync.RWMutex{}

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
	results               JobUpdateBatchResults
}

func InitJobDatabase() {
	if !RunsServerCode() {
		return
	}

	trackRequest.Handler(func(r *ipc.Request[trackRequestType]) ipc.Response[util.Empty] {
		request := orc.JobsControlRetrieveRequest{Id: r.Payload.JobId}
		request.IncludeParameters = true
		request.IncludeApplication = true
		request.IncludeProduct = true
		job, err := orc.JobsControlRetrieve.Invoke(request)

		if !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(job.Resource), r.Uid) {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
				Payload:    util.Empty{},
			}
		}

		if err == nil {
			job.ProviderGeneratedId = r.Payload.ProviderId
			JobTrackNew(job)
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
			Payload:    util.Empty{},
		}
	})

	retrieveRequest.Handler(func(r *ipc.Request[string]) ipc.Response[*orc.Job] {
		job, ok := JobRetrieve(r.Payload)
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

	activeJobsMutex.Lock()
	jobFetchAll(orc.JobStateInQueue)
	jobFetchAll(orc.JobStateSuspended)
	jobFetchAll(orc.JobStateRunning)
	activeJobsMutex.Unlock()

	initIpDatabase()
	initIngressDatabase()
	initLicenseDatabase()
	initPrivateNetworkDatabase()

	jobsLoadSessions()
	gateway.Resume()

	// Job metrics
	go func() {
		for util.IsAlive {
			var jobsRunning float64 = 0
			var jobsInQueue float64 = 0
			var jobsSuspended float64 = 0

			activeJobsMutex.RLock()
			for _, job := range activeJobs {
				switch job.Status.State {
				case orc.JobStateRunning:
					jobsRunning++
				case orc.JobStateInQueue:
					jobsInQueue++
				case orc.JobStateSuspended:
					jobsSuspended++
				}
			}
			activeJobsMutex.RUnlock()

			metricJobsRunning.Set(jobsRunning)
			metricJobsInQueue.Set(jobsInQueue)
			metricJobsSuspended.Set(jobsSuspended)

			time.Sleep(5 * time.Second)
		}
	}()
}

func JobTrackNew(job orc.Job) {
	timer := util.NewTimer()
	// NOTE(Dan): The job is supposed to be copied into this function. Do not change it to accept a pointer.

	// Automatically assign timestamps to all updates that do not have one.
	timer.Mark()
	for i := 0; i < len(job.Updates); i++ {
		update := &job.Updates[i]
		if update.Timestamp.UnixMilli() <= 0 {
			update.Timestamp = fnd.Timestamp(time.Now())
		}
	}
	metricTrackTransform.Observe(timer.Mark().Seconds())

	if RunsServerCode() {
		timer.Mark()
		refreshRoutes := false
		activeJobsMutex.Lock()
		activeJobs[job.Id] = &job

		if job.Status.State.IsFinal() {
			refreshRoutes = true
		}
		activeJobsMutex.Unlock()
		metricTrackUpdateMemory.Observe(timer.Mark().Seconds())

		jobTrackUpdateServer(&job)

		if refreshRoutes {
			timer.Mark()
			jobRoutesRefresh()
			metricTrackRouteRefresh.Observe(timer.Mark().Seconds())
		}
	} else if RunsUserCode() {
		_, _ = trackRequest.Invoke(trackRequestType{job.Id, job.ProviderGeneratedId})
	}
}

func jobTrackUpdateServer(job *orc.Job) {
	timer := util.NewTimer()
	if len(job.Updates) > 64 {
		var truncatedJobs []orc.JobUpdate
		for i := len(job.Updates) - 64; i < len(job.Updates); i++ {
			truncatedJobs = append(truncatedJobs, job.Updates[i])
		}
		job.Updates = truncatedJobs
	}

	jsonified, _ := json.Marshal(job)
	metricTrackDatabaseMarshal.Observe(timer.Mark().Seconds())

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
				"project_id":       job.Owner.Project.Value,
				"product_id":       job.Specification.Product.Id,
				"product_category": job.Specification.Product.Category,
				"resource":         string(jsonified),
				"state":            job.Status.State,
			},
		)
	})
	metricTrackDatabaseQuery.Observe(timer.Mark().Seconds())
}

var (
	metricTrackTransform       = metricJobTrack("transform")
	metricTrackUpdateMemory    = metricJobTrack("update_memory")
	metricTrackDatabaseQuery   = metricJobTrack("database_query")
	metricTrackDatabaseMarshal = metricJobTrack("database_marshal")
	metricTrackRouteRefresh    = metricJobTrack("route_refresh")
)

func metricJobTrack(region string) prometheus.Summary {
	return promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      fmt.Sprintf("job_track_%s_seconds", region),
		Help:      fmt.Sprintf("Summary of the duration (in seconds) it takes to run the region '%s' of JobTrackNew.", region),
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})
}

func JobRetrieve(jobId string) (*orc.Job, bool) {
	if RunsServerCode() {
		activeJobsMutex.RLock()
		job, ok := activeJobs[jobId]
		activeJobsMutex.RUnlock()

		if ok {
			return job, ok
		} else {
			request := orc.JobsControlRetrieveRequest{Id: jobId}
			request.IncludeParameters = true
			request.IncludeApplication = true
			request.IncludeProduct = true
			job, err := orc.JobsControlRetrieve.Invoke(request)

			if err == nil {
				JobTrackNew(job)
				return &job, true
			} else {
				return nil, false
			}
		}
	} else {
		job, err := retrieveRequest.Invoke(jobId)
		return job, err == nil
	}
}

func JobRetrieveAll() map[string]*orc.Job {
	result := map[string]*orc.Job{}
	activeJobsMutex.RLock()
	for _, job := range activeJobs {
		if !job.Status.State.IsFinal() {
			copied := *job
			result[copied.Id] = &copied
		}
	}
	activeJobsMutex.RUnlock()
	return result
}

func JobUpdatesBegin() *JobUpdateBatch {
	return &JobUpdateBatch{
		trackedDirtyStates:    make(map[string]orc.JobState),
		trackedNodeAllocation: make(map[string][]string),
	}
}

func (b *JobUpdateBatch) AddUpdate(update orc.ResourceUpdateAndId[orc.JobUpdate]) {
	b.entries = append(b.entries, update)
	if update.Update.State.Present {
		switch update.Update.State.Value {
		case orc.JobStateRunning:
			b.results.NormalStart = append(b.results.NormalStart, update.Id)

		case orc.JobStateExpired, orc.JobStateSuccess, orc.JobStateFailure:
			b.results.NormalTermination = append(b.results.NormalTermination, update.Id)

		case orc.JobStateSuspended:
			b.results.NormalSuspension = append(b.results.NormalSuspension, update.Id)
		}
	}
	if len(b.entries) >= 100 {
		b.flush()
	}
}

func (b *JobUpdateBatch) TrackState(jobId string, state orc.JobState, status util.Option[string]) bool {
	b.trackedDirtyStates[jobId] = state

	activeJobsMutex.RLock()
	currentJob, ok := activeJobs[jobId]
	var currentState orc.JobState
	if ok {
		currentState = currentJob.Status.State
	}
	activeJobsMutex.RUnlock()

	if ok && currentState != state {
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
	newNodes, _ := b.trackedNodeAllocation[jobId]
	for _, node := range nodes {
		if !slices.Contains(newNodes, node) {
			newNodes = append(newNodes, node)
		}
	}

	b.trackedNodeAllocation[jobId] = newNodes
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
		return "Your machine is currently powered off."
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

	_, err := orc.JobsControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{
		Items: b.entries,
	})

	if err != nil {
		b.failed = true
		log.Warn("Failed to flush updated jobs: %v", err)
		return
	}

	var nodeAllocJobIds []string
	var nodeAllocNodeIds []string

	activeJobsMutex.Lock()
	for _, entry := range b.entries {
		u := &entry.Update
		u.Timestamp = fnd.Timestamp(time.Now())
		job, ok := activeJobs[entry.Id]

		expectationsMet := ok
		if ok {
			if u.ExpectedState.IsSet() {
				if u.ExpectedState.Get() != job.Status.State {
					expectationsMet = false
				}
			}

			if u.ExpectedDifferentState.GetOrDefault(false) {
				if job.Status.State == u.State.Get() {
					expectationsMet = false
				}
			}
		}

		if ok && expectationsMet {
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
			// on UCloud/Core sending the job one more time which should cause JobTrackNew() to be called again (correcting
			// the state).

			job.Updates = append(job.Updates, *u)

			alloc, ok := b.trackedNodeAllocation[job.Id]
			if ok {
				for _, node := range alloc {
					nodeAllocJobIds = append(nodeAllocJobIds, job.Id)
					nodeAllocNodeIds = append(nodeAllocNodeIds, node)
				}
			}

			jobTrackUpdateServer(job)
		}
	}
	activeJobsMutex.Unlock()

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

type JobUpdateBatchResults struct {
	TerminatedDueToUnknownState []string
	NormalStart                 []string
	NormalTermination           []string
	NormalSuspension            []string
}

// End flushes out any remaining job updates and return a list of jobs which were terminated due to missing
// tracking data.
func (b *JobUpdateBatch) End() JobUpdateBatchResults {
	if b.failed {
		return JobUpdateBatchResults{}
	}

	var jobsWithUnknownState []string
	now := time.Now()

	activeJobsMutex.RLock()
	for activeJobId, job := range activeJobs {
		if job.Status.State.IsFinal() {
			continue
		}

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
	activeJobsMutex.RUnlock()

	for _, jobIdToTerminate := range jobsWithUnknownState {
		terminationState := orc.JobStateSuccess

		var id string
		var state orc.JobState
		var expiresAt util.Option[fnd.Timestamp]
		ok := false

		{
			activeJobsMutex.RLock()
			job, hasJob := activeJobs[jobIdToTerminate]
			if hasJob {
				ok = true
				expiresAt = job.Status.ExpiresAt
				state = job.Status.State
				id = job.Id
			}
			activeJobsMutex.RUnlock()
		}

		if ok {
			if expiresAt.IsSet() && now.After(expiresAt.Get().Time()) {
				terminationState = orc.JobStateExpired
			}

			if state != terminationState {
				b.AddUpdate(orc.ResourceUpdateAndId[orc.JobUpdate]{
					Id: jobIdToTerminate,
					Update: orc.JobUpdate{
						State:  util.OptValue(terminationState),
						Status: util.OptValue(b.jobUpdateMessage(terminationState)),
					},
				})
			}

			b.results.TerminatedDueToUnknownState = append(b.results.TerminatedDueToUnknownState, id)
		}
	}

	b.flush()
	return b.results
}

type JobMessage struct {
	JobId   string
	Message string
}

func JobTrackMessage(messages []JobMessage) *util.HttpError {
	if len(messages) == 0 {
		return nil
	}

	var updates []orc.ResourceUpdateAndId[orc.JobUpdate]
	for _, message := range messages {
		update := orc.JobUpdate{
			Status: util.OptValue(message.Message),
		}

		updates = append(updates, orc.ResourceUpdateAndId[orc.JobUpdate]{
			Id:     message.JobId,
			Update: update,
		})
	}

	return JobTrackRawUpdates(updates)
}

func JobTrackRawUpdates(updates []orc.ResourceUpdateAndId[orc.JobUpdate]) *util.HttpError {
	if len(updates) == 0 {
		return nil
	}

	timer := util.NewTimer()

	for _, message := range updates {
		timer.Mark()
		job, ok := JobRetrieve(message.Id)
		metricUpdateRetrieve.Observe(timer.Mark().Seconds())
		if ok {
			timer.Mark()
			copied := *job
			copied.Updates = append(job.Updates, message.Update)
			if message.Update.NewTimeAllocation.Present {
				copied.Specification.TimeAllocation = util.OptValue(
					orc.SimpleDurationFromMillis(message.Update.NewTimeAllocation.Value),
				)
			}
			metricUpdateTransform.Observe(timer.Mark().Seconds())
			JobTrackNew(copied)
			metricUpdateTrack.Observe(timer.Mark().Seconds())
		}
	}

	timer.Mark()
	_, err := orc.JobsControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{
		Items: updates,
	})
	metricUpdateApi.Observe(timer.Mark().Seconds())

	return err
}

var (
	metricUpdateRetrieve  = metricJobUpdate("retrieve_job")
	metricUpdateTransform = metricJobUpdate("transform")
	metricUpdateTrack     = metricJobUpdate("track")
	metricUpdateApi       = metricJobUpdate("api")
)

func metricJobUpdate(region string) prometheus.Summary {
	return promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud_im",
		Subsystem: "jobs",
		Name:      fmt.Sprintf("job_update_%s_seconds", region),
		Help:      fmt.Sprintf("Summary of the duration (in seconds) it takes to run the region '%s' of IsJobLocked.", region),
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})
}

func jobFetchAll(state orc.JobState) {
	log.Info("Fetching all jobs in state %v", state)
	jobs := db.NewTx(func(tx *db.Transaction) []*orc.Job {
		rows := db.Select[struct{ Resource string }](
			tx,
			`
				select resource from tracked_jobs where state = :state
		    `,
			db.Params{
				"state": string(state),
			},
		)

		var result []*orc.Job
		for _, row := range rows {
			var j orc.Job
			err := json.Unmarshal([]byte(row.Resource), &j)
			if err == nil {
				result = append(result, &j)
			}
		}
		return result
	})
	log.Info("Found %v in %v", len(jobs), state)

	for _, item := range jobs {
		activeJobs[item.Id] = item
	}
}

func JobsListServer() []*orc.Job {
	var result []*orc.Job
	activeJobsMutex.RLock()
	for _, job := range activeJobs {
		if !job.Status.State.IsFinal() {
			result = append(result, job)
		}
	}
	activeJobsMutex.RUnlock()
	return result
}
