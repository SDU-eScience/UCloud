package shared

import (
	"fmt"
	"slices"
	"time"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/tools/cache"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var JobPods *K8sResourceTracker[*corev1.Pod]

func Init() {
	initClients()
	initProducts()
	initSsh()

	JobPods = NewResourceTracker[*corev1.Pod](
		ServiceConfig.Compute.Namespace,
		func(factory informers.SharedInformerFactory) cache.SharedIndexInformer {
			return factory.Core().V1().Pods().Informer()
		},
		func(resource *corev1.Pod) string {
			return resource.Name
		},
	)
}

func JobIdLabel(jobId string) util.Tuple2[string, string] {
	return util.Tuple2[string, string]{"ucloud.dk/jobId", jobId}
}

func JobRankLabel(rank int) util.Tuple2[string, string] {
	return util.Tuple2[string, string]{"ucloud.dk/rank", fmt.Sprint(rank)}
}

type JobRunningTime struct {
	TimeRemaining util.Option[time.Duration]
	TimeConsumed  time.Duration
}

func ComputeRunningTime(job *orc.Job) JobRunningTime {
	timeConsumed := 0 * time.Second
	currentStart := util.OptNone[time.Time]()

	for _, update := range job.Updates {
		if update.State.Present {
			// NOTE(Dan): It is important that this code can handle duplicate state transitions as these can occur.
			// Thus, we only count a transition to running _if_ we don't already have a currentStart. Similarly,
			// we only increment timeConsumed if the currentStart is present. This will also consume the currentStart.
			if update.State.Value == orc.JobStateRunning && !currentStart.Present {
				currentStart.Set(update.Timestamp.Time())
			}

			if update.State.Value != orc.JobStateRunning && currentStart.Present {
				timeConsumed += update.Timestamp.Time().Sub(currentStart.Value)
				currentStart.Clear()
			}
		}
	}
	if currentStart.Present && job.Status.State == orc.JobStateRunning {
		timeConsumed += time.Now().Sub(currentStart.Value)
	}

	result := JobRunningTime{
		TimeConsumed: timeConsumed,
	}

	rawAlloc := job.Specification.TimeAllocation
	if rawAlloc.Present {
		timeAllocation := time.Duration(rawAlloc.Value.ToMillis()) * time.Millisecond
		result.TimeRemaining.Set(timeAllocation - timeConsumed)
	}

	return result
}

type LockedReason struct {
	Reason string
	Err    *util.HttpError
}

var IsJobLocked func(job *orc.Job) util.Option[LockedReason]
var IsJobLockedEx func(job *orc.Job, jobAnnotations map[string]string) util.Option[LockedReason]

const (
	AnnotationMountedDriveIds        string = "ucloud.dk/mountedDriveIds"
	AnnotationMountedDriveAsReadOnly string = "ucloud.dk/mountedDriveAsReadOnly"
)

func IsSensitiveProject(project string) bool {
	if project == "" {
		return false
	}

	return slices.Contains(ServiceConfig.SensitiveProjects, project)
}
