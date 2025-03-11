package k8s

import (
	"context"
	"encoding/json"
	"fmt"
	"slices"
	"time"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"maps"
	"slices"
	"time"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/kubevirt"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var nextJobMonitor time.Time
var nextNodeMonitor time.Time
var nextAccounting time.Time

// NOTE(Dan): This must only be used by code invoked from the goroutine in loopMonitoring. None of the code is
// thread-safe.

var schedulers = map[string]*Scheduler{}

func getScheduler(category string) (*Scheduler, bool) {
	_, isIApp := ctrl.IntegratedApplications[category]
	if isIApp {
		// TODO Ask the application about the scheduler.
	} else {
		_, ok := shared.ServiceConfig.Compute.Machines[category]
		if !ok {
			return nil, false
		}
	}

	existing, ok := schedulers[category]
	if !ok {
		schedulers[category] = NewScheduler(category)
		existing, ok = schedulers[category]
	}
	return existing, ok
}

func getSchedulerByJob(job *orc.Job) (*Scheduler, bool) {
	return getScheduler(job.Specification.Product.Category)
}

type jobGang struct {
	replicaState map[int]shared.JobReplicaState
}

type jobTracker struct {
	batch                *ctrl.JobUpdateBatch
	jobs                 map[string]*orc.Job
	gangs                map[string]jobGang
	terminationRequested []string
}

var jobStateRankings = map[orc.JobState]int{
	orc.JobStateRunning:   0,
	orc.JobStateInQueue:   1,
	orc.JobStateSuspended: 2,
	orc.JobStateSuccess:   3,
	orc.JobStateExpired:   4,
	orc.JobStateFailure:   5,
}

func (t *jobTracker) AddUpdate(id string, update orc.JobUpdate) {
	t.batch.AddUpdate(orc.ResourceUpdateAndId[orc.JobUpdate]{
		Id:     id,
		Update: update,
	})
}

func (t *jobTracker) TrackState(state shared.JobReplicaState) bool {
	job, ok := t.jobs[state.Id]
	if !ok {
		log.Info("Unknown job with TrackState: %v", state.Id)
		return false
	}

	sched, ok := getSchedulerByJob(job)
	if !ok {
		log.Info("Unknown scheduler for job: %v", state.Id)
		return false
	}

	gang, ok := t.gangs[state.Id]
	if !ok {
		gang.replicaState = map[int]shared.JobReplicaState{}
		t.gangs[state.Id] = gang
	}

	if state.State == orc.JobStateRunning && state.Node.Present {
		sched.RegisterRunningReplica(state.Id, state.Rank, shared.JobDimensions(job), state.Node.Value, nil,
			timeAllocationOrDefault(job.Specification.TimeAllocation))
	}

	gang.replicaState[state.Rank] = state

	if len(gang.replicaState) >= job.Specification.Replicas {
		var allNodes []string
		reportNodes := true

		// The overall job uses a state equivalent to the state of the most important state in the gang
		var stateToReport shared.JobReplicaState
		bestRank := -1
		for i := 0; i < job.Specification.Replicas; i++ {
			thisState := gang.replicaState[i]
			thisRank := jobStateRankings[thisState.State]
			if thisRank > bestRank {
				stateToReport = thisState
				bestRank = thisRank
			}

			reportNodes = reportNodes && thisState.Node.Present
			allNodes = append(allNodes, thisState.Node.Value)
		}

		if reportNodes {
			t.batch.TrackAssignedNodes(state.Id, allNodes)
		}

		if bestRank >= 0 {
			return t.batch.TrackState(stateToReport.Id, stateToReport.State, stateToReport.Status)
		} else {
			return false
		}
	} else {
		// NOTE(Dan): If a job doesn't TrackState for all ranks, then we are in one of two cases:
		//
		// 1. Not all jobs are ready yet. The JobUpdateBatch will allow this state for up to 5 minutes, after this
		//    the job automatically fails. This shouldn't be a problem in practice since it usually doesn't take long
		//    to create the resources in Kubernetes. The pods don't have to start before they report TrackState, they
		//    just have to exist in Kubernetes.
		// 2. One of the ranks have finished. In this case we want JobUpdateBatch to kill the job since we only want
		//    jobs to run if the entire gang is running.
	}

	return true
}

func (t *jobTracker) RequestCleanup(jobId string) {
	if !slices.Contains(t.terminationRequested, jobId) {
		t.terminationRequested = append(t.terminationRequested, jobId)
	}
}

func timeAllocationOrDefault(alloc util.Option[orc.SimpleDuration]) orc.SimpleDuration {
	return alloc.GetOrDefault(orc.SimpleDuration{
		Hours:   24 * 365,
		Minutes: 0,
		Seconds: 0,
	})
}

func loopMonitoring() {
	now := time.Now()

	// NOTE(Dan): Node monitoring must go before job monitoring such that the scheduler knows about the nodes before
	// it knows about running replicas.
	if now.After(nextNodeMonitor) {
		nodeList := util.RetryOrPanic[*corev1.NodeList]("list k8s nodes", func() (*corev1.NodeList, error) {
			return shared.K8sClient.CoreV1().Nodes().List(context.TODO(), metav1.ListOptions{})
		})

		if util.DevelopmentModeEnabled() && len(nodeList.Items) == 1 {
			baseNode := nodeList.Items[0]
			nodeList.Items = []corev1.Node{}

			for category, _ := range shared.ServiceConfig.Compute.Machines {
				normalMachine := baseNode
				normalMachine.Labels = maps.Clone(normalMachine.Labels)
				normalMachine.Labels["ucloud.dk/machine"] = category
				nodeList.Items = append(nodeList.Items, normalMachine)
				break
			}

			if shared.ServiceConfig.Compute.Syncthing.Enabled {
				syncthingNode := nodeList.Items[0]
				syncthingNode.Labels = maps.Clone(syncthingNode.Labels)
				syncthingNode.Labels["ucloud.dk/machine"] = "syncthing"
				nodeList.Items = append(nodeList.Items, syncthingNode)
			}
		}

		length := len(nodeList.Items)
		for i := 0; i < length; i++ {
			node := &nodeList.Items[i]
			categories := nodeCategories(node)
			for _, category := range categories {
				sched, ok := getScheduler(category)
				if !ok {
					continue
				}

				k8sCapacity := node.Status.Capacity
				k8sAllocatable := node.Status.Allocatable

				capacity := shared.SchedulerDimensions{
					CpuMillis:     int(k8sCapacity.Cpu().MilliValue()),
					MemoryInBytes: int(k8sCapacity.Memory().Value()),
					Gpu:           int(k8sCapacity.Name("nvidia.com/gpu", resource.DecimalSI).Value()),
				}

				limits := shared.SchedulerDimensions{
					CpuMillis:     int(k8sAllocatable.Cpu().MilliValue()),
					MemoryInBytes: int(k8sAllocatable.Memory().Value()),
					Gpu:           int(k8sAllocatable.Name("nvidia.com/gpu", resource.DecimalSI).Value()),
				}

				sched.RegisterNode(node.Name, capacity, limits, node.Spec.Unschedulable)
			}
		}

		for _, sched := range schedulers {
			sched.PruneNodes()
		}

		nextNodeMonitor = now.Add(15 * time.Second)
	}

	if now.After(nextJobMonitor) {
		activeJobs := ctrl.GetJobs()
		tracker := &jobTracker{
			batch: ctrl.BeginJobUpdates(),
			gangs: map[string]jobGang{},
		}

		tracker.jobs = activeJobs
		containers.Monitor(tracker, activeJobs)
		kubevirt.Monitor(tracker, activeJobs)
		for _, sched := range schedulers {
			sched.PruneReplicas()

			length := len(sched.Queue)
			for i := 0; i < length; i++ {
				queueEntry := &sched.Queue[i]
				tracker.batch.TrackState(queueEntry.JobId, orc.JobStateInQueue, util.OptNone[string]())
			}
		}

		batchResults := tracker.batch.End()

		{
			// Lock jobs which are out of resources

			activeJobsAfterBatch := ctrl.GetJobs()
			var lockedMessages []ctrl.JobMessage
			for _, job := range activeJobsAfterBatch {
				if reason := IsJobLocked(job); reason.Present {
					lockedMessages = append(lockedMessages, ctrl.JobMessage{
						JobId:   job.Id,
						Message: reason.Value.Reason,
					})
					tracker.RequestCleanup(job.Id)
				}
			}
			_ = ctrl.TrackJobMessages(lockedMessages)
		}

		go func() {
			for _, jobId := range tracker.terminationRequested {
				job, ok := ctrl.RetrieveJob(jobId)
				if ok {
					_ = terminate(ctrl.JobTerminateRequest{Job: job, IsCleanup: true})
				}
			}

			for _, jobId := range batchResults.TerminatedDueToUnknownState {
				job, ok := ctrl.RetrieveJob(jobId)
				if ok {
					_ = terminate(ctrl.JobTerminateRequest{Job: job, IsCleanup: true})
				}
			}

			var kubevirtStarted []orc.Job
			var containersStarted []orc.Job
			for _, jobId := range batchResults.NormalStart {
				job, ok := ctrl.RetrieveJob(jobId)
				if ok {
					if backendIsKubevirt(job) {
						kubevirtStarted = append(kubevirtStarted, *job)
					} else if backendIsContainers(job) {
						containersStarted = append(containersStarted, *job)
					}
				}
			}

			if len(containersStarted) > 0 {
				containers.OnStart(containersStarted)
			}
			if len(kubevirtStarted) > 0 {
				kubevirt.OnStart(kubevirtStarted)
			}
		}()

		if now.After(nextAccounting) {
			var reports []apm.UsageReportItem

			for _, job := range activeJobs {
				timeReport := shared.ComputeRunningTime(job)
				accUnits := convertJobTimeToAccountingUnits(job, timeReport.TimeConsumed)
				reports = append(reports, apm.UsageReportItem{
					IsDeltaCharge: false,
					Owner:         apm.WalletOwnerFromIds(job.Owner.CreatedBy, job.Owner.Project),
					CategoryIdV2: apm.ProductCategoryIdV2{
						Name:     job.Specification.Product.Category,
						Provider: job.Specification.Product.Provider,
					},
					Usage: accUnits,
					Description: apm.ChargeDescription{
						Scope: util.OptValue(fmt.Sprintf("job-%v", job.Id)),
					},
				})
			}

			if len(reports) > 0 {
				reportsChunked := util.ChunkBy(reports, 500)

				for chunkIdx := 0; chunkIdx < len(reportsChunked); chunkIdx++ {
					// NOTE(Dan): Results are completely ignored here, and we rely solely on the APM events which will
					// trigger when the wallet is locked.

					reportChunk := reportsChunked[chunkIdx]

					_, err := apm.ReportUsage(fnd.BulkRequest[apm.UsageReportItem]{Items: reportChunk})
					if err != nil {
						log.Info("Failed to report usage: %s", err)
					}
				}
			}

			nextAccounting = now.Add(30 * time.Second)
		}

		nextJobMonitor = now.Add(5 * time.Second)
	}

	entriesToSubmit := shared.SwapScheduleQueue()
	for _, entry := range entriesToSubmit {
		sched, ok := getSchedulerByJob(entry)
		if ok {
			sched.RegisterJobInQueue(entry.Id, shared.JobDimensions(entry),
				entry.Specification.Replicas, nil, entry.CreatedAt, timeAllocationOrDefault(entry.Specification.TimeAllocation))
		}
	}

	var scheduleMessages []ctrl.JobMessage

	for _, sched := range schedulers {
		jobsToSchedule := sched.Schedule()
		length := len(jobsToSchedule)
		for i := 0; i < length; i++ {
			toSchedule := &jobsToSchedule[i]
			job, ok := ctrl.RetrieveJob(toSchedule.JobId)
			if !ok {
				continue
			}

			if job.Specification.Replicas == 1 {
				scheduleMessages = append(scheduleMessages, ctrl.JobMessage{
					JobId:   job.Id,
					Message: fmt.Sprintf("Job has been scheduled and is starting soon (Assigned to %s)", toSchedule.Node),
				})
			} else {
				if toSchedule.Rank == 0 {
					scheduleMessages = append(scheduleMessages, ctrl.JobMessage{
						JobId:   job.Id,
						Message: fmt.Sprintf("Job has been scheduled and is starting soon (Rank 0 assigned to %v)", toSchedule.Node),
					})
				}
			}

			toolBackend := job.Status.ResolvedApplication.Invocation.Tool.Tool.Description.Backend
			if toolBackend == orc.ToolBackendVirtualMachine {
				kubevirt.StartScheduledJob(job, toSchedule.Rank, toSchedule.Node)
			} else {
				containers.StartScheduledJob(job, toSchedule.Rank, toSchedule.Node)
			}
		}
	}

	_ = ctrl.TrackJobMessages(scheduleMessages)
}

func nodeCategories(node *corev1.Node) []string {
	// NOTE(Dan): It is really important that production providers only return 1. Being able to return more than one
	// is just for testing in resource-constrained environments.
	parseResult := func(machineLabel string) []string {
		if len(machineLabel) == 0 {
			return nil
		} else {
			if machineLabel[0] == '[' {
				var nodes []string
				_ = json.Unmarshal([]byte(machineLabel), &nodes)
				return nodes
			} else {
				return []string{machineLabel}
			}
		}
	}

	var result []string
	result = append(result, parseResult(node.Labels["ucloud.dk/machine"])...)
	result = append(result, parseResult(node.Annotations["ucloud.dk/machine"])...)
	return result
}

func convertJobTimeToAccountingUnits(job *orc.Job, timeConsumed time.Duration) int64 {
	k8sCategory := shared.ServiceConfig.Compute.Machines[job.Specification.Product.Category]

	productUnits := float64(job.Specification.Replicas)
	switch k8sCategory.Payment.Unit {
	case cfg.MachineResourceTypeCpu:
		productUnits *= float64(job.Status.ResolvedProduct.Cpu)
	case cfg.MachineResourceTypeGpu:
		productUnits *= float64(job.Status.ResolvedProduct.Gpu)
	case cfg.MachineResourceTypeMemory:
		productUnits *= float64(job.Status.ResolvedProduct.MemoryInGigs)
	case "":
		// Use just the nodes. This is used when type is currency.
	}

	baseTimeUnit := 1 * time.Minute
	switch k8sCategory.Payment.Interval {
	case cfg.PaymentIntervalMinutely:
		baseTimeUnit = time.Minute
	case cfg.PaymentIntervalHourly:
		baseTimeUnit = time.Hour
	case cfg.PaymentIntervalDaily:
		baseTimeUnit = 24 * time.Hour
	}

	timeUnits := float64(timeConsumed) / float64(baseTimeUnit)

	hasPrice := k8sCategory.Payment.Type == cfg.PaymentTypeMoney
	price := float64(1)
	if hasPrice {
		price = float64(job.Status.ResolvedProduct.Price) / 1000000
	}

	accountingUnitsUsed := productUnits * timeUnits * price
	if hasPrice {
		accountingUnitsUsed *= 1000000
	}

	return int64(accountingUnitsUsed)
}
