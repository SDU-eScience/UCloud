package k8s

import (
	"context"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/kubevirt"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var nextJobMonitor time.Time
var nextNodeMonitor time.Time

// TODO Select a scheduler based on category. We need multiple schedulers.
var sched = NewScheduler()

type jobTracker struct {
	batch *ctrl.JobUpdateBatch
	jobs  map[string]*orc.Job
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
		return false
	}

	if state.State == orc.JobStateRunning && state.Node.Present {
		sched.RegisterRunningReplica(state.Id, state.Rank, jobDimensions(job), state.Node.Value, nil,
			timeAllocationOrDefault(job.Specification.TimeAllocation))

		t.batch.TrackAssignedNodes(state.Id, []string{state.Node.Value})
	}

	ok = t.batch.TrackState(state.Id, state.State, state.Status)
	return ok
}

func jobDimensions(job *orc.Job) SchedulerDimensions {
	prod := &job.Status.ResolvedProduct
	return SchedulerDimensions{
		CpuMillis:     prod.Cpu * 1000,
		MemoryInBytes: prod.MemoryInGigs * (1024 * 1024 * 1024),
		Gpu:           0,
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

	if now.After(nextJobMonitor) {
		tracker := &jobTracker{
			batch: ctrl.BeginJobUpdates(),
		}

		activeJobs := ctrl.GetJobs()
		tracker.jobs = activeJobs
		containers.Monitor(tracker, activeJobs)
		kubevirt.Monitor(tracker, activeJobs)
		sched.PruneReplicas()
		tracker.batch.End()

		length := len(sched.Queue)
		for i := 0; i < length; i++ {
			queueEntry := &sched.Queue[i]
			tracker.batch.TrackState(queueEntry.JobId, orc.JobStateInQueue, util.OptNone[string]())
		}

		nextJobMonitor = now.Add(5 * time.Second)
	}

	if now.After(nextNodeMonitor) {
		nodeList := util.RetryOrPanic[*corev1.NodeList]("list k8s nodes", func() (*corev1.NodeList, error) {
			return shared.K8sClient.CoreV1().Nodes().List(context.TODO(), metav1.ListOptions{})
		})

		length := len(nodeList.Items)
		for i := 0; i < length; i++ {
			node := &nodeList.Items[i]
			category := nodeCategory(node)
			if !category.Present {
				continue
			}

			k8sCapacity := node.Status.Capacity
			k8sAllocatable := node.Status.Allocatable

			capacity := SchedulerDimensions{
				CpuMillis:     int(k8sCapacity.Cpu().MilliValue()),
				MemoryInBytes: int(k8sCapacity.Memory().Value()),
				Gpu:           int(k8sCapacity.Name("nvidia.com/gpu", resource.DecimalSI).Value()),
			}

			limits := SchedulerDimensions{
				CpuMillis:     int(k8sAllocatable.Cpu().MilliValue()),
				MemoryInBytes: int(k8sAllocatable.Memory().Value()),
				Gpu:           int(k8sAllocatable.Name("nvidia.com/gpu", resource.DecimalSI).Value()),
			}

			sched.RegisterNode(node.Name, capacity, limits, node.Spec.Unschedulable)
			sched.PruneNodes()
		}

		nextNodeMonitor = now.Add(15 * time.Second)
	}

	entriesToSubmit := shared.SwapScheduleQueue()
	for _, entry := range entriesToSubmit {
		sched.RegisterJobInQueue(entry.Id, jobDimensions(entry),
			entry.Specification.Replicas, nil, entry.CreatedAt, timeAllocationOrDefault(entry.Specification.TimeAllocation))
	}

	jobsToSchedule := sched.Schedule()
	length := len(jobsToSchedule)
	for i := 0; i < length; i++ {
		toSchedule := &jobsToSchedule[i]
		job, ok := ctrl.RetrieveJob(toSchedule.JobId)
		if !ok {
			continue
		}

		toolBackend := job.Status.ResolvedApplication.Invocation.Tool.Tool.Description.Backend
		if toolBackend == orc.ToolBackendVirtualMachine {
			kubevirt.StartScheduledJob(job, toSchedule.Rank, toSchedule.Node)
		} else {
			containers.StartScheduledJob(job, toSchedule.Rank, toSchedule.Node)
		}
	}
}

func nodeCategory(node *corev1.Node) util.Option[string] {
	machineLabel, ok := node.Labels["ucloud.dk/machine"]
	if !ok {
		// Dev only fix
		if node.ObjectMeta.Name == "im2k3" {
			for k, _ := range shared.ServiceConfig.Compute.Machines {
				return util.OptValue(k)
			}
		}
		return util.OptNone[string]()
	}

	return util.OptValue(machineLabel)
}
