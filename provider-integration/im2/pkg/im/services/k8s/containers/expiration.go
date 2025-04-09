package containers

import (
	ctrl "ucloud.dk/pkg/im/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

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
