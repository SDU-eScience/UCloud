package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"path/filepath"
	"slices"
	"strings"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
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

		job, ok := jobs[idAndRank.First]
		if !ok {
			// This pod does not belong to an active job - delete it.
			tracker.RequestCleanup(idAndRank.First)
			continue
		}

		times := shared.ComputeRunningTime(job)
		if times.TimeRemaining.Present && times.TimeRemaining.Value < 0 {
			tracker.RequestCleanup(idAndRank.First)
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

		if pod.ObjectMeta.DeletionTimestamp == nil {
			// Do not track pods which have been deleted.

			tracker.TrackState(shared.JobReplicaState{
				Id:     idAndRank.First,
				Rank:   idAndRank.Second,
				State:  state,
				Node:   util.OptValue(pod.Spec.NodeName),
				Status: util.OptValue(status),
			})
		}
	}
}

func OnStart(jobs []orc.Job) {
	var messages []ctrl.JobMessage

	length := len(jobs)
	for i := 0; i < length; i++ {
		// Common data collection for each job
		// -------------------------------------------------------------------------------------------------------------
		job := &jobs[i]
		jobFolder, _, err := FindJobFolder(job)
		if err != nil {
			continue
		}

		// Dynamic targets
		// -------------------------------------------------------------------------------------------------------------
		dynamicTargets := readDynamicTargets(job, jobFolder)
		for _, target := range dynamicTargets {
			targetAsJson, _ := json.Marshal(target)

			messages = append(messages, ctrl.JobMessage{
				JobId:   job.Id,
				Message: fmt.Sprintf("Target: %s", string(targetAsJson)),
			})
		}
	}

	// Not much we can do in this case
	_ = ctrl.TrackJobMessages(messages)
}

func readDynamicTargets(job *orc.Job, jobFolder string) []orc.DynamicTarget {
	dynamicTargets := map[string]orc.DynamicTarget{}

	var dynTargetLists [][]orc.DynamicTarget
	for rank := 0; rank < job.Specification.Replicas; rank++ {
		file, ok := filesystem.OpenFile(filepath.Join(jobFolder, fmt.Sprintf(".script-targets-%d.yaml", rank)), unix.O_RDONLY, 0)
		if !ok {
			continue
		}

		var data []byte

		info, err := file.Stat()
		if err == nil {
			size := info.Size()
			if size < 1024*1024 {
				data = make([]byte, size)
				n, _ := file.Read(data)
				data = data[:n]
			}
		}
		_ = file.Close()

		var list []orc.DynamicTarget
		_ = yaml.Unmarshal(data, &list)
		dynTargetLists = append(dynTargetLists, list)
	}

	// Merge all the lists but give priority to the self-reported targets. Lower ranks have priority otherwise.
	for rank := len(dynTargetLists) - 1; rank >= 0; rank-- {
		list := dynTargetLists[rank]
		for _, target := range list {
			dynamicTargets[fmt.Sprintf("%v/%v", target.Rank, target.Target)] = target
		}
	}

	for rank := 0; rank < len(dynTargetLists); rank++ {
		list := dynTargetLists[rank]
		for _, target := range list {
			if target.Rank == rank {
				dynamicTargets[fmt.Sprintf("%v/%v", target.Rank, target.Target)] = target
			}
		}
	}

	var targetList []orc.DynamicTarget
	for _, target := range dynamicTargets {
		targetList = append(targetList, target)
	}

	slices.SortFunc(targetList, func(a, b orc.DynamicTarget) int {
		if a.Rank < b.Rank {
			return -1
		} else if a.Rank > b.Rank {
			return 1
		} else {
			return strings.Compare(a.Target, b.Target)
		}
	})

	return targetList
}
