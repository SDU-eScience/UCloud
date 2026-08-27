package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	k8score "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
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
	if InitScriptImagesConsumesScript(job.Id) {
		delete(parametersAndValues, "initScript")
		delete(parametersAndValues, "ucCacheInitScript")
	}
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

	sampleRate := ""
	sampleRateParam, hasSampleRateParam := parametersAndValues["ucMetricSampleRate"]
	if hasSampleRateParam {
		sampleRate, _ = sampleRateParam.Value.Value.(string)
	} else {
		sampleRate = "0ms"
	}

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

	initScript, hasInitScript := job.Specification.Labels[orc.ResourceLabelInitScript]

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

		if hasInitScript {
			builder.WriteString("bash ")
			builder.WriteString(orc.EscapeBash(initScript))
			builder.WriteString("\n")
		}

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

	container.Env = append(container.Env, k8score.EnvVar{
		Name:  "UCLOUD_METRICS_SAMPLE_INTERVAL",
		Value: sampleRate,
	})

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

	appendInferenceServerEnvVars(container, job)

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
	if len(ingress) > 0 {
		ingressNames := []string{
			"BASE_URL",
			"UCLOUD_BASE_URL",
		}
		for _, name := range ingressNames {
			container.Env = append(container.Env, k8score.EnvVar{
				Name:  name,
				Value: fmt.Sprintf("https://%s", ingress[0].TargetDomain),
			})
		}
	}
}

type inferenceServerEnvVar struct {
	Server string `json:"server"`
	Token  string `json:"token"`
}

func appendInferenceServerEnvVars(container *k8score.Container, job *orc.Job) {
	if inferenceServers, ok := inferenceServersEnvValue(job); ok {
		container.Env = append(container.Env, k8score.EnvVar{
			Name:  "UCLOUD_INFERENCE_SERVERS",
			Value: inferenceServers,
		})
	}
	for idx, server := range inferenceServersFromJob(job) {
		container.Env = append(container.Env,
			k8score.EnvVar{
				Name:  fmt.Sprintf("UCLOUD_INFERENCE_SERVER_BASE_%d", idx),
				Value: server.Server,
			},
			k8score.EnvVar{
				Name:  fmt.Sprintf("UCLOUD_INFERENCE_SERVER_TOKEN_%d", idx),
				Value: server.Token,
			},
		)
	}
}

func inferenceServersEnvValue(job *orc.Job) (string, bool) {
	servers := inferenceServersFromJob(job)
	if len(servers) == 0 {
		return "", false
	}

	value, err := json.Marshal(servers)
	if err != nil {
		return "", false
	}
	return string(value), true
}

func inferenceServersFromJob(job *orc.Job) []inferenceServerEnvVar {
	servers := []inferenceServerEnvVar{}
	for _, resource := range job.Specification.Resources {
		if resource.Type != orc.AppParameterValueTypeApiServer || resource.TokenType != "Inference" {
			continue
		}

		servers = append(servers, inferenceServerEnvVar{
			Server: resource.Server,
			Token:  resource.Token,
		})
	}

	return servers
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

	template, parametersYaml := containersPrepareJinjaInvocation(job, builder, parametersAndValues)

	// Write script files
	// -----------------------------------------------------------------------------------------------------------------
	templateFile, ok := filesystem.OpenFile(
		filepath.Join(jobFolder, ".script-template.j2"),
		unix.O_WRONLY|unix.O_CREAT,
		0600,
	)
	if ok {
		_ = templateFile.Chown(filesystem.DefaultUid, filesystem.DefaultUid)
		_, _ = templateFile.Write([]byte(template))
		_ = templateFile.Close()
	}

	paramsFile, ok := filesystem.OpenFile(
		filepath.Join(jobFolder, ".script-params.yaml"),
		unix.O_WRONLY|unix.O_CREAT,
		0600,
	)
	if ok {
		_ = paramsFile.Chown(filesystem.DefaultUid, filesystem.DefaultUid)
		_, _ = paramsFile.Write(parametersYaml)
		_ = paramsFile.Close()
	}

	return []string{outputScriptPath}
}

func containersPrepareJinjaInvocation(
	job *orc.Job,
	builder controller.JobArgBuilder,
	parametersAndValues map[string]controller.ParamAndValue,
) (string, []byte) {
	app := &job.Status.ResolvedApplication.Value
	template := app.Invocation.Invocation[0].InvocationParameterJinja.Template
	for _, parameterAndValue := range parametersAndValues {
		if parameterAndValue.Value.Type == orc.AppParameterValueTypeWorkflow {
			jobTemplate := parameterAndValue.Value.Specification.Job
			if jobTemplate.Present {
				if len(template) > 0 {
					template += "\n"
				}
				template += jobTemplate.Value
			}
		}
	}

	// Prepare parameters file
	// -----------------------------------------------------------------------------------------------------------------
	jinjaParameters := make(map[string]any)
	for name, parameterAndValue := range parametersAndValues {
		parameter := parameterAndValue.Parameter
		value := parameterAndValue.Value

		var output any

		switch parameter.Type {
		case orc.ApplicationParameterTypeFloatingPoint:
			output = value.Value

		case orc.ApplicationParameterTypeInteger:
			output = value.Value

		case orc.ApplicationParameterTypeBoolean:
			output = value.Value.(bool)

		case orc.ApplicationParameterTypeWorkflow:
			output = nil

		default:
			output = builder(parameterAndValue)
		}

		if output != nil {
			jinjaParameters[name] = output
		}
	}

	{
		ucloudContext := map[string]any{}
		ucloudContext["jobId"] = job.Id

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

		ucloudContext["machine"] = machine
		ucloudContext["nodes"] = job.Specification.Replicas
		ucloudContext["rank"] = 0 // replaced by script-gen

		applicationContext := map[string]any{}
		applicationContext["name"] = app.Metadata.Name
		applicationContext["version"] = app.Metadata.Version

		ucloudContext["application"] = applicationContext
		jinjaParameters["ucloud"] = ucloudContext
	}

	parametersYaml, err := yaml.Marshal(util.SanitizeMapForSerialization(jinjaParameters))
	if err != nil {
		parametersYaml = []byte("")
	}
	return template, parametersYaml
}

func containersRenderInvocation(job *orc.Job) (string, *util.HttpError) {
	internalJobFolder := filepath.Join(ServiceConfig.FileSystem.MountPoint, ".preview", job.Id)
	mounts, mountsValid := calculateMounts(job, internalJobFolder)
	if !mountsValid {
		return "", util.HttpErr(http.StatusBadRequest, "Unable to use these folders together")
	}
	internalToPod := map[string]string{}
	for internalPath, folder := range mounts.Folders {
		internalToPod[internalPath] = folder.PodPath
	}
	ucloudToPod := func(ucloudPath string) string {
		internalPath, found, _ := filesystem.UCloudToInternal(ucloudPath)
		if !found {
			return "/dev/null"
		}
		if podPath, present := internalToPod[internalPath]; present {
			return podPath
		}

		internalPath, found, _ = filesystem.UCloudToInternal(util.Parent(ucloudPath))
		if !found {
			return "/dev/null"
		}
		if podPath, present := internalToPod[internalPath]; present {
			return filepath.Join(podPath, util.FileName(ucloudPath))
		}
		return "/dev/null"
	}
	builder := controller.JobDefaultArgBuilder(ucloudToPod)
	app := &job.Status.ResolvedApplication.Value
	parametersAndValues := controller.JobFindParamAndValues(
		job,
		&app.Invocation,
		requestDynamicParameters(job.Owner, app),
	)
	licenseSecrets := []string{}
	for parameterId, parameterAndValue := range parametersAndValues {
		if parameterAndValue.Parameter.Type == orc.ApplicationParameterTypeLicenseServer {
			licensedParameterAndValue := parameterAndValue
			licensedParameterAndValue.Value.Id = controller.LicenseBuildParameter(parameterAndValue.Value.Id)
			licenseSecrets = append(licenseSecrets, licensedParameterAndValue.Value.Id)
			parametersAndValues[parameterId] = licensedParameterAndValue
		}
	}
	invocation := app.Invocation.Invocation
	isJinjaInvocation := len(invocation) == 1 && invocation[0].Type == orc.InvocationParameterTypeJinja
	if isJinjaInvocation {
		template, parametersYaml := containersPrepareJinjaInvocation(job, builder, parametersAndValues)
		script, err := containersRenderJinjaInvocationInPod(job.Id, template, parametersYaml)
		return containersRedactInvocationSecrets(script, licenseSecrets), err
	}
	var command []string
	for _, parameter := range invocation {
		for _, argument := range controller.JobBuildParameter(parameter, parametersAndValues, false, builder, nil) {
			command = append(command, orc.EscapeBash(argument))
		}
	}
	return containersRedactInvocationSecrets("#!/usr/bin/env bash\nexec "+strings.Join(command, " ")+"\n", licenseSecrets), nil
}

func containersRedactInvocationSecrets(script string, secrets []string) string {
	for _, secret := range secrets {
		if secret != "" {
			script = strings.ReplaceAll(script, secret, "<redacted>")
		}
	}
	return script
}

func containersRenderJinjaInvocationInPod(jobId, template string, parametersYaml []byte) (string, *util.HttpError) {
	podName := strings.ToLower(strings.ReplaceAll(jobId, "_", "-"))
	if len(podName) > 40 {
		podName = podName[len(podName)-40:]
	}
	podName = "invocation-preview-" + strings.Trim(podName, "-")

	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()

	defer func() {
		_ = K8sClient.CoreV1().Pods(Namespace).Delete(context.Background(), podName, meta.DeleteOptions{})
	}()

	pod := &k8score.Pod{
		ObjectMeta: meta.ObjectMeta{
			Name: podName,
			Labels: map[string]string{
				"ucloud.dk/temporary": "invocation-preview",
			},
		},
		Spec: k8score.PodSpec{
			RestartPolicy:                k8score.RestartPolicyNever,
			ActiveDeadlineSeconds:        util.Pointer[int64](20),
			AutomountServiceAccountToken: util.BoolPointer(false),
			Volumes: []k8score.Volume{
				{
					Name: "input",
					VolumeSource: k8score.VolumeSource{
						ConfigMap: &k8score.ConfigMapVolumeSource{
							LocalObjectReference: k8score.LocalObjectReference{
								Name: podName,
							},
						},
					},
				},
				{
					Name: "tmp",
					VolumeSource: k8score.VolumeSource{
						EmptyDir: &k8score.EmptyDirVolumeSource{},
					},
				},
			},
			Containers: []k8score.Container{
				{
					Name:  "render",
					Image: "dreg.cloud.sdu.dk/ucloud/im2:2025.3.83",
					Command: []string{
						"sh",
						"-c",
						"ucloud script-gen /input/template.j2 /input/params.yaml /tmp/output.sh && cat /tmp/output.sh",
					},
					VolumeMounts: []k8score.VolumeMount{
						{
							Name:      "input",
							MountPath: "/input",
							ReadOnly:  true,
						},
						{
							Name:      "tmp",
							MountPath: "/tmp",
						},
					},
					Resources: k8score.ResourceRequirements{
						Requests: k8score.ResourceList{
							k8score.ResourceCPU:    resource.MustParse("25m"),
							k8score.ResourceMemory: resource.MustParse("32Mi"),
						},
						Limits: k8score.ResourceList{
							k8score.ResourceCPU:    resource.MustParse("250m"),
							k8score.ResourceMemory: resource.MustParse("128Mi"),
						},
					},
					SecurityContext: &k8score.SecurityContext{
						RunAsNonRoot:             util.BoolPointer(true),
						RunAsUser:                util.Pointer[int64](filesystem.DefaultUid),
						AllowPrivilegeEscalation: util.BoolPointer(false),
						ReadOnlyRootFilesystem:   util.BoolPointer(true),
						Capabilities: &k8score.Capabilities{
							Drop: []k8score.Capability{"ALL"},
						},
					},
					Env: []k8score.EnvVar{
						{
							Name:  "UCLOUD_RANK",
							Value: "0",
						},
						{
							Name:  "UCLOUD_SCRIPT_GEN_STRICT",
							Value: "1",
						},
					},
				},
			},
		},
	}

	// Create the pod before its ConfigMap. Kubernetes waits for the volume until the ConfigMap exists.
	pod, err := K8sClient.CoreV1().Pods(Namespace).Create(
		ctx,
		pod,
		meta.CreateOptions{},
	)
	if err != nil {
		return "", util.HttpErr(http.StatusBadGateway, "Could not start invocation sandbox: %s", err)
	}

	ownerReference := meta.OwnerReference{
		APIVersion: "v1",
		Kind:       "Pod",
		Name:       pod.Name,
		UID:        pod.UID,
	}
	configMap := &k8score.ConfigMap{
		ObjectMeta: meta.ObjectMeta{
			Name: podName,
			OwnerReferences: []meta.OwnerReference{
				ownerReference,
			},
		},
		Data: map[string]string{
			"template.j2": string(template),
			"params.yaml": string(parametersYaml),
		},
	}

	_, err = K8sClient.CoreV1().ConfigMaps(Namespace).Create(
		ctx,
		configMap,
		meta.CreateOptions{},
	)
	if err != nil {
		return "", util.HttpErr(http.StatusBadGateway, "Could not prepare invocation sandbox: %s", err)
	}

	// Wait for the pod to finish rendering.
	for {
		currentPod, err := K8sClient.CoreV1().Pods(Namespace).Get(ctx, podName, meta.GetOptions{})
		if err != nil {
			return "", util.HttpErr(http.StatusBadGateway, "Invocation sandbox failed: %s", err)
		}
		podFinished := currentPod.Status.Phase == k8score.PodSucceeded || currentPod.Status.Phase == k8score.PodFailed
		if podFinished {
			break
		}
		select {
		case <-ctx.Done():
			return "", util.HttpErr(http.StatusGatewayTimeout, "Invocation sandbox timed out")
		case <-time.After(200 * time.Millisecond):
		}
	}

	// Read the generated script from the pod logs.
	logStream, err := K8sClient.CoreV1().Pods(Namespace).GetLogs(
		podName,
		&k8score.PodLogOptions{
			Container: "render",
		},
	).Stream(ctx)
	if err != nil {
		return "", util.HttpErr(http.StatusBadGateway, "Could not read invocation sandbox output")
	}
	defer logStream.Close()

	scriptOutput, err := io.ReadAll(io.LimitReader(logStream, 1024*1024))
	if err != nil {
		return "", util.HttpErr(http.StatusBadGateway, "Could not read invocation sandbox output")
	}

	// Reject failed rendering even if the pod log request succeeded.
	currentPod, _ := K8sClient.CoreV1().Pods(Namespace).Get(ctx, podName, meta.GetOptions{})
	if currentPod != nil && currentPod.Status.Phase != k8score.PodSucceeded {
		return "", util.HttpErr(http.StatusBadRequest, "Invocation rendering failed")
	}
	if strings.Contains(string(scriptOutput), "Failure during generation of script:") {
		return "", util.HttpErr(http.StatusBadRequest, "Invocation rendering failed")
	}
	return string(scriptOutput), nil
}
