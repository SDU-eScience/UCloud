package containers

import (
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
)

func Monitor(tracker shared.JobTracker, jobs map[string]*orc.Job) {}
