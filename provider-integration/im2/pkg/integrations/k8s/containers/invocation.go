package containers

import (
	"fmt"
	"path/filepath"
	"strings"

	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	k8score "k8s.io/api/core/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func prepareInvocationOnJobCreate(
	job *orc.Job,
	rank int,
	pod *k8score.Pod,
	container *k8score.Container,
	pathMapperInternalToPod map[string]string,
	jobFolder string,
) {
	app := &job.Status.ResolvedApplication.Value

	invocationParameters := app.Invocation.Invocation
	parametersAndValues := controller.JobFindParamAndValues(job, &app.Invocation, requestDynamicParameters(job.Owner, app))
	environment := app.Invocation.Environment

	ucloudToPod := func(ucloudPath string) string {
		internalPath, ok, _ := filesystem.UCloudToInternal(ucloudPath)
		if ok {
			podPath, ok := pathMapperInternalToPod[internalPath]
			if ok {
				return podPath
			} else {
				internalPath, ok, _ = filesystem.UCloudToInternal(util.Parent(ucloudPath))
				if ok {
					podPath, ok = pathMapperInternalToPod[internalPath]
					if ok {
						return filepath.Join(podPath, util.FileName(ucloudPath))
					}
				}
			}
		}

		return "/dev/null"
	}

	argBuilder := controller.JobDefaultArgBuilder(ucloudToPod)

	log.Info("The sample rate is %v", parametersAndValues["ucMetricSampleRate"]) // TODO remember to remove this

	// Convert license parameters
	for parameterId, parameterAndValue := range parametersAndValues {
		if parameterAndValue.Parameter.Type == orc.ApplicationParameterTypeLicenseServer {
			newParameterAndValue := parameterAndValue
			newParameterAndValue.Value.Id = controller.LicenseBuildParameter(parameterAndValue.Value.Id)
			parametersAndValues[parameterId] = newParameterAndValue
		}
	}

	var actualCommand []string
	for _, param := range invocationParameters {
		commandList := controller.JobBuildParameter(param, parametersAndValues, false, argBuilder, nil)
		for _, cmd := range commandList {
			actualCommand = append(actualCommand, orc.EscapeBash(cmd))
		}
	}

	if len(invocationParameters) == 1 && invocationParameters[0].Type == orc.InvocationParameterTypeJinja {
		actualCommand = handleJinjaInvocation(job, rank, pod, container, argBuilder, parametersAndValues,
			jobFolder, pathMapperInternalToPod)
	}

	path := filepath.Join(jobFolder, fmt.Sprintf("job-%d.sh", rank))
	jobFile, ok := filesystem.OpenFile(path, unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0700)
	_ = jobFile.Chown(filesystem.DefaultUid, filesystem.DefaultUid)
	if ok {
		builder := strings.Builder{}
		builder.WriteString("#!/usr/bin/env bash\n")
		if rank == 0 {
			builder.WriteString("resourceUtilization() {\n\t")
			builder.WriteString("# Collects resource utilization for display in the UI\n\t")
			builder.WriteString("/opt/ucloud/ucmetrics viz &> /dev/null\n")
			builder.WriteString("}\n\n")
			builder.WriteString("resourceUtilization &\n")
		}
		builder.WriteString("trap 'kill $(jobs -p) 2>/dev/null' EXIT\n") // Does this actually work with the new exec invocation?

		builder.WriteString("exec ")
		builder.WriteString(strings.Join(actualCommand, " "))
		builder.WriteString(" &> /work/stdout-$UCLOUD_RANK.log\n")

		_, _ = jobFile.WriteString(builder.String())
		_ = jobFile.Chmod(0755)
	}
	_ = jobFile.Close()

	container.Command = []string{fmt.Sprintf("/work/job-%d.sh", rank)}

	for k, param := range environment {
		commandList := controller.JobBuildParameter(param, parametersAndValues, false, argBuilder, nil)
		envValue := strings.Join(commandList, " ")
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  k,
			Value: envValue,
		})
	}

	sampleRate, ok := parametersAndValues["ucMetricSampleRate"]
	if ok {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  "UCLOUD_METRICS_SAMPLE_INTERVAL",
			Value: sampleRate.Value.Value.(string),
		})
	}

	openedFile := job.Specification.OpenedFile
	if openedFile != "" {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  "UCLOUD_OPEN_WITH_FILE",
			Value: ucloudToPod(openedFile),
		})
	}

	container.Env = append(container.Env, k8score.EnvVar{
		Name:  "UCLOUD_JOB_ID",
		Value: job.Id,
	})

	replicaNames := []string{
		"UCLOUD_TASK_COUNT",
		"VC_JOB_NUM",
	}
	for _, name := range replicaNames {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  name,
			Value: fmt.Sprint(job.Specification.Replicas),
		})
	}

	rankNames := []string{
		"VK_TASK_INDEX",
		"VC_TASK_INDEX",
		"UCLOUD_RANK",
	}
	for _, name := range rankNames {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  name,
			Value: fmt.Sprint(rank),
		})
	}

	ingress := serverFindIngress(job, rank, util.OptNone[string]())
	ingressNames := []string{
		"BASE_URL",
		"UCLOUD_BASE_URL",
	}
	for _, name := range ingressNames {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  name,
			Value: fmt.Sprintf("https://%s", ingress.TargetDomain),
		})
	}
}

func handleJinjaInvocation(
	job *orc.Job,
	rank int,
	pod *k8score.Pod,
	container *k8score.Container,
	builder controller.JobArgBuilder,
	parametersAndValues map[string]controller.ParamAndValue,
	jobFolder string,
	pathMapperInternalToPod map[string]string,
) []string {
	// Handle generation of Jinja templated scripts.
	//
	// NOTE(Dan): Unlike the Slurm integration, the Kubernetes integration runs the Jinja processing in an
	// init container. This is quite crucial for security given that the script itself is a user defined
	// program. The execution of this script must be sandboxed in a location we know is secure. In the Slurm
	// integration, that is the IM/User instance. For the Kubernetes integration, that is an init container.

	// Prepare init container
	// -----------------------------------------------------------------------------------------------------------------
	pod.Spec.InitContainers = append(pod.Spec.InitContainers, k8score.Container{})
	jinjaContainer := &pod.Spec.InitContainers[len(pod.Spec.InitContainers)-1]

	jinjaContainer.Name = "script-generation"
	jinjaContainer.Image = "dreg.cloud.sdu.dk/ucloud/im2:2025.3.83" // remember to update when needed

	subpath, ok := strings.CutPrefix(jobFolder, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
	if ok {
		jinjaContainer.VolumeMounts = append(jinjaContainer.VolumeMounts, k8score.VolumeMount{
			Name:      "ucloud-filesystem",
			MountPath: "/work",
			SubPath:   subpath,
		})
	}

	outputScriptPath := fmt.Sprintf("/work/.script-generated-%d.sh", rank)

	jinjaContainer.Command = []string{
		"ucloud",
		"script-gen",
		"/work/.script-template.j2",
		"/work/.script-params.yaml",
		outputScriptPath,
		fmt.Sprintf("/work/.script-targets-%d.yaml", rank),
	}

	// NOTE(Dan): Used by the script-gen to replace the dummy value in the ucloud rank variable.
	jinjaContainer.Env = []k8score.EnvVar{
		{
			Name:  "UCLOUD_RANK",
			Value: fmt.Sprint(rank),
		},
	}

	// Prepare template
	// -----------------------------------------------------------------------------------------------------------------
	app := &job.Status.ResolvedApplication.Value
	invocationParameters := app.Invocation.Invocation

	tpl := invocationParameters[0].InvocationParameterJinja.Template
	for _, v := range parametersAndValues {
		if v.Value.Type == orc.AppParameterValueTypeWorkflow {
			jobTpl := v.Value.Specification.Job
			if jobTpl.Present {
				if len(tpl) > 0 {
					tpl += "\n"
				}
				tpl += jobTpl.Value
			}
		}
	}

	// Prepare parameters file
	// -----------------------------------------------------------------------------------------------------------------
	jinjaParameters := make(map[string]any)
	for name, pv := range parametersAndValues {
		param := pv.Parameter
		value := pv.Value

		var output any

		switch param.Type {
		case orc.ApplicationParameterTypeFloatingPoint:
			output = value.Value

		case orc.ApplicationParameterTypeInteger:
			output = value.Value

		case orc.ApplicationParameterTypeBoolean:
			output = value.Value.(bool)

		case orc.ApplicationParameterTypeWorkflow:
			output = nil

		default:
			output = builder(pv)
		}

		if output != nil {
			jinjaParameters[name] = output
		}
	}

	{
		ucloudCtx := map[string]any{}
		ucloudCtx["jobId"] = job.Id

		{
			product := &job.Status.ResolvedProduct.Value
			machine := map[string]any{}

			machine["name"] = product.Name
			machine["category"] = product.Category.Name

			machine["cpu"] = product.Cpu
			machine["cpuModel"] = product.CpuModel

			machine["memoryInGigs"] = product.MemoryInGigs
			machine["memoryModel"] = product.MemoryModel

			machine["gpu"] = product.Gpu
			machine["gpuModel"] = product.GpuModel

			ucloudCtx["machine"] = machine
		}

		ucloudCtx["nodes"] = job.Specification.Replicas
		ucloudCtx["rank"] = 0 // replaced by script-gen

		{
			app := &job.Status.ResolvedApplication.Value
			appCtx := map[string]any{}

			appCtx["name"] = app.Metadata.Name
			appCtx["version"] = app.Metadata.Version

			ucloudCtx["application"] = appCtx
		}

		jinjaParameters["ucloud"] = ucloudCtx
	}

	paramsYaml, err := yaml.Marshal(util.SanitizeMapForSerialization(jinjaParameters))
	if err != nil {
		paramsYaml = []byte("")
	}

	// Write script files
	// -----------------------------------------------------------------------------------------------------------------
	templateFile, ok := filesystem.OpenFile(filepath.Join(jobFolder, ".script-template.j2"), unix.O_WRONLY|unix.O_CREAT, 0600)
	if ok {
		_ = templateFile.Chown(filesystem.DefaultUid, filesystem.DefaultUid)
		_, _ = templateFile.Write([]byte(tpl))
		_ = templateFile.Close()
	}

	paramsFile, ok := filesystem.OpenFile(filepath.Join(jobFolder, ".script-params.yaml"), unix.O_WRONLY|unix.O_CREAT, 0600)
	if ok {
		_ = paramsFile.Chown(filesystem.DefaultUid, filesystem.DefaultUid)
		_, _ = paramsFile.Write(paramsYaml)
		_ = paramsFile.Close()
	}

	return []string{outputScriptPath}
}
