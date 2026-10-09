package containers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	core "k8s.io/api/core/v1"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	StackDeclaredJobImageParameter = "declaredImage"
	StackDeclaredJobArgsParameter  = "declaredArgs"
	StackDeclaredJobPortParameter  = "declaredPort"

	StackDeclaredJobMountPath = "/etc/ucloud-stack"

	stackDeclaredJobBinaryPath          = "/opt/ucloud-ucx/current"
	stackDeclaredJobReservedApplication = "unknown"
)

func StackDeclaredJobIsDeclared(job *orc.Job) bool {
	if job == nil || job.Specification.Application.Name != stackDeclaredJobReservedApplication {
		return false
	}

	if !strings.EqualFold(strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackController]), "true") {
		return false
	}

	image, hasImage := stackDeclaredJobParameterText(job, StackDeclaredJobImageParameter)
	_, hasArgs := stackDeclaredJobParameterText(job, StackDeclaredJobArgsParameter)
	_, hasPort := stackDeclaredJobParameterInt(job, StackDeclaredJobPortParameter)

	return hasImage && strings.TrimSpace(image) != "" && hasArgs && hasPort
}

func stackDeclaredJobParameterText(job *orc.Job, key string) (string, bool) {
	value, ok := job.Specification.Parameters[key]
	if !ok || value.Type != orc.AppParameterValueTypeText {
		return "", false
	}

	text, ok := value.Value.(string)
	if !ok {
		return "", false
	}

	return text, true
}

func stackDeclaredJobParameterInt(job *orc.Job, key string) (int, bool) {
	value, ok := job.Specification.Parameters[key]
	if !ok || value.Type != orc.AppParameterValueTypeInteger {
		return 0, false
	}

	switch typed := value.Value.(type) {
	case int:
		return typed, true
	case int64:
		return int(typed), true
	case float64:
		return int(typed), true
	case json.Number:
		converted, err := typed.Int64()
		if err != nil {
			return 0, false
		}
		return int(converted), true
	default:
		return 0, false
	}
}

func stackDeclaredJobMutatePod(job *orc.Job, pod *core.Pod) *util.HttpError {
	if !StackDeclaredJobIsDeclared(job) {
		return nil
	}

	image, _ := stackDeclaredJobParameterText(job, StackDeclaredJobImageParameter)
	rawArgs, _ := stackDeclaredJobParameterText(job, StackDeclaredJobArgsParameter)
	port, _ := stackDeclaredJobParameterInt(job, StackDeclaredJobPortParameter)

	if port <= 0 || port > 65535 {
		return util.HttpErr(http.StatusBadRequest, "the declared job has an invalid port")
	}

	args := []string{}
	if rawArgs != "" {
		if err := json.Unmarshal([]byte(rawArgs), &args); err != nil {
			return util.HttpErr(http.StatusBadRequest, "the declared job has invalid arguments")
		}
	}

	env := []core.EnvVar{
		{Name: "UCX_PORT", Value: strconv.Itoa(port)},
		{Name: "UCLOUD_UCX_APP_NAME", Value: strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelUcxAppName])},
		{Name: "UCLOUD_UCX_APP_VERSION", Value: strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelUcxAppVersion])},
		{Name: "UCLOUD_UCX_STACK_ID", Value: strings.TrimSpace(job.Specification.Labels[orc.ResourceLabelStackInstance])},
	}

	for i := range pod.Spec.Containers {
		container := &pod.Spec.Containers[i]
		if container.Name != ContainerUserJob {
			continue
		}

		container.Image = strings.TrimSpace(image)
		container.Command = []string{stackDeclaredJobBinaryPath}
		container.Args = args
		container.Env = append(container.Env, env...)
		container.SecurityContext.RunAsNonRoot = util.BoolPointer(false)
		container.SecurityContext.AllowPrivilegeEscalation = util.BoolPointer(true)
	}

	return nil
}
