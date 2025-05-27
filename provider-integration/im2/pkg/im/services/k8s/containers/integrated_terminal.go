package containers

import (
	"encoding/json"
	core "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	"sync"
	"sync/atomic"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var integratedTerminalDimensions = shared.SchedulerDimensions{
	CpuMillis:     500,
	MemoryInBytes: 1000 * 1000 * 1000 * 2,
	Gpu:           0,
}

const itermInactivityDuration = 15 * time.Minute

const integratedTerminalAppName = "terminal"

var integratedTerminalImage = ""

type iappTermConfig struct {
	Folders []string
}

var iappTermLastKeyPressMutex = sync.Mutex{}
var iappTermLastKeyPress = map[string]*atomic.Int64{}

func iappGetLastKeyPress(jobId string, defaultValue time.Time) *atomic.Int64 {
	iappTermLastKeyPressMutex.Lock()
	result, ok := iappTermLastKeyPress[jobId]
	if !ok {
		result = &atomic.Int64{}
		result.Store(defaultValue.UnixMilli())
		iappTermLastKeyPress[jobId] = result
	}
	iappTermLastKeyPressMutex.Unlock()
	return result
}

func initIntegratedTerminal() {
	shared.RegisterJobDimensionMapper(integratedTerminalAppName, func(job *orc.Job) shared.SchedulerDimensions {
		return integratedTerminalDimensions
	})

	group, err := orc.FindGroupByApplication("terminal-ubuntu")
	if err == nil {
		apps := group.Status.Applications
		if len(apps) > 0 {
			integratedTerminalImage = apps[0].Invocation.Tool.Tool.Description.Image
		}
	}

	if integratedTerminalImage == "" {
		log.Info("Failed to find expected integrated terminal app image")
		integratedTerminalImage = "ubuntu:24.04"
	}

	if ServiceConfig.Compute.IntegratedTerminal.Enabled {
		IApps[integratedTerminalAppName] = ContainerIAppHandler{
			Flags:                           ctrl.IntegratedAppInternal,
			RetrieveDefaultConfiguration:    itermRetrieveDefaultConfiguration,
			ShouldRun:                       itermShouldRun,
			MutateJobNonPersistent:          itermMutateJobNonPersistent,
			MutateJobSpecBeforeRegistration: itermMutateJobBeforeRegistration,
			MutatePod:                       itermMutatePod,
			ValidateConfiguration:           itermValidateConfiguration,
		}
	}
}

func itermValidateConfiguration(job *orc.Job, configuration json.RawMessage) error {
	var config iappTermConfig
	return json.Unmarshal(configuration, &config)
}

func itermMutateJobBeforeRegistration(owner orc.ResourceOwner, spec *orc.JobSpecification) error {
	spec.Name = "Integrated terminal"
	spec.Application = orc.NameAndVersion{
		Name:    "unknown",
		Version: "unknown",
	}
	return nil
}

func itermMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) error {
	podSpec := &pod.Spec
	for i := 0; i < len(podSpec.Containers); i++ {
		container := &podSpec.Containers[i]

		if container.Name == ContainerUserJob {
			container.Resources.Requests = map[core.ResourceName]resource.Quantity{
				core.ResourceCPU:    *resource.NewScaledQuantity(int64(integratedTerminalDimensions.CpuMillis), resource.Milli),
				core.ResourceMemory: *resource.NewScaledQuantity(int64(integratedTerminalDimensions.MemoryInBytes), 0),
			}

			container.Resources.Limits = map[core.ResourceName]resource.Quantity{
				core.ResourceCPU:    *resource.NewScaledQuantity(int64(1000), resource.Milli),
				core.ResourceMemory: *resource.NewScaledQuantity(int64(integratedTerminalDimensions.MemoryInBytes), 0),
			}

			container.SecurityContext.AllowPrivilegeEscalation = util.BoolPointer(true)
			container.SecurityContext.RunAsNonRoot = util.Pointer(false)
			container.Image = integratedTerminalImage
			container.Command = []string{"sleep", "inf"}
		}
	}

	return nil
}

func itermMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	var config iappTermConfig
	err := json.Unmarshal(configuration, &config)
	if err != nil {
		return
	}

	appInvocation := &job.Status.ResolvedApplication.Invocation
	appInvocation.Environment = map[string]orc.InvocationParameter{}

	spec := &job.Specification

	spec.Parameters = map[string]orc.AppParameterValue{}
	spec.Resources = nil

	for i := 0; i < len(config.Folders); i++ {
		folder := config.Folders[i]
		driveId := util.GetOptionalElement(util.Components(folder), 0).Value
		if ctrl.CanUseDrive(job.Owner, driveId, false) {
			spec.Resources = append(spec.Resources, orc.AppParameterValueFile(folder, false))
		} else if ctrl.CanUseDrive(job.Owner, driveId, true) {
			spec.Resources = append(spec.Resources, orc.AppParameterValueFile(folder, true))
		}
	}
}

func itermRetrieveDefaultConfiguration(owner orc.ResourceOwner) json.RawMessage {
	return json.RawMessage("{}")
}

func itermShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	var config iappTermConfig
	err := json.Unmarshal(configuration, &config)
	if err != nil {
		return false
	}

	if len(config.Folders) == 0 {
		return false
	}

	ts := time.UnixMilli(
		iappGetLastKeyPress(
			job.Id,
			job.Status.StartedAt.GetOrDefault(fnd.Timestamp(time.Now())).Time(),
		).Load(),
	)

	if time.Now().Sub(ts) > itermInactivityDuration {
		return false
	}

	return true
}
