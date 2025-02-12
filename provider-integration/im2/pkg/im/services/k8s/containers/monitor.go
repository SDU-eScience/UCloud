package containers

import (
	"context"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func Monitor(tracker shared.JobTracker, jobs map[string]*orc.Job) {
	allPods, err := K8sClient.CoreV1().Pods(Namespace).List(context.Background(), meta.ListOptions{})
	if err != nil {
		log.Info("Failed to fetch pods: %v", err)
		return
	}

	length := len(allPods.Items)
	for i := 0; i < length; i++ {
		pod := &allPods.Items[i]
		idAndRank, ok := podNameToIdAndRank(pod.Name)

		if !ok {
			// This pod is not relevant - Do not process it. Most likely this could be a VMI pod.
			continue
		}

		state := orc.JobStateFailure
		status := "Unexpected state"

		switch pod.Status.Phase {
		case core.PodPending:
			state = orc.JobStateInQueue
			status = "Job is currently in the queue"

		case core.PodRunning:
			state = orc.JobStateRunning
			status = "Job is now running"

		case core.PodSucceeded:
			state = orc.JobStateSuccess
			status = "Job has terminated"

		case core.PodFailed:
			state = orc.JobStateSuccess
			status = "Job has terminated with a non-zero exit code"

		case core.PodUnknown:
			state = orc.JobStateFailure
			status = "Job has failed due to an internal error (#1)"
		}

		tracker.TrackState(shared.JobReplicaState{
			Id:     idAndRank.First,
			Rank:   idAndRank.Second,
			State:  state,
			Node:   util.OptValue(pod.Spec.NodeName),
			Status: util.OptValue(status),
		})
	}
}
