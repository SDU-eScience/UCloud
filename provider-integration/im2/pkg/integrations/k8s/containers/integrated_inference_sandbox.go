package containers

import (
	"encoding/json"
	"time"

	core "k8s.io/api/core/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const inferenceSandboxInactivityDuration = 15 * time.Minute

const integratedInferenceSandboxAppName = shared.InferenceSandboxAppName

func initIntegratedInferenceSandbox() {
	if ServiceConfig.Compute.IntegratedTerminal.Enabled {
		IApps[integratedInferenceSandboxAppName] = ContainerIAppHandler{
			Flags:                           controller.IntegratedAppInternal,
			RetrieveDefaultConfiguration:    integratedSandboxRetrieveDefaultConfiguration,
			ShouldRun:                       inferenceSandboxShouldRun,
			MutateJobNonPersistent:          inferenceSandboxMutateJobNonPersistent,
			MutateJobSpecBeforeRegistration: inferenceSandboxMutateJobBeforeRegistration,
			MutatePod:                       inferenceSandboxMutatePod,
			ValidateConfiguration:           integratedSandboxValidateConfiguration,
		}
	}
}

func inferenceSandboxMutateJobBeforeRegistration(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
	if err := integratedSandboxMutateJobBeforeRegistration("Inference sandbox", spec); err != nil {
		return err
	}
	spec.Product.Category = shared.IntegratedTerminalAppName
	return nil
}

func inferenceSandboxMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	return integratedSandboxMutatePod(integratedTerminalDimensions, integratedTerminalImage, pod)
}

func inferenceSandboxMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	integratedSandboxMutateJobNonPersistent(job, configuration, true)
}

func inferenceSandboxShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	return integratedSandboxShouldRun(job, configuration, true, shared.InferenceSandboxLeaseUntil, func(owner orc.ResourceOwner) {
		_ = shared.InferenceSandboxLease(owner, inferenceSandboxInactivityDuration)
	})
}
