package containers

import (
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"

	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func containersInspectWebSession(
	job *orc.Job,
	rank int,
	suffix string,
	target config.HostInfo,
	tls bool,
) controller.WebSessionDiagnostics {
	result := controller.WebSessionDiagnostics{
		AppPort:       containersResolveWebPort(job, rank, suffix),
		ProbeStatus:   shared.ProbeTcpTarget(target),
		LogsAvailable: true,
	}

	podName := idAndRankToPodName(job.Id, rank)
	pod, ok := shared.JobPods.Retrieve(podName)
	if !ok {
		result.PodPhase = "Pending"
		result.PodStateLabel = "Starting"
		result.PodDetail = "The job container is being scheduled or initialized."
	} else {
		containersPopulatePodDiagnostics(pod, &result)
	}

	jobFolder, _, httpErr := FindJobFolderEx(job, FindJobFolderNoInitFolder)
	if httpErr == nil {
		logPath := filepath.Join(jobFolder, fmt.Sprintf("stdout-%d.log", rank))
		file, opened := filesystem.OpenFile(logPath, unix.O_RDONLY, 0)
		if opened {
			defer func() {
				_ = file.Close()
			}()
			logs, truncated := shared.ReadStdoutLogTail(file, shared.WebDiagnosticsMaxLogBytes)
			result.Logs = logs
			result.LogsTruncated = truncated
		}
	}

	return result
}

func containersResolveWebPort(job *orc.Job, rank int, suffix string) int {
	if job == nil || !job.Status.ResolvedApplication.Present {
		return 0
	}

	port := int(job.Status.ResolvedApplication.Value.Invocation.Web.Value.Port)
	normalizedSuffix := strings.TrimPrefix(suffix, "-")
	if normalizedSuffix == "" {
		return port
	}

	for i := range job.Updates {
		update := &job.Updates[i]
		if !strings.HasPrefix(update.Status.Value, "Target: ") {
			continue
		}
		asJson := strings.TrimPrefix(update.Status.Value, "Target: ")
		var dynTarget orc.DynamicTarget
		err := json.Unmarshal([]byte(asJson), &dynTarget)
		if err != nil {
			continue
		}
		if dynTarget.Rank == rank && controller.ToHostnameSafe(dynTarget.Target) == normalizedSuffix {
			port = dynTarget.Port
		}
	}

	return port
}

func containersPopulatePodDiagnostics(pod *core.Pod, result *controller.WebSessionDiagnostics) {
	result.PodPhase = string(pod.Status.Phase)
	result.PodStateLabel = string(pod.Status.Phase)

	for _, initStatus := range pod.Status.InitContainerStatuses {
		if initStatus.State.Waiting != nil {
			result.PodStateLabel = initStatus.State.Waiting.Reason
			if result.PodStateLabel == "" {
				result.PodStateLabel = "PodInitializing"
			}
			result.PodDetail = initStatus.State.Waiting.Message
			return
		}
		if initStatus.State.Running != nil {
			result.PodStateLabel = "PodInitializing"
			result.PodDetail = "Initialization container is still running."
			return
		}
		if initStatus.State.Terminated != nil && initStatus.State.Terminated.ExitCode != 0 {
			result.PodStateLabel = initStatus.State.Terminated.Reason
			if result.PodStateLabel == "" {
				result.PodStateLabel = "InitError"
			}
			result.PodDetail = fmt.Sprintf(
				"Initialization container exited with code %d.",
				initStatus.State.Terminated.ExitCode,
			)
			return
		}
	}

	for _, status := range pod.Status.ContainerStatuses {
		if status.Name != ContainerUserJob {
			continue
		}
		result.RestartCount = status.RestartCount
		if status.State.Waiting != nil {
			result.PodStateLabel = status.State.Waiting.Reason
			if result.PodStateLabel == "" {
				result.PodStateLabel = "Starting"
			}
			result.PodDetail = status.State.Waiting.Message
			return
		}
		if status.State.Terminated != nil {
			terminated := status.State.Terminated
			result.PodStateLabel = terminated.Reason
			if result.PodStateLabel == "" {
				result.PodStateLabel = "Terminated"
			}
			if terminated.Reason == "OOMKilled" {
				result.PodDetail = "The job container exceeded its memory limit (OOMKilled)."
			} else if terminated.Signal != 0 {
				result.PodDetail = fmt.Sprintf("The job container was terminated by signal %d.", terminated.Signal)
			} else {
				result.PodDetail = fmt.Sprintf("The job container exited with code %d.", terminated.ExitCode)
			}
			return
		}
		if status.State.Running != nil {
			result.PodStateLabel = "Running"
			return
		}
	}
}
