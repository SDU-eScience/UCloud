package kubevirt

import (
	"context"
	"time"

	k8score "k8s.io/api/core/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	kvcore "kubevirt.io/api/core/v1"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	releasedPvScanInterval       = time.Minute
	releasedPvPermissionInterval = 5 * time.Minute
	missingVmGracePeriod         = 5 * time.Minute
)

var missingVmSince = map[string]time.Time{}

var releasedPvCleanupState struct {
	nextScan               time.Time
	scanPermissionLogged   bool
	deletePermissionLogged bool
}

func SchedulerPlacementExists(jobId string, rank int) (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	name := vmName(jobId, rank)
	_, err := KubevirtClient.VirtualMachine(Namespace).Get(ctx, name, metav1.GetOptions{})
	if !k8serrors.IsNotFound(err) {
		return true, err
	}
	_, err = KubevirtClient.VirtualMachineInstance(Namespace).Get(ctx, name, metav1.GetOptions{})
	if !k8serrors.IsNotFound(err) {
		return true, err
	}
	pods, err := shared.K8sClient.CoreV1().Pods(Namespace).List(ctx, metav1.ListOptions{
		LabelSelector: "ucloud.dk/vmName=" + name,
	})
	if err != nil {
		return true, err
	}
	return len(pods.Items) > 0, nil
}

func Monitor(tracker shared.JobTracker, jobs map[string]*orc.Job) {
	for jobId := range missingVmSince {
		if _, active := jobs[jobId]; !active {
			delete(missingVmSince, jobId)
		}
	}

	// In case of an error, we must not imply that the VM was deleted. We start by preserving all active VM states
	// before we run any KubeVirt queries. We then update only the known results.
	for _, job := range jobs {
		tracker.PreserveState(job.Id)
	}

	if !Enabled {
		missingVmSince = map[string]time.Time{}
		return
	}

	cleanupReleasedPVs()

	activeInstances, err := KubevirtClient.VirtualMachineInstance(Namespace).List(context.Background(), metav1.ListOptions{})
	if err != nil {
		missingVmSince = map[string]time.Time{}
		log.Info("Failed to fetch virtual machines instances: %v", err)
		return
	}

	instancesByName := map[string]*kvcore.VirtualMachineInstance{}
	length := len(activeInstances.Items)
	for i := 0; i < length; i++ {
		instance := &activeInstances.Items[i]
		instancesByName[instance.Name] = instance
	}

	activeMachines, err := KubevirtClient.VirtualMachine(Namespace).List(context.Background(), metav1.ListOptions{})
	if err != nil {
		missingVmSince = map[string]time.Time{}
		log.Info("Failed to fetch virtual machines: %v", err)
		return
	}

	seenJobs := map[string]util.Empty{}
	length = len(activeMachines.Items)
	for i := 0; i < length; i++ {
		machine := &activeMachines.Items[i]
		instance, hasInstance := instancesByName[machine.Name]
		jobId, _, ok := vmNameToJobIdAndRank(machine.Name)
		if !ok {
			continue
		}
		if _, active := jobs[jobId]; !active {
			continue
		}
		seenJobs[jobId] = util.Empty{}
		delete(missingVmSince, jobId)
		cpuRequestMillis := util.OptNone[int]()
		if machine.Spec.Template != nil {
			quantity, present := machine.Spec.Template.Spec.Domain.Resources.Requests[k8score.ResourceCPU]
			if present {
				cpuRequestMillis.Set(int(quantity.MilliValue()))
			}
		}
		machineStarting := machine.Spec.Template != nil && machine.Spec.RunStrategy != nil && *machine.Spec.RunStrategy == kvcore.RunStrategyAlways
		instanceFinished := hasInstance && (instance.Status.Phase == kvcore.Succeeded || instance.Status.Phase == kvcore.Failed)
		machineStarting = machineStarting && !instanceFinished

		if hasInstance && machine.Status.Ready {
			tracker.TrackState(
				shared.JobReplicaState{
					Id:               jobId,
					Rank:             0,
					State:            orc.JobStateRunning,
					Node:             util.OptValue(instance.Status.NodeName),
					CpuRequestMillis: cpuRequestMillis,
				},
			)
		} else if machineStarting {
			node := machine.Spec.Template.Spec.NodeSelector["kubernetes.io/hostname"]
			if hasInstance && instance.Status.NodeName != "" {
				node = instance.Status.NodeName
			}
			tracker.TrackState(shared.JobReplicaState{
				Id:               jobId,
				Rank:             0,
				State:            orc.JobStateInQueue,
				Node:             util.OptStringIfNotEmpty(node),
				Status:           util.OptValue("The virtual machine is starting."),
				CpuRequestMillis: cpuRequestMillis,
			})
		} else {
			tracker.TrackState(
				shared.JobReplicaState{
					Id:     jobId,
					Rank:   0,
					State:  orc.JobStateSuspended,
					Node:   util.OptNone[string](),
					Status: util.OptValue("The virtual machine is powered off."),
				},
			)
		}
	}

	for jobId, job := range jobs {
		if job.Status.State == orc.JobStateInQueue {
			delete(missingVmSince, jobId)
			continue
		}
		if _, ok := seenJobs[jobId]; ok {
			continue
		}

		missingSince, missingBefore := missingVmSince[jobId]
		if !missingBefore {
			missingVmSince[jobId] = time.Now()
		} else if time.Since(missingSince) >= missingVmGracePeriod {
			_, err = KubevirtClient.VirtualMachine(Namespace).Get(
				context.Background(),
				vmName(jobId, 0),
				metav1.GetOptions{},
			)
			delete(missingVmSince, jobId)
			if k8serrors.IsNotFound(err) {
				tracker.TrackState(shared.JobReplicaState{
					Id:    jobId,
					Rank:  0,
					State: orc.JobStateSuccess,
				})
				tracker.RequestCleanupWithoutResourceDeletion(jobId)
				continue
			}
			if err != nil {
				log.Info("Failed to confirm missing virtual machine %s: %v", vmName(jobId, 0), err)
			}
		}

		tracker.TrackState(shared.JobReplicaState{
			Id:     jobId,
			Rank:   0,
			State:  orc.JobStateSuspended,
			Node:   util.OptNone[string](),
			Status: util.OptValue("The virtual machine is powered off."),
		})
	}
}

func cleanupReleasedPVs() {
	now := time.Now()
	if now.Before(releasedPvCleanupState.nextScan) {
		return
	}
	releasedPvCleanupState.nextScan = now.Add(releasedPvScanInterval)

	pvs, err := shared.K8sClient.CoreV1().PersistentVolumes().List(context.Background(), metav1.ListOptions{})
	if err != nil {
		if isPermissionError(err) {
			if !releasedPvCleanupState.scanPermissionLogged {
				log.Warn("Unable to scan persistent volumes for released VM volumes: %v", err)
				releasedPvCleanupState.scanPermissionLogged = true
			}
			releasedPvCleanupState.nextScan = now.Add(releasedPvPermissionInterval)
		} else {
			log.Warn("Unable to scan persistent volumes for released VM volumes: %v", err)
		}
		return
	}
	releasedPvCleanupState.scanPermissionLogged = false

	deletePermissionFailed := false
	deleteSucceeded := false
	for i := range pvs.Items {
		pv := &pvs.Items[i]
		if pv.Status.Phase != k8score.VolumeReleased || pv.Spec.ClaimRef == nil ||
			pv.Spec.ClaimRef.Namespace != Namespace {
			continue
		}

		if _, _, ok := vmNameToJobIdAndRank(pv.Name); !ok {
			continue
		}

		err := shared.K8sClient.CoreV1().PersistentVolumes().Delete(context.Background(), pv.Name, metav1.DeleteOptions{})
		if err == nil || k8serrors.IsNotFound(err) {
			deleteSucceeded = true
			continue
		}

		if isPermissionError(err) {
			if !releasedPvCleanupState.deletePermissionLogged {
				log.Warn("Unable to delete released persistent volume %q: %v", pv.Name, err)
				releasedPvCleanupState.deletePermissionLogged = true
			}
			releasedPvCleanupState.nextScan = now.Add(releasedPvPermissionInterval)
			deletePermissionFailed = true
			break
		}

		log.Warn("Unable to delete released persistent volume %q: %v", pv.Name, err)
	}

	if deleteSucceeded && !deletePermissionFailed {
		releasedPvCleanupState.deletePermissionLogged = false
	}
}

func isPermissionError(err error) bool {
	return k8serrors.IsForbidden(err) || k8serrors.IsUnauthorized(err)
}

func OnStart(jobs []orc.Job) {

}
