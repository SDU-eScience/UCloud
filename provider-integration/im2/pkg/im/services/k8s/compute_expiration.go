package k8s

import (
	"fmt"
	core "k8s.io/api/core/v1"
	orc "ucloud.dk/pkg/orchestrators"
)

func prepareExpirationOnJobCreate(job *orc.Job, pod *core.Pod) {
	allocation := job.Specification.TimeAllocation
	if allocation.IsSet() {
		pod.ObjectMeta.Annotations[annotationMaxTime] = fmt.Sprint(allocation.Get().ToMillis())
	}
}

const (
	annotationMaxTime  = "ucloud.dk/maxTime"
	annotationExpiry   = "ucloud.dk/expiry"
	annotationJobStart = "ucloud.dk/jobStart"
)
