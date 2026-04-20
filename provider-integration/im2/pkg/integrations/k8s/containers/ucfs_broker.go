package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"sort"
	"time"

	core "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	ucfsbroker "ucloud.dk/pkg/integrations/k8s/ucfs-broker"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	ucfsBrokerSharedVolumeName = "ucloud-broker-shared"
	ucfsBrokerPodNameSuffix    = "-ucfs-broker"
	ucfsBrokerPodImage         = "alpine:latest"
	ucfsBrokerCpuMillis        = 100
	ucfsBrokerMemoryMegabytes  = 100
	ucfsBrokerSharedBasePath   = "/var/lib/ucloud/ucfs"
)

func ucfsBrokerEnabled() bool {
	return util.DevelopmentModeEnabled()
}

func subtractUcfsBrokerReservation(container *core.Container) {
	adjust := func(resources map[core.ResourceName]resource.Quantity) {
		if resources == nil {
			return
		}

		cpu := resources[core.ResourceCPU]
		cpuMillis := cpu.MilliValue() - ucfsBrokerCpuMillis
		if cpuMillis < 0 {
			cpuMillis = 0
		}
		resources[core.ResourceCPU] = *resource.NewMilliQuantity(cpuMillis, resource.DecimalSI)

		memory := resources[core.ResourceMemory]
		memoryMegabytes := memory.ScaledValue(resource.Mega) - ucfsBrokerMemoryMegabytes
		if memoryMegabytes < 0 {
			memoryMegabytes = 0
		}
		resources[core.ResourceMemory] = *resource.NewScaledQuantity(memoryMegabytes, resource.Mega)
	}

	adjust(container.Resources.Requests)
	adjust(container.Resources.Limits)
}

func ucfsBrokerSharedHostPath(job *orc.Job, rank int) string {
	return filepath.Join(
		ucfsBrokerSharedBasePath,
		filesystem.MountPointSanitize(job.Id),
		fmt.Sprintf("rank-%d", rank),
	)
}

func prepareUcfsBrokerSharedVolume(pod *core.Pod, userContainer *core.Container, job *orc.Job, rank int) string {
	hostPath := ucfsBrokerSharedHostPath(job, rank)

	pod.Spec.Volumes = append(pod.Spec.Volumes, core.Volume{
		Name: ucfsBrokerSharedVolumeName,
		VolumeSource: core.VolumeSource{
			HostPath: &core.HostPathVolumeSource{
				Path: hostPath,
				Type: util.Pointer(core.HostPathDirectoryOrCreate),
			},
		},
	})

	userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
		Name:             ucfsBrokerSharedVolumeName,
		MountPath:        "/ucloud",
		MountPropagation: util.Pointer(core.MountPropagationHostToContainer),
	})

	return hostPath
}

func buildUcfsBrokerManifest(job *orc.Job) ucfsbroker.Manifest {
	candidates := controller.DriveMountCandidatesForJob(job)
	if len(candidates) == 0 {
		return ucfsbroker.Manifest{}
	}

	projectTitle := job.Owner.Project.Value
	hasProject := job.Owner.Project.Present
	if hasProject {
		if project, ok := controller.ProjectRetrieve(job.Owner.Project.Value); ok && project.Specification.Title != "" {
			projectTitle = project.Specification.Title
		}
	}

	workspaceRoot := filesystem.BrokerWorkspaceMountRoot(projectTitle, job.Owner.CreatedBy, hasProject)
	baseNames := make([]string, len(candidates))
	nameCounts := map[string]int{}

	for i, candidate := range candidates {
		baseName := filesystem.MountPointSanitize(candidate.Drive.Specification.Title)

		baseNames[i] = baseName
		nameCounts[baseName]++
	}

	mounts := make([]ucfsbroker.MountSpec, 0, len(candidates))
	for i, candidate := range candidates {
		baseName := baseNames[i]
		targetName := baseName
		if nameCounts[baseName] > 1 {
			targetName = fmt.Sprintf("%s-%s", baseName, filesystem.MountPointSanitize(candidate.Drive.Id))
		}

		sourcePath, ok, _ := filesystem.DriveToLocalPath(&candidate.Drive)
		if !ok {
			log.Warn("Skipping UCloud mount for %s because the internal path could not be resolved", candidate.Drive.Id)
			continue
		}

		mounts = append(mounts, ucfsbroker.MountSpec{
			SourcePath: sourcePath,
			Name:       targetName,
			ReadOnly:   candidate.ReadOnly,
		})
	}

	sort.Slice(mounts, func(i, j int) bool {
		if mounts[i].Name == mounts[j].Name {
			return mounts[i].SourcePath < mounts[j].SourcePath
		}

		return mounts[i].Name < mounts[j].Name
	})

	return ucfsbroker.Manifest{Workspaces: []ucfsbroker.WorkspaceMount{{RootPath: workspaceRoot, Mounts: mounts}}}
}

func prepareUcfsBrokerPod(job *orc.Job, rank int, node string, workerPod *core.Pod) (*core.Pod, error) {
	manifest := buildUcfsBrokerManifest(job)
	manifestData, err := json.Marshal(manifest)
	if err != nil {
		return nil, err
	}

	podName := fmt.Sprintf("%s%s", workerPod.Name, ucfsBrokerPodNameSuffix)
	pod := &core.Pod{
		ObjectMeta: meta.ObjectMeta{
			Name:        podName,
			Namespace:   Namespace,
			Annotations: map[string]string{},
			Labels:      map[string]string{},
			OwnerReferences: []meta.OwnerReference{{
				APIVersion: "v1",
				Kind:       "Pod",
				Name:       workerPod.Name,
				UID:        workerPod.UID,
				Controller: util.BoolPointer(true),
			}},
		},
		Spec: core.PodSpec{
			RestartPolicy:                 core.RestartPolicyNever,
			AutomountServiceAccountToken:  util.Pointer(false),
			EnableServiceLinks:            util.Pointer(false),
			TerminationGracePeriodSeconds: util.Pointer(int64(30)),
			NodeName:                      node,
			Containers:                    []core.Container{},
			Volumes:                       []core.Volume{},
		},
	}

	idLabel := shared.JobIdLabel(job.Id)
	rankLabel := shared.JobRankLabel(rank)
	pod.Labels[idLabel.First] = idLabel.Second
	pod.Labels[rankLabel.First] = rankLabel.Second
	pod.Labels["ucloud.dk/component"] = "ucfs-broker"

	pod.Spec.Volumes = append(pod.Spec.Volumes, core.Volume{
		Name: "ucloud-filesystem",
		VolumeSource: core.VolumeSource{
			PersistentVolumeClaim: &core.PersistentVolumeClaimVolumeSource{
				ClaimName: shared.ServiceConfig.FileSystem.ClaimName,
				ReadOnly:  false,
			},
		},
	})

	pod.Spec.Volumes = append(pod.Spec.Volumes, core.Volume{
		Name: ucfsBrokerSharedVolumeName,
		VolumeSource: core.VolumeSource{
			HostPath: &core.HostPathVolumeSource{
				Path: ucfsBrokerSharedHostPath(job, rank),
				Type: util.Pointer(core.HostPathDirectoryOrCreate),
			},
		},
	})
	pod.Spec.HostPID = true

	container := core.Container{
		Name:            "ucfs-broker",
		Image:           ucfsBrokerPodImage,
		ImagePullPolicy: core.PullIfNotPresent,
		SecurityContext: &core.SecurityContext{
			Privileged: util.BoolPointer(true),
		},
		Resources: core.ResourceRequirements{
			Requests: map[core.ResourceName]resource.Quantity{},
			Limits:   map[core.ResourceName]resource.Quantity{},
		},
		Command: []string{"/opt/ucloud/ucfs-broker"},
		Env: []core.EnvVar{
			{Name: ucfsbroker.EnvManifest, Value: string(manifestData)},
			{Name: ucfsbroker.EnvRoot, Value: "/ucloud"},
			{Name: ucfsbroker.EnvReadyFile, Value: "/ucloud/.broker-ready"},
			{Name: "UCFS_JOB_ID", Value: job.Id},
			{Name: "UCFS_JOB_RANK", Value: fmt.Sprint(rank)},
		},
		VolumeMounts: []core.VolumeMount{
			{
				Name:      "ucloud-filesystem",
				MountPath: shared.ServiceConfig.FileSystem.MountPoint,
			},
			{
				Name:      "ucloud-filesystem",
				MountPath: "/opt/ucloud",
				ReadOnly:  true,
				SubPath:   shared.ExecutablesDir,
			},
			{
				Name:             ucfsBrokerSharedVolumeName,
				MountPath:        "/ucloud",
				MountPropagation: util.Pointer(core.MountPropagationBidirectional),
			},
		},
	}

	container.Resources.Requests[core.ResourceCPU] = *resource.NewMilliQuantity(ucfsBrokerCpuMillis, resource.DecimalSI)
	container.Resources.Limits[core.ResourceCPU] = *resource.NewMilliQuantity(ucfsBrokerCpuMillis, resource.DecimalSI)
	container.Resources.Requests[core.ResourceMemory] = *resource.NewScaledQuantity(ucfsBrokerMemoryMegabytes, resource.Mega)
	container.Resources.Limits[core.ResourceMemory] = *resource.NewScaledQuantity(ucfsBrokerMemoryMegabytes, resource.Mega)

	pod.Spec.Containers = append(pod.Spec.Containers, container)

	return pod, nil
}

func startUcfsBrokerIfEnabled(job *orc.Job, rank int, node string, workerPod *core.Pod) {
	if !ucfsBrokerEnabled() {
		return
	}

	pod, err := prepareUcfsBrokerPod(job, rank, node, workerPod)
	if err != nil {
		log.Warn("Failed to prepare ucfs broker pod for job %s: %s", job.Id, err)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	existing, err := K8sClient.CoreV1().Pods(Namespace).Get(ctx, pod.Name, meta.GetOptions{})
	if err == nil && existing != nil {
		return
	}

	_, err = K8sClient.CoreV1().Pods(Namespace).Create(ctx, pod, meta.CreateOptions{})
	if err != nil {
		log.Warn("Failed to create ucfs broker pod for job %s: %s", job.Id, err)
	}
}
