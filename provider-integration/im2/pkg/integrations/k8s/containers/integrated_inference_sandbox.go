package containers

import (
	"encoding/json"
	"time"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const inferenceSandboxInactivityDuration = 15 * time.Minute

const integratedInferenceSandboxAppName = shared.InferenceSandboxAppName
const inferenceSandboxImage = "dreg.cloud.sdu.dk/ucloud/ucloud-inf-tools:2026.3.118"

func initIntegratedInferenceSandbox() {
	if ServiceConfig.Compute.IntegratedTerminal.Enabled {
		IApps[integratedInferenceSandboxAppName] = ContainerIAppHandler{
			Flags:                           controller.IntegratedAppInternal,
			RetrieveDefaultConfiguration:    integratedSandboxRetrieveDefaultConfiguration,
			ShouldRun:                       inferenceSandboxShouldRun,
			MutateJobNonPersistent:          inferenceSandboxMutateJobNonPersistent,
			MutateJobSpecBeforeRegistration: inferenceSandboxMutateJobBeforeRegistration,
			MutatePod:                       inferenceSandboxMutatePod,
			MutateNetworkPolicy:             inferenceSandboxMutateNetworkPolicy,
			ValidateConfiguration:           integratedSandboxValidateConfiguration,
		}
	}
}

func inferenceSandboxMutateJobBeforeRegistration(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
	if err := integratedSandboxMutateJobBeforeRegistration("Inference sandbox", spec); err != nil {
		return err
	}
	spec.Product.Id = shared.IntegratedTerminalAppName
	spec.Product.Category = shared.IntegratedTerminalAppName
	return nil
}

func inferenceSandboxMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	if err := integratedSandboxMutatePod(integratedTerminalDimensions, inferenceSandboxImage, pod); err != nil {
		return err
	}

	for i := range pod.Spec.Containers {
		container := &pod.Spec.Containers[i]
		if container.Name == ContainerUserJob {
			container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
				Name:      "ucloud-filesystem",
				ReadOnly:  true,
				MountPath: "/mnt/exe",
				SubPath:   shared.ExecutablesDir,
			})
		}
	}

	return nil
}

func inferenceSandboxMutateNetworkPolicy(job *orc.Job, configuration json.RawMessage, firewall *networking.NetworkPolicy, pod *core.Pod) *util.HttpError {
	shared.AllowNetworkToClusterDNS(firewall)
	shared.AllowNetworkToPublicInternet(firewall, []int32{80, 443})
	return nil
}

func inferenceSandboxMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	integratedSandboxMutateJobNonPersistent(job, configuration, true)
}

func inferenceSandboxShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	return integratedSandboxShouldRun(job, configuration, true, shared.InferenceSandboxLeaseUntil, func(owner orc.ResourceOwner) {
		_ = shared.InferenceSandboxLease(owner, inferenceSandboxInactivityDuration)
	})
}
