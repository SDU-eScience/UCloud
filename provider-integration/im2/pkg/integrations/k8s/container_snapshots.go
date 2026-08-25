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

type containerSnapshotOperationRequest struct {
	Name          string
	NodeName      string
	ContainerId   string
	Destination   string
	Owner         orc.ResourceOwner
	Deadline      int
	Labels        map[string]string
	Annotations   map[string]string
	AllowExisting bool
}

type containerSnapshotOperationResult struct {
	Image string
	Logs  []byte
	Job   *batch.Job
	Err   string
}

type containerSnapshotOperationExecution struct {
	Done    chan containerSnapshotOperationResult
	Cleanup chan struct{}
}

var containerSnapshotExecutions = struct {
	sync.Mutex
	Items map[string]*containerSnapshotExecution
}{Items: map[string]*containerSnapshotExecution{}}

var containerSnapshotOperationExecutions = struct {
	sync.Mutex
	Items map[string]*containerSnapshotOperationExecution
}{Items: map[string]*containerSnapshotOperationExecution{}}

var containerSnapshotReservations = struct {
	sync.Mutex
	Items map[string]string
}{Items: map[string]string{}}

func releaseContainerSnapshotReservation(name string) {
	containerSnapshotReservations.Lock()
	delete(containerSnapshotReservations.Items, name)
	containerSnapshotReservations.Unlock()
}

func containerSnapshotOperationDestination(path string) (string, *util.HttpError) {
	server, err := url.Parse(registry.Server())
	if err != nil || server.Host == "" {
		return "", util.ServerHttpError("invalid registry server configuration")
	}
	return server.Host + "/" + strings.TrimPrefix(path, "/"), nil
}

// Shared snapshot operation
// ---------------------------------------------------------------------------------------------------------------------
// Both snapshot use-cases publish a running container through the same node-local helper. This operation owns the
// short-lived token, helper job, status polling, failure logs, and cleanup. Callers own validation and result handling.

func containerSnapshotOperationStart(request containerSnapshotOperationRequest) (*containerSnapshotOperationExecution, *util.HttpError) {
	if _, value, found := strings.Cut(request.ContainerId, "://"); found {
		request.ContainerId = value
	}
	if request.Name == "" || request.NodeName == "" || request.ContainerId == "" || request.Destination == "" {
		return nil, util.ServerHttpError("invalid container snapshot request")
	}
	if request.Deadline <= 0 {
		return nil, util.ServerHttpError("invalid container snapshot deadline")
	}
	server, err := url.Parse(registry.Server())
	if err != nil || server.Host == "" {
		return nil, util.ServerHttpError("invalid registry server configuration")
	}
	if util.DevelopmentModeEnabled() && shared.ProviderHostname == "" {
		return nil, util.ServerHttpError("provider service IP is not available")
	}

	containerSnapshotOperationExecutions.Lock()
	existingExecution := containerSnapshotOperationExecutions.Items[request.Name]
	containerSnapshotOperationExecutions.Unlock()
	if existingExecution != nil {
		if request.AllowExisting {
			return existingExecution, nil
		}
		return nil, util.HttpErr(http.StatusConflict, "a snapshot is already running for this job")
	}

	token, herr := registry.ApiTokensCreateForSnapshot(request.Owner, time.Hour)
	if herr != nil {
		return nil, herr
	}
	labels := make(map[string]string, len(request.Labels))
	for key, value := range request.Labels {
		labels[key] = value
	}
	annotations := make(map[string]string, len(request.Annotations)+2)
	for key, value := range request.Annotations {
		annotations[key] = value
	}
	annotations[containerSnapshotImageAnnotation] = request.Destination
	annotations[containerSnapshotTokenAnnotation] = token.Id

	backoffLimit := int32(0)
	helper := &batch.Job{
		ObjectMeta: meta.ObjectMeta{
			Name:        request.Name,
			Namespace:   shared.ServiceConfig.Compute.TaskNamespace,
			Labels:      labels,
			Annotations: annotations,
		},
		Spec: batch.JobSpec{
			BackoffLimit:          &backoffLimit,
			ActiveDeadlineSeconds: util.Pointer(int64(request.Deadline)),
			Template: core.PodTemplateSpec{
				Spec: core.PodSpec{
					AutomountServiceAccountToken: util.Pointer(false),
					EnableServiceLinks:           util.Pointer(false),
					NodeName:                     request.NodeName,
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
						SecurityContext: &core.SecurityContext{SELinuxOptions: &core.SELinuxOptions{Type: "spc_t"}},
						Args: []string{`timeout "$SNAPSHOT_DEADLINE" /bin/sh -c '
set -e
printf %s "$REGISTRY_TOKEN" | nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS login --username ucloud --password-stdin "$REGISTRY_SERVER"
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" commit --pause=true --compression=gzip "$CONTAINER_ID" "$DESTINATION_IMAGE"
cleanup() { nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" image rm "$DESTINATION_IMAGE" >/dev/null 2>&1 || true; }
trap cleanup EXIT
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS push "$DESTINATION_IMAGE"
'`},
						Env: []core.EnvVar{
							{Name: "CONTAINER_ID", Value: request.ContainerId},
							{Name: "DESTINATION_IMAGE", Value: request.Destination},
							{Name: "REGISTRY_SERVER", Value: server.Host},
							{Name: "REGISTRY_TOKEN", Value: token.Secret},
							{Name: "CONTAINERD_ADDRESS", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket},
							{Name: "CONTAINERD_NAMESPACE", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdNamespace},
							{Name: "SNAPSHOT_DEADLINE", Value: strconv.Itoa(request.Deadline)},
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
	if util.DevelopmentModeEnabled() {
		helper.Spec.Template.Spec.HostAliases = []core.HostAlias{{
			IP: shared.ProviderHostname, Hostnames: []string{shared.ServiceConfig.Registry.Host},
		}}
		helper.Spec.Template.Spec.Containers[0].Env = append(
			helper.Spec.Template.Spec.Containers[0].Env,
			core.EnvVar{Name: "NERDCTL_REGISTRY_FLAGS", Value: "--insecure-registry"},
		)
	}

	_, err = shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Create(
		context.Background(),
		helper,
		meta.CreateOptions{},
	)
	if apierrors.IsAlreadyExists(err) && request.AllowExisting {
		registry.ApiTokensRevoke(token.Id)
		return containerSnapshotOperationMonitor(request.Name), nil
	}
	if apierrors.IsAlreadyExists(err) {
		registry.ApiTokensRevoke(token.Id)
		return nil, util.HttpErr(http.StatusConflict, "a snapshot is already running for this job")
	}
	if err != nil {
		registry.ApiTokensRevoke(token.Id)
		return nil, util.HttpErrorFromErr(err)
	}
	return containerSnapshotOperationMonitor(request.Name), nil
}

func containerSnapshotOperationMonitor(name string) *containerSnapshotOperationExecution {
	containerSnapshotOperationExecutions.Lock()
	if existing := containerSnapshotOperationExecutions.Items[name]; existing != nil {
		containerSnapshotOperationExecutions.Unlock()
		return existing
	}
	execution := &containerSnapshotOperationExecution{
		Done:    make(chan containerSnapshotOperationResult, 1),
		Cleanup: make(chan struct{}),
	}
	containerSnapshotOperationExecutions.Items[name] = execution
	containerSnapshotOperationExecutions.Unlock()

	go func() {
		result := containerSnapshotOperationResult{}
		observed := false
		for util.IsAlive {
			current, present := shared.BatchBackgroundJobs.Retrieve(shared.ServiceConfig.Compute.TaskNamespace + "/" + name)
			if !present {
				if observed {
					result.Err = "helper job disappeared"
					break
				}
				time.Sleep(2 * time.Second)
				continue
			}
			observed = true
			result.Job = current
			result.Image = current.Annotations[containerSnapshotImageAnnotation]
			if current.Status.Succeeded > 0 {
				break
			}
			failed := current.Status.Failed > 0
			for _, condition := range current.Status.Conditions {
				failed = failed || condition.Type == batch.JobFailed && condition.Status == core.ConditionTrue
			}
			if failed {
				result.Logs = containerSnapshotOperationLogs(name)
				result.Err = "helper job failed"
				if len(result.Logs) == 0 {
					currentJson, _ := json.MarshalIndent(current, "", "  ")
					log.Info("Failed job:\n%s", currentJson)
				}
				break
			}
			time.Sleep(2 * time.Second)
		}
		if result.Err == "" && (result.Job == nil || result.Job.Status.Succeeded == 0) {
			result.Err = "provider is shutting down"
		}

		execution.Done <- result
		close(execution.Done)
		<-execution.Cleanup

		if current, present := shared.BatchBackgroundJobs.Retrieve(shared.ServiceConfig.Compute.TaskNamespace + "/" + name); present {
			result.Job = current
		}
		if result.Job != nil {
			registry.ApiTokensRevoke(result.Job.Annotations[containerSnapshotTokenAnnotation])
		}
		if util.IsAlive {
			for {
				propagation := meta.DeletePropagationBackground
				deleteErr := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Delete(
					context.Background(),
					name,
					meta.DeleteOptions{PropagationPolicy: &propagation},
				)
				if deleteErr == nil || apierrors.IsNotFound(deleteErr) {
					break
				}
				time.Sleep(2 * time.Second)
			}
		}

		containerSnapshotOperationExecutions.Lock()
		delete(containerSnapshotOperationExecutions.Items, name)
		containerSnapshotOperationExecutions.Unlock()
	}()
	return execution
}

func containerSnapshotOperationLogs(name string) []byte {
	pods, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).List(
		context.Background(),
		meta.ListOptions{LabelSelector: labels.Set{"job-name": name}.AsSelector().String()},
	)
	if err != nil || len(pods.Items) == 0 {
		return nil
	}
	stream, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).
		GetLogs(pods.Items[0].Name, &core.PodLogOptions{Container: "snapshot", TailLines: util.Pointer(int64(1000))}).
		Stream(context.Background())
	if err != nil {
		return nil
	}
	defer util.SilentClose(stream)
	data, _ := io.ReadAll(io.LimitReader(stream, 1024*1024))
	return data
}

func initContainerSnapshots() {
	for _, job := range shared.BatchBackgroundJobs.List() {
		if job.Labels[containerSnapshotLabel] == "true" {
			monitorContainerSnapshot(job.Name)
		}
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
	destination, herr := containerSnapshotOperationDestination(repository + "/" + image)
	if herr != nil {
		return "", herr
	}
	if util.DevelopmentModeEnabled() && shared.ProviderHostname == "" {
		return "", util.ServerHttpError("provider service IP is not available")
	}
	name := fmt.Sprintf("snapshot-%s", job.Id)
	deadline := shared.ServiceConfig.Registry.Snapshot.DeadlineSeconds
	annotations := map[string]string{}
	if variantId > 0 {
		annotations[containerSnapshotVariantAnnotation] = strconv.FormatInt(variantId, 10)
		annotations[containerSnapshotTaskAnnotation] = strconv.Itoa(taskId)
		annotations[containerSnapshotBaseNameAnnotation] = baseApplication.Name
		annotations[containerSnapshotBaseVersionAnnotation] = baseApplication.Version
		annotations[containerSnapshotRequestedByAnnotation] = requestedBy
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
	_, herr = containerSnapshotOperationStart(containerSnapshotOperationRequest{
		Name:        name,
		NodeName:    currentPod.Spec.NodeName,
		ContainerId: containerId,
		Destination: destination,
		Owner:       job.Owner,
		Deadline:    deadline,
		Labels: map[string]string{
			containerSnapshotLabel:    "true",
			containerSnapshotJobLabel: job.Id,
		},
		Annotations: annotations,
	})
	if herr != nil {
		releaseContainerSnapshotReservation(name)
		return "", herr
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
	operation := containerSnapshotOperationMonitor(name)
	operationResult := <-operation.Done
	result := containerSnapshotResult{Image: operationResult.Image, Err: operationResult.Err}
	if len(operationResult.Logs) > 0 {
		logs := operationResult.Logs
		if len(logs) > 4000 {
			logs = logs[len(logs)-4000:]
		}
		result.Err = strings.TrimSpace(string(logs))
	}
	snapshotJob := operationResult.Job

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
				validated, validationErr := registry.ImagesValidateVariant(job.Owner, result.Image, job.Owner.Project.Present, false)
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

	if current, present := shared.BatchBackgroundJobs.Retrieve(shared.ServiceConfig.Compute.TaskNamespace + "/" + name); present {
		snapshotJob = current
	}
	if snapshotJob != nil {
		jobId = snapshotJob.Labels[containerSnapshotJobLabel]
		stopRequested = snapshotJob.Annotations[containerSnapshotStopAnnotation] == "true"
		cleanupRequested = snapshotJob.Annotations[containerSnapshotCleanupAnnotation] == "true"
	}
	close(operation.Cleanup)
	containerSnapshotExecutions.Lock()
	delete(containerSnapshotExecutions.Items, name)
	containerSnapshotExecutions.Unlock()
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
