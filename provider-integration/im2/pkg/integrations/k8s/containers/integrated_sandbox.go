package containers

import (
	"encoding/json"
	"time"

	core "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type integratedSandboxConfig = shared.IntegratedSandboxConfig

func integratedSandboxValidateConfiguration(job *orc.Job, configuration json.RawMessage) *util.HttpError {
	var config integratedSandboxConfig
	return util.HttpErrorFromErr(json.Unmarshal(configuration, &config))
}

func integratedSandboxMutateJobBeforeRegistration(name string, spec *orc.JobSpecification) *util.HttpError {
	spec.Name = name
	spec.Application = orc.NameAndVersion{
		Name:    "unknown",
		Version: "unknown",
	}
	return nil
}

func integratedSandboxMutatePod(dimensions shared.SchedulerDimensions, image string, pod *core.Pod) *util.HttpError {
	podSpec := &pod.Spec
	for i := 0; i < len(podSpec.Containers); i++ {
		container := &podSpec.Containers[i]

		if container.Name == ContainerUserJob {
			container.Resources.Requests = map[core.ResourceName]resource.Quantity{
				core.ResourceCPU:    *resource.NewScaledQuantity(int64(dimensions.CpuMillis), resource.Milli),
				core.ResourceMemory: *resource.NewScaledQuantity(int64(dimensions.MemoryInBytes), 0),
			}

			container.Resources.Limits = map[core.ResourceName]resource.Quantity{
				core.ResourceCPU:    *resource.NewScaledQuantity(int64(1000), resource.Milli),
				core.ResourceMemory: *resource.NewScaledQuantity(int64(dimensions.MemoryInBytes), 0),
			}

			container.SecurityContext.AllowPrivilegeEscalation = util.BoolPointer(true)
			container.SecurityContext.RunAsNonRoot = util.Pointer(false)
			container.Image = image
			container.Command = []string{"sleep", "inf"}
		}
	}

	return nil
}

func integratedSandboxMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage, forceReadOnly bool) {
	var config integratedSandboxConfig
	err := json.Unmarshal(configuration, &config)
	if err != nil {
		return
	}

	appInvocation := &job.Status.ResolvedApplication.Value.Invocation
	appInvocation.Environment = map[string]orc.InvocationParameter{}

	spec := &job.Specification
	spec.Parameters = map[string]orc.AppParameterValue{}
	spec.Resources = nil

	for i := 0; i < len(config.Folders); i++ {
		folder := config.Folders[i]
		driveId := util.GetOptionalElement(util.Components(folder), 0).Value
		if controller.DriveCanUse(job.Owner, driveId, false) || (forceReadOnly && controller.DriveCanUse(job.Owner, driveId, true)) {
			spec.Resources = append(spec.Resources, orc.AppParameterValueFile(folder, false))
		} else if !forceReadOnly && controller.DriveCanUse(job.Owner, driveId, true) {
			spec.Resources = append(spec.Resources, orc.AppParameterValueFile(folder, true))
		}
	}
}

func integratedSandboxRetrieveDefaultConfiguration(owner orc.ResourceOwner) json.RawMessage {
	return json.RawMessage("{}")
}

func integratedSandboxShouldRun(
	job *orc.Job,
	configuration json.RawMessage,
	forceReadOnly bool,
	leaseUntil func(owner orc.ResourceOwner) util.Option[time.Time],
	lease func(owner orc.ResourceOwner),
) bool {
	var config integratedSandboxConfig
	err := json.Unmarshal(configuration, &config)
	if err != nil {
		return false
	}

	until := leaseUntil(job.Owner)
	if !until.Present {
		if job.Status.State != orc.JobStateRunning {
			return false
		}

		lease(job.Owner)
		until = leaseUntil(job.Owner)
		if !until.Present {
			return false
		}
	}

	if until.Present && time.Now().After(until.Value) {
		return false
	}

	if len(config.Folders) == 0 {
		return true
	}

	for _, folder := range config.Folders {
		driveId, ok := filesystem.DriveIdFromUCloudPath(folder)
		if !ok {
			return false
		}

		drive, ok := controller.DriveRetrieve(driveId)
		if !ok {
			return false
		}

		storageLocked := controller.ResourceIsLocked(drive.Resource, drive.Specification.Product)
		if storageLocked {
			return false
		}

		if !controller.DriveCanUse(job.Owner, driveId, false) && !(forceReadOnly && controller.DriveCanUse(job.Owner, driveId, true)) {
			return false
		}
	}

	return true
}
