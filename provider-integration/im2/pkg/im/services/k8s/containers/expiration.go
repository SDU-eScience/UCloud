package containers

import (
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type jobRunningTime struct {
	TimeRemaining time.Duration
	TimeConsumed  time.Duration
}

func computeRunningTime(job *orc.Job) jobRunningTime {
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

	rawAlloc := job.Specification.TimeAllocation.GetOrDefault(orc.SimpleDuration{
		Hours: 24 * 365,
	})

	timeAllocation := time.Duration(rawAlloc.ToMillis()) * time.Millisecond

	return jobRunningTime{
		TimeRemaining: timeAllocation - timeConsumed,
		TimeConsumed:  timeConsumed,
	}
}

func extend(request ctrl.JobExtendRequest) error {
	// NOTE(Dan): Scheduler is automatically notified by the shared monitoring loop since it will forward the time
	// allocation from the job.
	return ctrl.TrackRawUpdates([]orc.ResourceUpdateAndId[orc.JobUpdate]{
		{
			Id: request.Job.Id,
			Update: orc.JobUpdate{
				NewTimeAllocation: util.OptValue(request.RequestedTime.ToMillis()),
			},
		},
	})
}
