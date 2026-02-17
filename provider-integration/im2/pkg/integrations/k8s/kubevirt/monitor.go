package kubevirt

import (
	"context"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	kvcore "kubevirt.io/api/core/v1"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func Monitor(tracker shared.JobTracker, jobs map[string]*orc.Job) {
	if !Enabled {
		return
	}

	activeInstances, err := KubevirtClient.VirtualMachineInstance(Namespace).List(context.Background(), metav1.ListOptions{})
	if err != nil {
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
		log.Info("Failed to fetch virtual machines: %v", err)
		return
	}

	length = len(activeMachines.Items)
	for i := 0; i < length; i++ {
		machine := &activeMachines.Items[i]
		instance, hasInstance := instancesByName[machine.Name]
		jobId, _, ok := vmNameToJobIdAndRank(machine.Name)
		if !ok {
			continue
		}

		if hasInstance && machine.Status.Ready {
			tracker.TrackState(
				shared.JobReplicaState{
					Id:    jobId,
					Rank:  0,
					State: orc.JobStateRunning,
					Node:  util.OptValue(instance.Status.NodeName),
				},
			)
		} else {
			tracker.TrackState(
				shared.JobReplicaState{
					Id:    jobId,
					Rank:  0,
					State: orc.JobStateSuspended,
					Node:  util.OptNone[string](),
				},
			)
		}
	}
}

func OnStart(jobs []orc.Job) {

}
