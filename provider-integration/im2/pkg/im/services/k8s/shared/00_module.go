package shared

import (
	"time"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func Init() {
	initClients()
	initProducts()
	initSsh()
}

func JobIdLabel(jobId string) util.Tuple2[string, string] {
	return util.Tuple2[string, string]{"ucloud.dk/jobId", jobId}
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
