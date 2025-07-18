package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func StartScheduledJob(job *orc.Job, rank int, node string) error {
	podName := idAndRankToPodName(job.Id, rank)

	{
		timeout, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		pod, err := K8sClient.CoreV1().Pods(Namespace).Get(timeout, podName, meta.GetOptions{})
		cancel()
		if pod != nil && err == nil {
			// Pod already exists, do not schedule it
			return nil
		}
	}

	iappConfig := ctrl.RetrieveIAppByJobId(job.Id)
	iappHandler := util.OptNone[ContainerIAppHandler]()
	if iappConfig.Present {
		jobCopy := *job

		handler, ok := IApps[iappConfig.Value.AppName]
		if !ok {
			return fmt.Errorf("invalid iapp %s", iappConfig.Value.AppName)
		}

		iappHandler.Set(handler)
		job = &jobCopy

		if handler.MutateJobNonPersistent != nil {
			handler.MutateJobNonPersistent(job, iappConfig.Value.Configuration)
		}
	}

	jobFolder, drive, err := FindJobFolder(job)
	if err != nil {
		return fmt.Errorf("failed to initialize job folder")
	}

	if rank == 0 {
		ucloudFolder, ok := filesystem.InternalToUCloudWithDrive(drive, jobFolder)
		if ok {
			_ = ctrl.TrackRawUpdates([]orc.ResourceUpdateAndId[orc.JobUpdate]{
				{
					Id: job.Id,
					Update: orc.JobUpdate{
						OutputFolder: util.OptValue[string](ucloudFolder),
					},
				},
			})
		}
	}

	namespace := ServiceConfig.Compute.Namespace

	application := &job.Status.ResolvedApplication.Invocation
	tool := &job.Status.ResolvedApplication.Invocation.Tool.Tool

	// Sensitive project validation
	// -----------------------------------------------------------------------------------------------------------------
	if shared.IsSensitiveProject(job.Owner.Project) {
		rejectionMessage := util.OptNone[string]()
		for _, resc := range job.Specification.Resources {
			if resc.Type == orc.AppParameterValueTypeIngress {
				rejectionMessage.Set("Public links cannot be used by this project")
				break
			}

			if resc.Type == orc.AppParameterValueTypeNetwork {
				rejectionMessage.Set("Public IPs cannot be used by this project")
				break
			}

			if resc.Type == orc.AppParameterValueTypePeer {
				peerJob, ok := ctrl.RetrieveJob(resc.JobId)
				if !ok {
					rejectionMessage.Set("One of your connected jobs cannot be used in this project")
					break
				}

				if job.Owner.Project != peerJob.Owner.Project {
					rejectionMessage.Set("One of your connected jobs cannot be used in this project")
					break
				}
			}

			// NOTE(Dan): Files are checked by the mounts code
		}

		if rejectionMessage.Present {
			return util.ServerHttpError("%s", rejectionMessage)
		}
	}

	// Setting up the basics
	// -----------------------------------------------------------------------------------------------------------------
	pod := &core.Pod{
		TypeMeta: meta.TypeMeta{},
		ObjectMeta: meta.ObjectMeta{
			Name:        podName,
			Annotations: make(map[string]string),
			Labels:      make(map[string]string),
		},
		Spec: core.PodSpec{},
	}

	if iappConfig.Present {
		pod.Annotations[IAppAnnotationEtag] = iappConfig.Value.ETag
		pod.Annotations[IAppAnnotationName] = iappConfig.Value.AppName
	}

	spec := &pod.Spec
	spec.RestartPolicy = core.RestartPolicyNever
	spec.AutomountServiceAccountToken = util.BoolPointer(false)

	spec.Containers = append(spec.Containers, core.Container{
		Name: ContainerUserJob,
	})

	userContainer := &spec.Containers[0]
	userContainer.ImagePullPolicy = core.PullIfNotPresent
	userContainer.Resources.Limits = map[core.ResourceName]resource.Quantity{}
	userContainer.Resources.Requests = map[core.ResourceName]resource.Quantity{}
	userContainer.SecurityContext = &core.SecurityContext{}
	userContainer.Image = tool.Description.Image
	if userContainer.Image == "" {
		userContainer.Image = tool.Description.Container
	}

	// Setting up network policy and service
	// -----------------------------------------------------------------------------------------------------------------
	// Only rank 0 is responsible for creating these additional resources. Their pointers will be nil if they should
	// not be created by this invocation.

	var firewall *networking.NetworkPolicy
	var service *core.Service
	var sshService *core.Service
	var ipService *core.Service

	if rank == 0 {
		firewall = &networking.NetworkPolicy{
			ObjectMeta: meta.ObjectMeta{
				Name: firewallName(job.Id),
			},
			Spec: networking.NetworkPolicySpec{
				PodSelector: k8PodSelectorForJob(job.Id),
			},
		}
		allowNetworkFrom(firewall, job.Id)
		allowNetworkTo(firewall, job.Id)

		serviceLabel := shared.JobIdLabel(job.Id)
		service = &core.Service{
			ObjectMeta: meta.ObjectMeta{
				Name: serviceName(job.Id),
				Labels: map[string]string{
					serviceLabel.First: serviceLabel.Second,
				},
			},
			Spec: core.ServiceSpec{
				Type:      core.ServiceTypeClusterIP,
				ClusterIP: core.ClusterIPNone,
				Selector: map[string]string{
					serviceLabel.First: serviceLabel.Second,
				},
			},
		}

		sshService = shared.AssignAndPrepareSshService(job).GetOrDefault(nil)
		if sshService != nil {
			allowNetworkFromWorld(firewall, []orc.PortRangeAndProto{
				{
					Protocol: orc.IpProtocolTcp,
					Start:    22,
					End:      22,
				},
			})
		}

		ipService = preparePublicIp(job, firewall, userContainer)
	}

	// JobParameters.json
	// -----------------------------------------------------------------------------------------------------------------
	if rank == 0 && job.Status.JobParametersJson.SiteVersion != 0 {
		jsonData, _ := json.Marshal(job.Status.JobParametersJson)
		fd, ok := filesystem.OpenFile(filepath.Join(jobFolder, "JobParameters.json"), os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0660)
		if ok {
			_, _ = fd.Write(jsonData)
			_ = fd.Close()
		}
	}

	// Scheduling and runtime constraints for Kubernetes
	// -----------------------------------------------------------------------------------------------------------------
	addResource := func(name core.ResourceName, value int64, scale resource.Scale) {
		quantity := resource.NewScaledQuantity(value, scale)
		quantity.Format = resource.DecimalSI

		userContainer.Resources.Limits[name] = *quantity
		userContainer.Resources.Requests[name] = *quantity
	}

	product := job.Status.ResolvedProduct
	cpuMillis := int64(product.Cpu * 1000)
	memoryMegabytes := int64(product.MemoryInGigs * 1000)
	gpus := int64(product.Gpu * 1000)

	gpuType := "nvidia.com/gpu"

	machineCategory, ok := shared.ServiceConfig.Compute.Machines[job.Specification.Product.Category]
	if ok {
		nodeCat, ok := machineCategory.Groups[job.Specification.Product.Category]
		if ok {
			gpuType = nodeCat.GpuResourceType
			if nodeCat.CustomRuntime != "" {
				spec.RuntimeClassName = &nodeCat.CustomRuntime
			}
		}

		for _, config := range nodeCat.Configs {
			if config.AdvertisedCpu == product.Cpu && config.MemoryInGigabytes == product.MemoryInGigs && config.Gpu == product.Gpu {
				cpuMillis = int64(config.ActualCpuMillis)
				break
			}
		}
	}

	addResource(core.ResourceCPU, cpuMillis, resource.Milli)
	addResource(core.ResourceMemory, memoryMegabytes, resource.Mega)
	if gpus > 0 {
		addResource(core.ResourceName(gpuType), gpus, 0)
	}

	pod.Spec.NodeName = node

	userContainer.SecurityContext.RunAsNonRoot = util.BoolPointer(!application.Container.RunAsRoot)
	userContainer.SecurityContext.AllowPrivilegeEscalation = util.BoolPointer(application.Container.RunAsRoot)

	spec.Hostname = fmt.Sprintf("j-%s-job-%d", job.Id, rank)
	spec.Subdomain = fmt.Sprintf("j-%v", job.Id)

	// Working directory
	// -----------------------------------------------------------------------------------------------------------------
	if application.Container.ChangeWorkingDirectory {
		userContainer.WorkingDir = "/work"
	}

	// Mounts
	// -----------------------------------------------------------------------------------------------------------------
	internalToPod, ok := prepareMountsOnJobCreate(job, pod, userContainer, jobFolder)
	if !ok {
		return util.ServerHttpError("Unable to use these folders together. One or more are sensitive.")
	}

	// Modules
	// -----------------------------------------------------------------------------------------------------------------
	prepareModules(job, pod, userContainer)

	// Invocation
	// -----------------------------------------------------------------------------------------------------------------
	prepareInvocationOnJobCreate(job, rank, pod, userContainer, internalToPod, jobFolder)

	// Multi-node sidecar
	// -----------------------------------------------------------------------------------------------------------------
	spec.InitContainers = append(spec.InitContainers, core.Container{
		Name:  "ucloud-compat",
		Image: "alpine:latest",
	})
	multinodeSidecar := &spec.InitContainers[len(spec.InitContainers)-1]

	optUCloudVolumeName := "ucloud-opt"
	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: optUCloudVolumeName,
		VolumeSource: core.VolumeSource{
			EmptyDir: &core.EmptyDirVolumeSource{},
		},
	})

	etcUCloudVolumeName := "ucloud-etc"
	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: etcUCloudVolumeName,
		VolumeSource: core.VolumeSource{
			EmptyDir: &core.EmptyDirVolumeSource{},
		},
	})

	multiNodeVolume := &spec.Volumes[len(spec.Volumes)-1]
	userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
		Name:      multiNodeVolume.Name,
		MountPath: "/etc/ucloud",
	})
	userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
		Name:      optUCloudVolumeName,
		MountPath: "/opt/ucloud",
	})

	multinodeSidecar.VolumeMounts = append(multinodeSidecar.VolumeMounts, core.VolumeMount{
		Name:      multiNodeVolume.Name,
		MountPath: "/etc/ucloud",
	})

	multinodeSidecar.Resources.Limits = map[core.ResourceName]resource.Quantity{}
	multinodeSidecar.Resources.Limits.Cpu().SetMilli(100)
	multinodeSidecar.Resources.Limits.Memory().SetScaled(64, resource.Mega)

	multiNodeScript := strings.Builder{}
	{
		appendLine := func(format string, args ...any) {
			multiNodeScript.WriteString(fmt.Sprintf(format+"\n", args...))
		}

		appendLine("echo '%d' > /etc/ucloud/number_of_nodes.txt", job.Specification.Replicas)
		for rank := 0; rank < job.Specification.Replicas; rank++ {
			hostname := jobHostName(job.Id, rank)

			appendLine("echo '%v' > /etc/ucloud/node-%v.txt", hostname, rank)
			appendLine("echo '%v' >> /etc/ucloud/nodes.txt", hostname)
		}
		appendLine("echo %v > /etc/ucloud/rank.txt", rank)
	}

	multinodeSidecar.Command = []string{
		"/bin/sh",
		"-c",
		multiNodeScript.String(),
	}

	// UCloud visualization (ucviz)
	// -----------------------------------------------------------------------------------------------------------------
	spec.InitContainers = append(spec.InitContainers, core.Container{
		Name:  "ucviz",
		Image: "dreg.cloud.sdu.dk/ucloud/im2:2025.3.55",
	})

	ucvizContainer := &spec.InitContainers[len(spec.InitContainers)-1]
	ucvizContainer.VolumeMounts = append(ucvizContainer.VolumeMounts, core.VolumeMount{
		Name:      optUCloudVolumeName,
		MountPath: "/opt/ucloud",
	})

	ucvizContainer.Command = []string{"bash", "-c", "cp /usr/bin/ucmetrics /opt/ucloud/ucmetrics ; cp /usr/bin/ucviz /opt/ucloud/ucviz"}

	if util.DevelopmentModeEnabled() {
		ucvizContainer.ImagePullPolicy = core.PullAlways
	}

	if util.DevelopmentModeEnabled() && ServiceConfig.Compute.ImSourceCode.Present {
		ucvizContainer.Image = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2025.3.3"
		ucvizContainer.VolumeMounts = append(ucvizContainer.VolumeMounts, core.VolumeMount{
			Name:      "ucloud-filesystem",
			ReadOnly:  false,
			MountPath: "/opt/source",
			SubPath:   ServiceConfig.Compute.ImSourceCode.Value,
		})

		userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
			Name:      "ucloud-filesystem",
			ReadOnly:  false,
			MountPath: "/opt/source",
			SubPath:   ServiceConfig.Compute.ImSourceCode.Value,
		})

		// From provider-integration folder:
		// rsync -vhra . ../.compose/default/im2k8/im/storage/source-code --exclude ucloud-im --exclude integration-module
		ucvizContainer.Command = []string{
			"bash", "-c", "cd /opt/source/im2 ; export PATH=$PATH:/usr/local/go/bin ; CGO_ENABLED=0 go build -o /opt/ucloud/ucviz -trimpath ucloud.dk/cmd/ucviz ; CGO_ENABLED=0 go build -o /opt/ucloud/ucmetrics -trimpath ucloud.dk/cmd/ucmetrics",
		}
	}

	// Firewall
	// -----------------------------------------------------------------------------------------------------------------
	prepareFirewallOnJobCreate(job, pod, firewall, service)

	// SSH
	// -----------------------------------------------------------------------------------------------------------------
	injectSshKeys(job.Id, pod, userContainer)

	// Shared-memory
	// -----------------------------------------------------------------------------------------------------------------
	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: "shm",
		VolumeSource: core.VolumeSource{
			EmptyDir: &core.EmptyDirVolumeSource{
				Medium:    core.StorageMediumMemory,
				SizeLimit: resource.NewScaledQuantity(memoryMegabytes, resource.Mega),
			},
		},
	})

	userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
		Name:      "shm",
		MountPath: "/dev/shm",
	})

	// Job metadata
	// -----------------------------------------------------------------------------------------------------------------
	idLabel := shared.JobIdLabel(job.Id)
	rankLabel := shared.JobRankLabel(rank)
	pod.Annotations[idLabel.First] = idLabel.Second
	pod.Annotations[rankLabel.First] = rankLabel.Second
	pod.Labels[idLabel.First] = idLabel.Second
	pod.Labels[rankLabel.First] = rankLabel.Second
	if job.Owner.Project != "" {
		pod.Labels["ucloud.dk/workspaceId"] = job.Owner.Project
	}

	if iappHandler.Present && iappHandler.Value.MutatePod != nil {
		err = iappHandler.Value.MutatePod(job, iappConfig.Value.Configuration, pod)
		if err != nil {
			// Block errors on pod, but we do not immediately block on the rest.
			return err
		}
	}

	// NOTE(Dan): Check if the job is allowed to be submitted. This most be immediately before the job creation. It
	// must not be moved down or up. Do not add code between these two.
	if reason := shared.IsJobLockedEx(job, pod.Annotations); reason.Present {
		return reason.Value.Err
	}

	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(15*time.Second))
	defer cancel()

	pod, err = K8sClient.CoreV1().Pods(namespace).Create(ctx, pod, meta.CreateOptions{})
	var ownerReference meta.OwnerReference
	if err == nil {
		ownerReference = meta.OwnerReference{
			APIVersion: "v1",
			Kind:       "Pod",
			Name:       pod.Name,
			UID:        pod.UID,
		}
	}
	if firewall != nil && err == nil {
		firewall.OwnerReferences = append(firewall.OwnerReferences, ownerReference)

		if iappHandler.Present && iappHandler.Value.MutateNetworkPolicy != nil {
			myError := iappHandler.Value.MutateNetworkPolicy(job, iappConfig.Value.Configuration, firewall, pod)
			err = util.MergeError(err, myError)
		}

		_, myError := K8sClient.NetworkingV1().NetworkPolicies(namespace).Create(ctx, firewall, meta.CreateOptions{})
		err = util.MergeError(err, myError)
	}
	if service != nil && err == nil {
		service.OwnerReferences = append(service.OwnerReferences, ownerReference)

		if iappHandler.Present && iappHandler.Value.MutateService != nil {
			myError := iappHandler.Value.MutateService(job, iappConfig.Value.Configuration, service, pod)
			err = util.MergeError(err, myError)
		}

		_, myError := K8sClient.CoreV1().Services(namespace).Create(ctx, service, meta.CreateOptions{})
		err = util.MergeError(err, myError)
	}
	if sshService != nil && err == nil {
		sshService.OwnerReferences = append(sshService.OwnerReferences, ownerReference)

		_, myError := K8sClient.CoreV1().Services(namespace).Create(ctx, sshService, meta.CreateOptions{})
		err = util.MergeError(err, myError)
	}
	if ipService != nil && err == nil {
		ipService.OwnerReferences = append(ipService.OwnerReferences, ownerReference)

		_, myError := K8sClient.CoreV1().Services(namespace).Create(ctx, ipService, meta.CreateOptions{})
		err = util.MergeError(err, myError)
	}

	// NOTE(Dan): Cleanup in case of errors are centralized in the delete code. This is mostly to do with the fact that
	//   we have multiple replicas.

	return err
}

func allowNetworkFrom(policy *networking.NetworkPolicy, jobId string) {
	selector := k8PodSelectorForJob(jobId)
	spec := &policy.Spec

	spec.Ingress = append(spec.Ingress, networking.NetworkPolicyIngressRule{
		From: []networking.NetworkPolicyPeer{
			{PodSelector: &selector},
		},
	})
}

func allowNetworkTo(policy *networking.NetworkPolicy, jobId string) {
	selector := k8PodSelectorForJob(jobId)
	spec := &policy.Spec

	spec.Egress = append(spec.Egress, networking.NetworkPolicyEgressRule{
		To: []networking.NetworkPolicyPeer{
			{PodSelector: &selector},
		},
	})
}

func allowNetworkFromSubnet(policy *networking.NetworkPolicy, subnet string) {
	spec := &policy.Spec
	spec.Ingress = append(spec.Ingress, networking.NetworkPolicyIngressRule{
		From: []networking.NetworkPolicyPeer{
			{
				IPBlock: &networking.IPBlock{
					CIDR: subnet,
				},
			},
		},
	})
}

func allowNetworkToSubnet(policy *networking.NetworkPolicy, subnet string) {
	spec := &policy.Spec
	spec.Egress = append(spec.Egress, networking.NetworkPolicyEgressRule{
		To: []networking.NetworkPolicyPeer{
			{
				IPBlock: &networking.IPBlock{
					CIDR: subnet,
				},
			},
		},
	})
}

func allowNetworkFromWorld(policy *networking.NetworkPolicy, proto []orc.PortRangeAndProto) {
	var portEntries []networking.NetworkPolicyPort
	for _, entry := range proto {
		portEntries = append(portEntries, networking.NetworkPolicyPort{
			Protocol: util.Pointer(core.Protocol(entry.Protocol)),
			Port: &intstr.IntOrString{
				Type:   intstr.Int,
				IntVal: int32(entry.Start),
			},
			EndPort: util.Pointer(int32(entry.End)),
		})
	}

	policy.Spec.Ingress = append(policy.Spec.Ingress, networking.NetworkPolicyIngressRule{
		Ports: portEntries,
		From: []networking.NetworkPolicyPeer{
			{
				IPBlock: &networking.IPBlock{
					CIDR: "0.0.0.0/0",
				},
			},
		},
	})
}

func firewallName(jobId string) string {
	return "policy-" + jobId
}

func serviceName(jobId string) string {
	return "j-" + jobId
}

func k8PodSelectorForJob(jobId string) meta.LabelSelector {
	l := shared.JobIdLabel(jobId)
	return meta.LabelSelector{
		MatchLabels: map[string]string{
			l.First: l.Second,
		},
	}
}

func idAndRankToPodName(id string, rank int) string {
	return fmt.Sprintf("j-%v-job-%v", id, rank)
}

func podNameToIdAndRank(podName string) (util.Tuple2[string, int], bool) {
	name, hasPrefix := strings.CutPrefix(podName, "j-")
	if !hasPrefix {
		return util.Tuple2[string, int]{}, false
	}

	name = strings.ReplaceAll(name, "-job-", "-")
	parts := strings.Split(name, "-")
	if len(parts) != 2 {
		return util.Tuple2[string, int]{}, false
	}

	rank, err := strconv.Atoi(parts[1])
	if err != nil {
		return util.Tuple2[string, int]{}, false
	}

	return util.Tuple2[string, int]{parts[0], rank}, true
}

func jobHostName(jobId string, rank int) string {
	return fmt.Sprintf(
		"j-%v-job-%v.j-%v.%v.svc.cluster.local",
		jobId,
		rank,
		jobId,
		ServiceConfig.Compute.Namespace,
	)
}
