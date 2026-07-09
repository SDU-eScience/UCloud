package containers

import (
	"encoding/json"
	"time"

	core "k8s.io/api/core/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var integratedTerminalDimensions = shared.SchedulerDimensions{
	CpuMillis:     500,
	MemoryInBytes: 1000 * 1000 * 1000 * 2,
	Resources:     map[string]int{},
}

const itermInactivityDuration = 15 * time.Minute

var integratedTerminalImage = ""

const integratedTerminalAppName = shared.IntegratedTerminalAppName

func initIntegratedTerminal() {
	shared.RegisterJobDimensionMapper(integratedTerminalAppName, func(job *orc.Job) shared.SchedulerDimensions {
		return integratedTerminalDimensions
	})

	group, err := orc.AppsFindGroupByApplication.Invoke(orc.AppCatalogFindGroupByApplicationRequest{
		AppName: "terminal-ubuntu",
	})
	if err == nil {
		apps := group.Status.Applications
		if len(apps) > 0 {
			integratedTerminalImage = apps[len(apps)-1].Invocation.Tool.Tool.Value.Description.Image
		}
	}

	if integratedTerminalImage == "" {
		log.Info("Failed to find expected integrated terminal app image")
		integratedTerminalImage = "ubuntu:24.04"
	}

	if ServiceConfig.Compute.IntegratedTerminal.Enabled {
		IApps[integratedTerminalAppName] = ContainerIAppHandler{
			Flags:                           controller.IntegratedAppInternal,
			RetrieveDefaultConfiguration:    integratedSandboxRetrieveDefaultConfiguration,
			ShouldRun:                       itermShouldRun,
			MutateJobNonPersistent:          itermMutateJobNonPersistent,
			MutateJobSpecBeforeRegistration: itermMutateJobBeforeRegistration,
			MutatePod:                       itermMutatePod,
			ValidateConfiguration:           integratedSandboxValidateConfiguration,
		}
	}
}

func itermMutateJobBeforeRegistration(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
	return integratedSandboxMutateJobBeforeRegistration("Integrated terminal", spec)
}

func itermMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	return integratedSandboxMutatePod(integratedTerminalDimensions, integratedTerminalImage, pod)
}

func itermMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	integratedSandboxMutateJobNonPersistent(job, configuration, false)
}

func itermShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	return integratedSandboxShouldRun(job, configuration, false, shared.TerminalLeaseUntil, func(owner orc.ResourceOwner) {
		_ = shared.TerminalLease(owner, itermInactivityDuration)
	})
}
