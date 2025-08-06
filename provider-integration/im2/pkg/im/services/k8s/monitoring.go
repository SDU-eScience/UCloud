package k8s

import (
	"context"
	"encoding/json"
	"fmt"
	"maps"
	"slices"
	"strings"
	"time"

	core "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/kubevirt"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/shared/pkg/apm"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var nextNodeMonitor time.Time
var nextAccounting time.Time

// NOTE(Dan): This must only be used by code invoked from the goroutine in loopMonitoring. None of the code is
// thread-safe.

var schedulers = map[string]*Scheduler{}

func getScheduler(category string, group string) (*Scheduler, bool) {
	schedKey := fmt.Sprintf("%s/%s", category, group)

	_, isIApp := ctrl.IntegratedApplications[category]
	if isIApp {
		// TODO Ask the application about the scheduler.
	} else {
		_, ok := shared.ServiceConfig.Compute.Machines[category]
		if !ok {
			return nil, false
		}
	}

	existing, ok := schedulers[schedKey]
	if !ok {
		schedulers[schedKey] = NewScheduler(schedKey)
		existing, ok = schedulers[schedKey]
	}
	return existing, ok
}

func getSchedulerByJob(job *orc.Job) (*Scheduler, bool) {
	product := job.Specification.Product
	_, isIApp := ctrl.IntegratedApplications[product.Category]
	if isIApp {
		return getScheduler(product.Category, product.Category)
	} else {
		_, group, _, ok := shared.ServiceConfig.Compute.ResolveMachine(product.Id, product.Category)
		if !ok {
			return nil, false
		}
		return getScheduler(product.Category, group.GroupName)
	}
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

// initJobQueue will initialize the queue with jobs which were in the queue when the integration module was last
// shutdown.
func initJobQueue() {
	jobs := ctrl.JobsListServer()
	for _, job := range jobs {
		if job.Status.State == orc.JobStateInQueue {
			shared.RequestSchedule(job)
		}
	}
}

func loopMonitoring() {
	now := time.Now()

	// NOTE(Dan): Node monitoring must go before job monitoring such that the scheduler knows about the nodes before
	// it knows about running replicas.
	if now.After(nextNodeMonitor) {
		nodeList := util.RetryOrPanic[*core.NodeList]("list k8s nodes", func() (*core.NodeList, error) {
			return shared.K8sClient.CoreV1().Nodes().List(context.TODO(), metav1.ListOptions{})
		})

		if util.DevelopmentModeEnabled() && len(nodeList.Items) == 1 {
			baseNode := nodeList.Items[0]
			nodeList.Items = []core.Node{}

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

			if shared.ServiceConfig.Compute.IntegratedTerminal.Enabled {
				syncthingNode := nodeList.Items[0]
				syncthingNode.Labels = maps.Clone(syncthingNode.Labels)
				syncthingNode.Labels["ucloud.dk/machine"] = "terminal"
				nodeList.Items = append(nodeList.Items, syncthingNode)
			}
		}

		length := len(nodeList.Items)
		for i := 0; i < length; i++ {
			node := &nodeList.Items[i]
			catGroups := nodeCategories(node)
			for _, catGroup := range catGroups {
				sched, ok := getScheduler(catGroup.Category, catGroup.Group)
				if !ok {
					continue
				}

				k8sCapacity := node.Status.Capacity
				k8sAllocatable := node.Status.Allocatable

				gpuType := "nvidia.com/gpu"
				machineCategory, ok := shared.ServiceConfig.Compute.Machines[catGroup.Category]
				if ok {
					// TODO This seems like it will break if there is more than one category
					nodeCat, ok := machineCategory.Groups[catGroup.Category]
					if ok {
						gpuType = nodeCat.GpuResourceType
					}
				}

				capacity := shared.SchedulerDimensions{
					CpuMillis:     int(k8sCapacity.Cpu().MilliValue()),
					MemoryInBytes: int(k8sCapacity.Memory().Value()),
				}

				limits := shared.SchedulerDimensions{
					CpuMillis:     int(k8sAllocatable.Cpu().MilliValue()),
					MemoryInBytes: int(k8sAllocatable.Memory().Value()),
				}

				gpuCap := k8sCapacity.Name(core.ResourceName(gpuType), resource.DecimalSI)
				gpuLim := k8sAllocatable.Name(core.ResourceName(gpuType), resource.DecimalSI)

				if gpuCap != nil && gpuLim != nil {
					capacity.Gpu += int(gpuCap.Value())
					limits.Gpu += int(gpuLim.Value())
				}

				for _, cond := range node.Status.Conditions {
					setLimitsToZero := false
					status := cond.Status

					switch cond.Type {
					case core.NodeReady:
						setLimitsToZero = status == core.ConditionFalse
					case core.NodeMemoryPressure:
						setLimitsToZero = status == core.ConditionTrue
					case core.NodeDiskPressure:
						setLimitsToZero = status == core.ConditionTrue
					case core.NodePIDPressure:
						setLimitsToZero = status == core.ConditionTrue
					case core.NodeNetworkUnavailable:
						setLimitsToZero = status == core.ConditionTrue
					}

					if setLimitsToZero {
						limits.CpuMillis = 0
						limits.MemoryInBytes = 0
						limits.Gpu = 0
						break
					}
				}

				capacity.CpuMillis = max(0, capacity.CpuMillis)
				limits.CpuMillis = max(0, limits.CpuMillis)

				sched.RegisterNode(node.Name, capacity, limits, node.Spec.Unschedulable)
			}
		}

		for _, sched := range schedulers {
			sched.PruneNodes()
		}

		nextNodeMonitor = now.Add(15 * time.Second)
	}

	// Job monitoring
	// -----------------------------------------------------------------------------------------------------------------
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

	// Side-effects from job monitoring
	// -----------------------------------------------------------------------------------------------------------------
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

	// Accounting
	// -----------------------------------------------------------------------------------------------------------------
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

	// Node remaining capacity calculation
	// -----------------------------------------------------------------------------------------------------------------
	// The following snippet of code will calculate exactly how many resources are left on the node based directly
	// on the information available to us in Kubernetes. This solution is supposed to be as robust as possible and is
	// intended to avoid any kind of OutOfCpu error that K8s might return to us if we schedule a pod slightly before
	// K8s is ready for it. The scheduler, technically, works without it, but K8s might complain a lot.
	//
	// Unfortunately, K8s doesn't just expose a set of numbers we can read. Instead, we must compute this from a list of
	// pods. In most deployment scenarios, the K8s client doesn't have cluster-wide read rights. As a result, we compute
	// everything we can from the app namespace and guess what the remaining reservation is. These are configurable in
	// the service configuration files.
	//
	// NOTE(Dan): This step must occur _after_ replicas being pruned.
	//
	// TODO(Dan): I am slightly worried that for VMs this really just serves as a minimum usage and not something that
	//  represents the expected usage in the near future. For VMs, this might be very wrong if they haven't created
	//   their pod immediately after the schedule.

	{
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		allPods, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.Namespace).List(ctx, metav1.ListOptions{})
		cancel()
		if err != nil {
			log.Info("Failed to get a list of pods from Kubernetes: %s", err)
			return
		}

		resourcesByNode := map[string]map[string]int64{}

		for _, pod := range allPods.Items {
			nodeName := pod.Spec.NodeName
			resources, ok := resourcesByNode[nodeName]
			if !ok {
				resources = make(map[string]int64)
				resourcesByNode[nodeName] = resources
			}

			for _, c := range pod.Spec.Containers {
				for resourceType, amount := range c.Resources.Limits {
					intAmount := int64(0)
					if resourceType == core.ResourceCPU {
						intAmount = amount.MilliValue()
					} else {
						intAmount = amount.Value()
					}

					resources[string(resourceType)] = resources[string(resourceType)] + intAmount
				}
			}
		}

		for catName, sched := range schedulers {
			for nodeName, _ := range sched.Nodes {
				usage := resourcesByNode[nodeName]
				if usage == nil {
					usage = make(map[string]int64)
				}

				machineCategory, ok := shared.ServiceConfig.Compute.Machines[catName]
				gpuResourceType := "nvidia.com/gpu"
				systemReservedCpuMillis := 500

				if ok {
					// TODO This seems like it will break if there is more than one category
					nodeCat, ok := machineCategory.Groups[catName]
					if ok {
						gpuResourceType = nodeCat.GpuResourceType
						systemReservedCpuMillis = nodeCat.SystemReservedCpuMillis
					}
				}

				dims := shared.SchedulerDimensions{
					CpuMillis:     int(usage[string(core.ResourceCPU)]) + systemReservedCpuMillis,
					MemoryInBytes: int(usage[string(core.ResourceMemory)]),
					Gpu:           int(usage[gpuResourceType]),
				}

				sched.SynchronizeNodeUsage(nodeName, dims)
			}
		}
	}

	// Job scheduling
	// -----------------------------------------------------------------------------------------------------------------
	entriesToSubmit := shared.SwapScheduleQueue()
	for _, entry := range entriesToSubmit {
		sched, ok := getSchedulerByJob(entry)
		if ok {
			sched.RegisterJobInQueue(entry.Id, shared.JobDimensions(entry),
				entry.Specification.Replicas, nil, entry.CreatedAt, timeAllocationOrDefault(entry.Specification.TimeAllocation))
		}
	}

	var scheduleMessages []ctrl.JobMessage
	var allScheduled []*SchedulerReplicaEntry

	for _, sched := range schedulers {
		jobsToSchedule := sched.Schedule()
		length := len(jobsToSchedule)
		for i := 0; i < length; i++ {
			toSchedule := &jobsToSchedule[i]
			job, ok := ctrl.RetrieveJob(toSchedule.JobId)
			if !ok {
				continue
			}
			allScheduled = append(allScheduled, toSchedule)

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
				err := containers.StartScheduledJob(job, toSchedule.Rank, toSchedule.Node)
				if err != nil {
					scheduleMessages = append(scheduleMessages, ctrl.JobMessage{
						JobId:   job.Id,
						Message: fmt.Sprintf("Failed to schedule job: %s", err),
					})

					_, isIApp := ctrl.IntegratedApplications[job.Specification.Product.Category]
					if !isIApp {
						// IApps typically hits this branch if they are out of resources. We do not want them to stop
						// attempting to re-schedule in this scenario. Generally we do not want iapps to enter a
						// final state ever.
						_ = terminate(ctrl.JobTerminateRequest{
							Job:       job,
							IsCleanup: false,
						})
					}
				}
			}
		}
	}

	for newIdx := 0; newIdx < len(entriesToSubmit); newIdx++ {
		newJob := entriesToSubmit[newIdx]

		found := false
		for schedIdx := 0; schedIdx < len(allScheduled); schedIdx++ {
			scheduled := allScheduled[schedIdx]
			if scheduled.JobId == newJob.Id {
				found = true
				break
			}
		}

		if !found {
			scheduleMessages = append(scheduleMessages, ctrl.JobMessage{
				JobId: newJob.Id,
				Message: "There are currently no machines available to run your job.\n" +
					"A smaller machine might give you quicker access to your job.",
			})
		}
	}

	_ = ctrl.TrackJobMessages(scheduleMessages)
}

type NodeCatGroup struct {
	Category string
	Group    string
}

func nodeCategories(node *core.Node) []NodeCatGroup {
	// NOTE(Dan): It is really important that production providers only return 1. Being able to return more than one
	// is just for testing in resource-constrained environments.
	parseResult := func(machineLabel string) []NodeCatGroup {
		if len(machineLabel) == 0 {
			return nil
		} else {
			var nodes []string
			if machineLabel[0] == '[' {
				_ = json.Unmarshal([]byte(machineLabel), &nodes)
			} else {
				nodes = []string{machineLabel}
			}

			var result []NodeCatGroup
			for _, n := range nodes {
				toks := strings.Split(n, "/")
				if len(toks) == 2 {
					result = append(result, NodeCatGroup{
						Category: toks[0],
						Group:    toks[1],
					})
				} else {
					result = append(result, NodeCatGroup{
						Category: n,
						Group:    n,
					})
				}
			}

			return result
		}
	}

	var result []NodeCatGroup
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
