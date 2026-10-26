package orchestrator

import (
	"fmt"
	"net/http"
	"slices"
	"sync"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var jobMutationLocks [256]sync.Mutex

func jobMutationLock(jobId string) *sync.Mutex {
	canonical := fmt.Sprint(ResourceParseId(jobId))
	return &jobMutationLocks[orchestratorLockIndex(canonical, uint32(len(jobMutationLocks)))]
}

func jobResourceValues(spec orcapi.JobSpecification) []orcapi.AppParameterValue {
	values := slices.Clone(spec.Resources)
	for _, value := range spec.Parameters {
		values = append(values, value)
	}

	var result []orcapi.AppParameterValue
	for _, value := range values {
		if !jobResourceValuesContain(result, value) {
			result = append(result, value)
		}
	}
	return result
}

func jobResourceValuesContain(values []orcapi.AppParameterValue, value orcapi.AppParameterValue) bool {
	for _, existing := range values {
		if jobResourceSameIdentity(existing, value) {
			return true
		}
	}
	return false
}

func jobBindAddedResources(
	jobId string,
	previous []orcapi.AppParameterValue,
	next []orcapi.AppParameterValue,
) ([]orcapi.AppParameterValue, bool) {
	var bound []orcapi.AppParameterValue
	for _, value := range next {
		if jobResourceValuesContain(previous, value) {
			continue
		}
		if !jobBindResource(jobId, value) {
			for _, added := range bound {
				jobUnbindResource(jobId, added)
			}
			return nil, false
		}
		bound = append(bound, value)
	}
	return bound, true
}

func jobBindCreatedResources(jobId string) *util.HttpError {
	mutationLock := jobMutationLock(jobId)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	job, err := JobsRetrieve(rpc.ActorSystem, jobId, orcapi.JobFlags{})
	if err != nil {
		return err
	}
	if job.Status.State.IsFinal() {
		return util.HttpErr(http.StatusConflict, "the job is already in a final state")
	}

	_, ok := jobBindAddedResources(jobId, nil, jobResourceValues(job.Specification))
	if !ok {
		return util.HttpErr(http.StatusConflict, "one or more private networks are no longer available")
	}
	return nil
}

func jobDiscardFailedCreate(jobId string) {
	mutationLock := jobMutationLock(jobId)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	job, err := JobsRetrieve(rpc.ActorSystem, jobId, orcapi.JobFlags{})
	if err != nil {
		return
	}
	ResourceDelete(rpc.ActorSystem, jobType, ResourceParseId(jobId))
	for _, value := range jobResourceValues(job.Specification) {
		jobUnbindResource(jobId, value)
	}
}

func jobControlValidateResources(job orcapi.Job, values []orcapi.AppParameterValue) ([]orcapi.AppParameterValue, *util.HttpError) {
	support, ok := SupportByProduct[orcapi.JobSupport](jobType, job.Specification.Product)
	if !ok || !job.Status.ResolvedApplication.Present || !job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Present {
		return nil, util.HttpErr(http.StatusConflict, "the job application or product is unavailable")
	}
	owner, ok := rpc.LookupActor(job.Owner.CreatedBy)
	if !ok {
		return nil, util.HttpErr(http.StatusConflict, "the job owner is unavailable")
	}

	validation := &jobValueValidation{
		JobId:                   util.OptValue(job.Id),
		Owner:                   util.OptValue(job.Owner),
		Replicas:                util.OptValue(job.Specification.Replicas),
		NetworkChangesAllowed:   jobNetworkAttachAllowed(job.Status.State),
		NetworkIpChangesAllowed: true,
		ExistingResources:       job.Specification.Resources,
	}
	if !validation.NetworkChangesAllowed {
		err := jobNetworkChangesBlocked(job, values)
		if err != nil {
			return nil, err
		}
	}

	backend := job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value.Description.Backend
	validated := make([]orcapi.AppParameterValue, 0, len(values))
	for _, value := range values {
		err := jobValidateValue(owner, &value, backend, support, validation)
		if err != nil {
			return nil, err
		}
		validated = append(validated, value)
	}

	err := privateNetworkValidateJobNetworks(owner, backend, values)
	if err != nil {
		return nil, err
	}

	return jobPersistableResources(validated), nil
}

func jobControlAddUpdates(actor rpc.Actor, jobId string, updates []orcapi.JobUpdate, now time.Time) *util.HttpError {
	mutationLock := jobMutationLock(jobId)
	mutationLock.Lock()
	defer mutationLock.Unlock()

	job, err := JobsRetrieve(actor, jobId, orcapi.JobFlags{
		ResourceFlags:      orcapi.ResourceFlagsIncludeAll(),
		IncludeApplication: true,
	})
	if err != nil {
		return err
	}

	previous := jobResourceValues(job.Specification)
	if job.Status.State.IsFinal() {
		previous = nil
	}
	wasFinal := job.Status.State.IsFinal()
	job.Updates = slices.Clone(job.Updates)
	var pending []orcapi.JobUpdate
	for _, update := range updates {
		update.Timestamp = fndapi.Timestamp(now)
		if state := update.ExpectedState; state.Present && job.Status.State != state.Value {
			continue
		}
		if !update.ExpectedState.Present && update.ExpectedDifferentState.Present && update.ExpectedDifferentState.Value &&
			update.State.Present && job.Status.State == update.State.Value {
			continue
		}
		if job.Status.State.IsFinal() && update.State.Present && !update.State.Value.IsFinal() {
			continue
		}
		if jobUpdateIsDuplicate(job.Updates, update) {
			continue
		}

		if update.ResourceList.Present {
			resources, validationErr := jobControlValidateResources(job, update.ResourceList.Value)
			if validationErr != nil {
				return validationErr
			}
			update.ResourceList.Set(resources)
			job.Specification.Resources = resources
		}
		if update.State.Present {
			job.Status.State = update.State.Value
		}
		job.Updates = append(job.Updates, update)
		pending = append(pending, update)
	}
	if len(pending) == 0 {
		return nil
	}

	next := jobResourceValues(job.Specification)
	if job.Status.State.IsFinal() {
		next = nil
	}
	bound, bindOk := jobBindAddedResources(jobId, previous, next)
	if !bindOk {
		return util.HttpErr(http.StatusConflict, "one or more private networks are no longer available")
	}

	var notifications []orcapi.Job
	ok := ResourceUpdate(actor, jobType, ResourceParseId(jobId), orcapi.PermissionProvider, func(r *resource, mapped orcapi.Job) {
		internal := r.Extra.(*internalJob)
		internal.ChangeFlags |= internalJobPartialChange | internalJobChangeUpdates | internalJobChangeMetadata
		for _, update := range pending {
			if state := update.State; state.Present && internal.State != state.Value {
				internal.State = state.Value
				if internal.State == orcapi.JobStateRunning {
					internal.StartedAt.Set(fndapi.Timestamp(now))
				}
				mapped.Status.State = internal.State
				notifications = append(notifications, mapped)
			}
			if update.OutputFolder.Present {
				internal.OutputFolder = update.OutputFolder
			}
			if update.NewTimeAllocation.Present {
				internal.TimeAllocation.Set(orcapi.SimpleDurationFromMillis(update.NewTimeAllocation.Value))
			}
			if update.ResourceList.Present {
				internal.Resources = update.ResourceList.Value
				internal.ChangeFlags |= internalJobChangeResources
			}
			internal.Updates = append(internal.Updates, update)
		}
	})
	if !ok {
		for _, added := range bound {
			jobUnbindResource(jobId, added)
		}
		return util.HttpErr(http.StatusNotFound, "unknown job or permission denied (%v)", jobId)
	}

	for _, value := range previous {
		if !jobResourceValuesContain(next, value) {
			jobUnbindResource(jobId, value)
		}
	}
	for _, notification := range notifications {
		jobNotifyStateChange(notification)
	}
	if !wasFinal && job.Status.State.IsFinal() {
		serviceRemoveMembershipsOfJob(jobId)
	}
	return nil
}
