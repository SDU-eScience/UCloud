package k8s

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	batch "k8s.io/api/batch/v1"
	core "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/types"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/containers"
	"ucloud.dk/pkg/integrations/k8s/registry"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	containerSnapshotLabel                 = "ucloud.dk/containerSnapshot"
	containerSnapshotJobLabel              = "ucloud.dk/containerSnapshotJobId"
	containerSnapshotImageAnnotation       = "ucloud.dk/containerSnapshotImage"
	containerSnapshotTokenAnnotation       = "ucloud.dk/containerSnapshotTokenId"
	containerSnapshotStopAnnotation        = "ucloud.dk/containerSnapshotStopRequested"
	containerSnapshotCleanupAnnotation     = "ucloud.dk/containerSnapshotCleanupRequested"
	containerSnapshotVariantAnnotation     = "ucloud.dk/containerSnapshotVariantId"
	containerSnapshotTaskAnnotation        = "ucloud.dk/containerSnapshotTaskId"
	containerSnapshotBaseNameAnnotation    = "ucloud.dk/containerSnapshotBaseName"
	containerSnapshotBaseVersionAnnotation = "ucloud.dk/containerSnapshotBaseVersion"
	containerSnapshotRequestedByAnnotation = "ucloud.dk/containerSnapshotRequestedBy"
)

var containerSnapshotImagePattern = regexp.MustCompile(`^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$`)

type containerSnapshotResult struct {
	Image string
	Err   string
}

type containerSnapshotExecution struct {
	Done chan containerSnapshotResult
}

var containerSnapshotExecutions = struct {
	sync.Mutex
	Items map[string]*containerSnapshotExecution
}{Items: map[string]*containerSnapshotExecution{}}

var containerSnapshotReservations = struct {
	sync.Mutex
	Items map[string]string
}{Items: map[string]string{}}

func releaseContainerSnapshotReservation(name string) {
	containerSnapshotReservations.Lock()
	delete(containerSnapshotReservations.Items, name)
	containerSnapshotReservations.Unlock()
}

func initContainerSnapshots() {
	jobs, err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).List(
		context.Background(),
		meta.ListOptions{LabelSelector: labels.Set{containerSnapshotLabel: "true"}.AsSelector().String()},
	)
	if err != nil {
		log.Warn("Container snapshots: failed to recover helper jobs: %v", err)
		return
	}
	for i := range jobs.Items {
		monitorContainerSnapshot(jobs.Items[i].Name)
	}
}

func startContainerSnapshot(jobId, image string, rank int) (string, *util.HttpError) {
	return startContainerSnapshotEx(jobId, image, rank, 0, 0, orc.NameAndVersion{}, "", true)
}

func startContainerSnapshotAsync(jobId, image string, rank int, variantId int64, taskId int, baseApplication orc.NameAndVersion, requestedBy string) (string, *util.HttpError) {
	return startContainerSnapshotEx(jobId, image, rank, variantId, taskId, baseApplication, requestedBy, false)
}

func startContainerSnapshotEx(jobId, image string, rank int, variantId int64, taskId int, baseApplication orc.NameAndVersion, requestedBy string, wait bool) (string, *util.HttpError) {
	if !containerSnapshotImagePattern.MatchString(image) {
		return "", util.HttpErr(http.StatusBadRequest, "image must use a lowercase repository path and include a valid tag")
	}

	job, ok := controller.JobRetrieve(jobId)
	if !ok {
		return "", util.HttpErr(http.StatusNotFound, "job not found")
	}
	if !backendIsContainers(job) {
		return "", util.HttpErr(http.StatusBadRequest, "only container jobs can be snapshotted")
	}
	if job.Status.State != orc.JobStateRunning {
		return "", util.HttpErr(http.StatusBadRequest, "job must be running")
	}
	if rank < 0 || rank >= job.Specification.Replicas {
		return "", util.HttpErr(http.StatusBadRequest, "rank must be between 0 and %d", job.Specification.Replicas-1)
	}

	podName := fmt.Sprintf("j-%s-job-%d", job.Id, rank)
	pod, ok := shared.JobPods.Retrieve(podName)
	if !ok {
		return "", util.HttpErr(http.StatusConflict, "job replica is not available")
	}
	if pod.Spec.NodeName == "" {
		return "", util.HttpErr(http.StatusConflict, "job replica has not been assigned to a node")
	}
	containerId := ""
	for _, status := range pod.Status.ContainerStatuses {
		if status.Name == containers.ContainerUserJob && status.State.Running != nil {
			containerId = status.ContainerID
			break
		}
	}
	if containerId == "" {
		return "", util.HttpErr(http.StatusConflict, "job container is not running")
	}
	if _, value, found := strings.Cut(containerId, "://"); found {
		containerId = value
	}

	repository, herr := registry.RepositoryFindDefault(job.Owner)
	if herr != nil {
		return "", herr
	}
	registryServer := registry.Server()
	parsedServer, parseErr := url.Parse(registryServer)
	if parseErr != nil || parsedServer.Host == "" {
		return "", util.ServerHttpError("invalid registry server configuration")
	}
	destination := parsedServer.Host + "/" + repository + "/" + image
	if util.DevelopmentModeEnabled() && shared.ProviderHostname == "" {
		return "", util.ServerHttpError("provider service IP is not available")
	}
	var token registry.SnapshotToken
	name := fmt.Sprintf("snapshot-%s", job.Id)
	deadline := shared.ServiceConfig.Registry.Snapshot.DeadlineSeconds
	backoffLimit := int32(0)
	helpJob := &batch.Job{
		ObjectMeta: meta.ObjectMeta{
			Name:      name,
			Namespace: shared.ServiceConfig.Compute.TaskNamespace,
			Labels: map[string]string{
				containerSnapshotLabel:    "true",
				containerSnapshotJobLabel: job.Id,
			},
			Annotations: map[string]string{
				containerSnapshotImageAnnotation: destination,
			},
		},
		Spec: batch.JobSpec{
			BackoffLimit:          &backoffLimit,
			ActiveDeadlineSeconds: util.Pointer(int64(deadline)),
			Template: core.PodTemplateSpec{
				Spec: core.PodSpec{
					AutomountServiceAccountToken: util.Pointer(false),
					EnableServiceLinks:           util.Pointer(false),
					NodeName:                     pod.Spec.NodeName,
					RestartPolicy:                core.RestartPolicyNever,
					Volumes: []core.Volume{{
						Name: "containerd-socket",
						VolumeSource: core.VolumeSource{HostPath: &core.HostPathVolumeSource{
							Path: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket,
							Type: util.Pointer(core.HostPathSocket),
						}},
					}},
					Containers: []core.Container{{
						Name:            "snapshot",
						Image:           shared.ServiceConfig.Registry.Snapshot.HelperImage,
						ImagePullPolicy: core.PullIfNotPresent,
						Command:         []string{"/bin/sh", "-c"},
						Args: []string{`timeout "$SNAPSHOT_DEADLINE" /bin/sh -c '
set -e
printf %s "$REGISTRY_TOKEN" | nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS login --username ucloud --password-stdin "$REGISTRY_SERVER"
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" commit --pause=true --compression=gzip "$CONTAINER_ID" "$DESTINATION_IMAGE"
cleanup() { nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" image rm "$DESTINATION_IMAGE" >/dev/null 2>&1 || true; }
trap cleanup EXIT
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS push "$DESTINATION_IMAGE"
'`},
						Env: []core.EnvVar{
							{Name: "CONTAINER_ID", Value: containerId},
							{Name: "DESTINATION_IMAGE", Value: destination},
							{Name: "REGISTRY_SERVER", Value: parsedServer.Host},
							{Name: "REGISTRY_TOKEN"},
							{Name: "CONTAINERD_ADDRESS", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket},
							{Name: "CONTAINERD_NAMESPACE", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdNamespace},
							{Name: "SNAPSHOT_DEADLINE", Value: strconv.Itoa(deadline)},
						},
						VolumeMounts: []core.VolumeMount{{
							Name:      "containerd-socket",
							MountPath: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket,
						}},
					}},
				},
			},
		},
	}
	if variantId > 0 {
		helpJob.Annotations[containerSnapshotVariantAnnotation] = strconv.FormatInt(variantId, 10)
		helpJob.Annotations[containerSnapshotTaskAnnotation] = strconv.Itoa(taskId)
		helpJob.Annotations[containerSnapshotBaseNameAnnotation] = baseApplication.Name
		helpJob.Annotations[containerSnapshotBaseVersionAnnotation] = baseApplication.Version
		helpJob.Annotations[containerSnapshotRequestedByAnnotation] = requestedBy
	}
	if util.DevelopmentModeEnabled() {
		helpJob.Spec.Template.Spec.HostAliases = []core.HostAlias{{
			IP: shared.ProviderHostname, Hostnames: []string{shared.ServiceConfig.Registry.Host},
		}}
		helpJob.Spec.Template.Spec.Containers[0].Env = append(
			helpJob.Spec.Template.Spec.Containers[0].Env,
			core.EnvVar{Name: "NERDCTL_REGISTRY_FLAGS", Value: "--insecure-registry"},
		)
	}

	activeSnapshots := shared.BatchBackgroundJobs.List()
	containerSnapshotReservations.Lock()
	if _, reserved := containerSnapshotReservations.Items[name]; reserved {
		containerSnapshotReservations.Unlock()
		return "", util.HttpErr(http.StatusConflict, "a snapshot is already running for this job")
	}
	for _, active := range activeSnapshots {
		if active.DeletionTimestamp == nil &&
			active.Labels[containerSnapshotLabel] == "true" &&
			active.Annotations[containerSnapshotImageAnnotation] == destination {
			containerSnapshotReservations.Unlock()
			return "", util.HttpErr(http.StatusConflict, "a snapshot is already publishing this image tag")
		}
	}
	for _, reservedDestination := range containerSnapshotReservations.Items {
		if reservedDestination == destination {
			containerSnapshotReservations.Unlock()
			return "", util.HttpErr(http.StatusConflict, "a snapshot is already publishing this image tag")
		}
	}
	containerSnapshotReservations.Items[name] = destination
	containerSnapshotReservations.Unlock()

	currentPod, ok := shared.JobPods.Retrieve(podName)
	if !ok || currentPod.DeletionTimestamp != nil {
		releaseContainerSnapshotReservation(name)
		return "", util.HttpErr(http.StatusConflict, "job replica is no longer available")
	}
	token, herr = registry.ApiTokensCreateForSnapshot(job.Owner, time.Hour)
	if herr != nil {
		releaseContainerSnapshotReservation(name)
		return "", herr
	}
	helpJob.Annotations[containerSnapshotTokenAnnotation] = token.Id
	for idx := range helpJob.Spec.Template.Spec.Containers[0].Env {
		if helpJob.Spec.Template.Spec.Containers[0].Env[idx].Name == "REGISTRY_TOKEN" {
			helpJob.Spec.Template.Spec.Containers[0].Env[idx].Value = token.Secret
		}
	}
	_, kerr := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Create(context.Background(), helpJob, meta.CreateOptions{})
	if apierrors.IsAlreadyExists(kerr) {
		releaseContainerSnapshotReservation(name)
		registry.ApiTokensRevoke(token.Id)
		return "", util.HttpErr(http.StatusConflict, "a snapshot is already running for this job")
	}
	if kerr != nil {
		releaseContainerSnapshotReservation(name)
		registry.ApiTokensRevoke(token.Id)
		return "", util.HttpErrorFromErr(kerr)
	}
	message := "Saving the container. Your job is paused while this finishes. This usually takes a few minutes."
	if variantId > 0 {
		message = "Saving your flavor. Your job is paused while this finishes. This usually takes a few minutes."
	}
	_ = controller.JobTrackMessage([]controller.JobMessage{{
		JobId:   job.Id,
		Message: message,
	}})

	execution := monitorContainerSnapshot(name)
	if !wait {
		return destination, nil
	}
	result := <-execution.Done
	if result.Err != "" {
		return "", util.ServerHttpError("container snapshot failed: %s", result.Err)
	}
	return result.Image, nil
}

func monitorContainerSnapshot(name string) *containerSnapshotExecution {
	containerSnapshotExecutions.Lock()
	if existing := containerSnapshotExecutions.Items[name]; existing != nil {
		containerSnapshotExecutions.Unlock()
		return existing
	}
	execution := &containerSnapshotExecution{Done: make(chan containerSnapshotResult, 1)}
	containerSnapshotExecutions.Items[name] = execution
	containerSnapshotExecutions.Unlock()

	go runContainerSnapshotMonitor(name, execution)
	return execution
}

func runContainerSnapshotMonitor(name string, execution *containerSnapshotExecution) {
	result := containerSnapshotResult{}
	namespace := shared.ServiceConfig.Compute.TaskNamespace
	var snapshotJob *batch.Job
	for {
		current, err := shared.K8sClient.BatchV1().Jobs(namespace).Get(context.Background(), name, meta.GetOptions{})
		if err != nil {
			if apierrors.IsNotFound(err) {
				result.Err = "helper job disappeared"
				break
			}
			time.Sleep(2 * time.Second)
			continue
		}
		snapshotJob = current
		result.Image = current.Annotations[containerSnapshotImageAnnotation]
		if current.Status.Succeeded > 0 {
			break
		}
		failed := current.Status.Failed > 0
		for _, condition := range current.Status.Conditions {
			failed = failed || condition.Type == batch.JobFailed && condition.Status == core.ConditionTrue
		}
		if failed {
			result.Err = containerSnapshotLogs(name)
			if result.Err == "" {
				result.Err = "helper job failed"
				currentJson, _ := json.MarshalIndent(current, "", "  ")
				log.Info("Failed job:\n%s", currentJson)
			}
			break
		}
		time.Sleep(2 * time.Second)
	}

	jobId := ""
	stopRequested := false
	cleanupRequested := false
	if snapshotJob != nil {
		jobId = snapshotJob.Labels[containerSnapshotJobLabel]
		stopRequested = snapshotJob.Annotations[containerSnapshotStopAnnotation] == "true"
		cleanupRequested = snapshotJob.Annotations[containerSnapshotCleanupAnnotation] == "true"
	}

	variantId, _ := strconv.ParseInt(snapshotJobAnnotation(snapshotJob, containerSnapshotVariantAnnotation), 10, 64)
	taskId, _ := strconv.Atoi(snapshotJobAnnotation(snapshotJob, containerSnapshotTaskAnnotation))
	if variantId > 0 && taskId > 0 {
		if result.Err == "" {
			if job, ok := controller.JobRetrieve(jobId); ok {
				validated, validationErr := registry.ImagesValidateVariant(job.Owner, result.Image, job.Owner.Project.Present)
				if validationErr != nil {
					result.Err = validationErr.Why
				} else {
					callback := orc.ApplicationVariantCompleteSnapshotRequest{
						VariantId: variantId, TaskId: taskId,
						BaseApplication: orc.NameAndVersion{
							Name:    snapshotJobAnnotation(snapshotJob, containerSnapshotBaseNameAnnotation),
							Version: snapshotJobAnnotation(snapshotJob, containerSnapshotBaseVersionAnnotation),
						},
						RequestedBy: snapshotJobAnnotation(snapshotJob, containerSnapshotRequestedByAnnotation),
						Image:       validated.Image, ImageDigest: validated.ImageDigest,
					}
					for {
						_, callbackErr := orc.ApplicationVariantsControlCompleteSnapshot.Invoke(callback)
						if callbackErr == nil {
							break
						}
						if callbackErr.StatusCode < 500 {
							result.Err = "image was published, but variant activation failed: " + callbackErr.Why
							break
						}
						time.Sleep(2 * time.Second)
					}
				}
			} else {
				result.Err = "source job is no longer available"
			}
		}
		if result.Err != "" {
			failure := orc.ApplicationVariantCompleteSnapshotRequest{
				VariantId: variantId, TaskId: taskId, Failure: util.OptValue(result.Err),
			}
			for {
				_, callbackErr := orc.ApplicationVariantsControlCompleteSnapshot.Invoke(failure)
				if callbackErr == nil || callbackErr.StatusCode < 500 {
					break
				}
				time.Sleep(2 * time.Second)
			}
			postContainerSnapshotTask(
				taskId,
				fnd.TaskStateFailure,
				"We could not save your flavor. "+result.Err,
				util.OptNone[string](),
			)
		} else {
			postContainerSnapshotTask(
				taskId,
				fnd.TaskStateSuccess,
				"Your flavor is ready",
				util.OptValue(fmt.Sprintf("Image location: %s", result.Image)),
			)
		}
	}

	for {
		containerSnapshotExecutions.Lock()
		if current, err := shared.K8sClient.BatchV1().Jobs(namespace).Get(context.Background(), name, meta.GetOptions{}); err == nil {
			snapshotJob = current
		}
		if snapshotJob != nil {
			jobId = snapshotJob.Labels[containerSnapshotJobLabel]
			stopRequested = snapshotJob.Annotations[containerSnapshotStopAnnotation] == "true"
			cleanupRequested = snapshotJob.Annotations[containerSnapshotCleanupAnnotation] == "true"
		}
		propagation := meta.DeletePropagationBackground
		err := shared.K8sClient.BatchV1().Jobs(namespace).Delete(
			context.Background(), name, meta.DeleteOptions{PropagationPolicy: &propagation},
		)
		if err == nil || apierrors.IsNotFound(err) {
			delete(containerSnapshotExecutions.Items, name)
			containerSnapshotExecutions.Unlock()
			break
		}
		containerSnapshotExecutions.Unlock()
		time.Sleep(2 * time.Second)
	}
	if snapshotJob != nil {
		registry.ApiTokensRevoke(snapshotJob.Annotations[containerSnapshotTokenAnnotation])
	}
	releaseContainerSnapshotReservation(name)

	if jobId != "" {
		message := fmt.Sprintf("The container is ready. Image location: %s", result.Image)
		if variantId > 0 {
			message = fmt.Sprintf("Your flavor is ready. Image location: %s", result.Image)
		}
		if result.Err != "" {
			message = fmt.Sprintf("We could not save the container. %s", result.Err)
			if variantId > 0 {
				message = fmt.Sprintf("We could not save your flavor. %s", result.Err)
			}
		}
		_ = controller.JobTrackMessage([]controller.JobMessage{{JobId: jobId, Message: message}})
	}
	if stopRequested && jobId != "" {
		if job, ok := controller.JobRetrieve(jobId); ok {
			_ = terminate(controller.JobTerminateRequest{Job: job, IsCleanup: cleanupRequested})
		}
	}

	execution.Done <- result
	close(execution.Done)
}

func snapshotJobAnnotation(job *batch.Job, key string) string {
	if job == nil {
		return ""
	}
	return job.Annotations[key]
}

func containerSnapshotLogs(name string) string {
	pods, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).List(
		context.Background(), meta.ListOptions{LabelSelector: labels.Set{"job-name": name}.AsSelector().String()},
	)
	if err != nil || len(pods.Items) == 0 {
		return ""
	}
	stream, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).
		GetLogs(pods.Items[0].Name, &core.PodLogOptions{Container: "snapshot", TailLines: util.Pointer(int64(50))}).
		Stream(context.Background())
	if err != nil {
		return ""
	}
	defer util.SilentClose(stream)
	data, _ := io.ReadAll(io.LimitReader(stream, 4000))
	return strings.TrimSpace(string(data))
}

func delayTerminationForContainerSnapshot(request controller.JobTerminateRequest) (bool, *util.HttpError) {
	if !shared.ServiceConfig.Registry.Enabled || !backendIsContainers(request.Job) {
		return false, nil
	}

	name := fmt.Sprintf("snapshot-%s", request.Job.Id)
	key := shared.ServiceConfig.Compute.TaskNamespace + "/" + name
	snapshotJob, ok := shared.BatchBackgroundJobs.Retrieve(key)
	if !ok || snapshotJob.DeletionTimestamp != nil ||
		snapshotJob.Labels[containerSnapshotLabel] != "true" ||
		snapshotJob.Labels[containerSnapshotJobLabel] != request.Job.Id {
		return false, nil
	}

	annotations := map[string]string{containerSnapshotStopAnnotation: "true"}
	if request.IsCleanup {
		annotations[containerSnapshotCleanupAnnotation] = "true"
	}
	patch, _ := json.Marshal(map[string]any{"metadata": map[string]any{"annotations": annotations}})
	_, err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Patch(
		context.Background(), snapshotJob.Name, types.MergePatchType, patch, meta.PatchOptions{},
	)
	if err != nil {
		return true, util.HttpErrorFromErr(err)
	}
	return true, nil
}
