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
var sched = newScheduler(0)

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
		sched.RegisterRunningReplica(state.Id, state.Rank, jobDimensions(job), state.Node.Value, nil)
		t.batch.TrackAssignedNodes(state.Id, []string{state.Node.Value})
	}

	ok = t.batch.TrackState(state.Id, state.State, state.Status)
	return ok
}

func jobDimensions(job *orc.Job) schedulerDimensions {
	prod := &job.Status.ResolvedProduct
	return schedulerDimensions{
		CpuMillis:     prod.Cpu * 1000,
		MemoryInBytes: prod.MemoryInGigs * (1024 * 1024 * 1024),
		Gpu:           0,
	}
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

		length := len(sched.queue)
		for i := 0; i < length; i++ {
			queueEntry := &sched.queue[i]
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

			allocatable := node.Status.Allocatable
			cpuMillis := int(allocatable.Cpu().MilliValue())
			memoryBytes := int(allocatable.Memory().Value())
			gpus := int(allocatable.Name("nvidia.com/gpu", resource.DecimalSI).Value())

			sched.RegisterNode(node.Name, category.Value, cpuMillis, memoryBytes, gpus, node.Spec.Unschedulable)
			sched.PruneNodes()
		}

		nextNodeMonitor = now.Add(15 * time.Second)
	}

	entriesToSubmit := shared.SwapScheduleQueue()
	for _, entry := range entriesToSubmit {
		sched.RegisterJobInQueue(entry.Id, entry.Specification.Product.Category, jobDimensions(entry), 1,
			entry.Specification.Replicas, nil)
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
		return util.OptNone[string]()
	}

	return util.OptValue(machineLabel)
}
