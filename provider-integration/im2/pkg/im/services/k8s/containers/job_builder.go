package containers

import (
	"context"
	"fmt"
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"strconv"
	"strings"
	"time"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func StartScheduledJob(job *orc.Job, rank int, node string) error {
	jobFolder, err := FindJobFolder(job)
	if err != nil {
		return fmt.Errorf("failed to initialize job folder")
	}

	// TODO Get these from configuration
	tolerationKv := util.Option[util.Tuple2[string, string]]{}
	priorityClass := util.Option[string]{}
	customRuntimesByCategory := map[string]string{}

	namespace := ServiceConfig.Compute.Namespace

	application := &job.Status.ResolvedApplication.Invocation
	tool := &job.Status.ResolvedApplication.Invocation.Tool.Tool

	// Setting up network policy and service
	// -----------------------------------------------------------------------------------------------------------------
	// Only rank 0 is responsible for creating these additional resources. Their pointers will be nil if they should
	// not be created by this invocation.

	var firewall *networking.NetworkPolicy
	var service *core.Service

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

		serviceLabel := jobIdLabel(job.Id)
		service = &core.Service{
			ObjectMeta: meta.ObjectMeta{
				Name: serviceName(job.Id),
			},
			Spec: core.ServiceSpec{
				Type:      core.ServiceTypeClusterIP,
				ClusterIP: core.ClusterIPNone,
				Selector: map[string]string{
					serviceLabel.First: serviceLabel.Second,
				},
			},
		}
	}

	// Setting up the basics
	// -----------------------------------------------------------------------------------------------------------------
	pod := &core.Pod{
		TypeMeta: meta.TypeMeta{},
		ObjectMeta: meta.ObjectMeta{
			Name:        idAndRankToPodName(job.Id, rank),
			Annotations: make(map[string]string),
			Labels:      make(map[string]string),
		},
		Spec: core.PodSpec{},
	}

	spec := &pod.Spec
	spec.RestartPolicy = core.RestartPolicyNever
	spec.AutomountServiceAccountToken = util.BoolPointer(false)

	spec.Containers = append(spec.Containers, core.Container{
		Name: "user-job",
	})

	userContainer := &spec.Containers[0]
	userContainer.Image = tool.Description.Image
	userContainer.ImagePullPolicy = core.PullIfNotPresent
	userContainer.Resources.Limits = map[core.ResourceName]resource.Quantity{}
	userContainer.Resources.Requests = map[core.ResourceName]resource.Quantity{}
	userContainer.SecurityContext = &core.SecurityContext{}

	// Scheduling and runtime constraints for Kubernetes
	// -----------------------------------------------------------------------------------------------------------------
	addResource := func(name core.ResourceName, value int64, scale resource.Scale) {
		userContainer.Resources.Limits.Name(name, resource.DecimalSI).SetScaled(value, scale)
		userContainer.Resources.Requests.Name(name, resource.DecimalSI).SetScaled(value, scale)
	}

	cpuMillis := int64(job.Status.ResolvedProduct.Cpu * 1000)
	memoryMegabytes := int64(job.Status.ResolvedProduct.MemoryInGigs * 1000)
	gpus := int64(job.Status.ResolvedProduct.Gpu * 1000)
	// TODO reservations and dev scheduling

	addResource(core.ResourceCPU, cpuMillis, resource.Milli)
	addResource(core.ResourceMemory, memoryMegabytes, resource.Mega)
	if gpus > 0 {
		addResource("nvidia.com/gpu", gpus, resource.Milli)
	}

	// TODO We used to set a nodeselector but this appears redundant since we are already setting the node.
	pod.Spec.NodeName = node

	userContainer.SecurityContext.RunAsNonRoot = util.BoolPointer(!application.Container.RunAsRoot)
	userContainer.SecurityContext.AllowPrivilegeEscalation = util.BoolPointer(application.Container.RunAsRoot)

	customRuntime, hasRuntime := customRuntimesByCategory[job.Specification.Product.Category]
	if hasRuntime {
		spec.RuntimeClassName = &customRuntime
	}

	if priorityClass.IsSet() {
		spec.PriorityClassName = priorityClass.Get()
	}

	if tolerationKv.IsSet() {
		kv := tolerationKv.Get()
		spec.Tolerations = append(spec.Tolerations, core.Toleration{
			Key:      kv.First,
			Operator: core.TolerationOpEqual,
			Value:    kv.Second,
		})
	}

	spec.Subdomain = fmt.Sprintf("j-%v", job.Id)

	// Working directory
	// -----------------------------------------------------------------------------------------------------------------
	if application.Container.ChangeWorkingDirectory {
		userContainer.WorkingDir = "/work"
	}

	// Mounts
	// -----------------------------------------------------------------------------------------------------------------
	internalToPod := prepareMountsOnJobCreate(job, pod, userContainer, jobFolder)

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

	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: "ucloud-multinode",
		VolumeSource: core.VolumeSource{
			EmptyDir: &core.EmptyDirVolumeSource{},
		},
	})

	multiNodeVolume := &spec.Volumes[len(spec.Volumes)-1]
	userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
		Name:      multiNodeVolume.Name,
		MountPath: "/etc/ucloud",
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
			hostname := fmt.Sprintf(
				"j-%v-job-%v.j-%v.%v.svc.cluster.local",
				job.Id,
				rank,
				job.Id,
				ServiceConfig.Compute.Namespace,
			)

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

	// Firewall
	// -----------------------------------------------------------------------------------------------------------------
	prepareFirewallOnJobCreate(job, pod, firewall, service)

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

	// Expiration
	// -----------------------------------------------------------------------------------------------------------------
	prepareExpirationOnJobCreate(job, pod)

	// Job metadata
	// -----------------------------------------------------------------------------------------------------------------
	idLabel := jobIdLabel(job.Id)
	pod.Annotations[idLabel.First] = idLabel.Second
	pod.Labels[idLabel.First] = idLabel.Second
	if job.Owner.Project != "" {
		pod.Labels["ucloud.dk/workspaceId"] = job.Owner.Project
	}

	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(15*time.Second))
	defer cancel()

	pod, err = K8sClient.CoreV1().Pods(namespace).Create(ctx, pod, meta.CreateOptions{})
	if firewall != nil && err == nil {
		firewall.OwnerReferences = append(firewall.OwnerReferences, meta.OwnerReference{
			APIVersion: "v1",
			Kind:       "Pod",
			Name:       pod.Name,
			UID:        pod.UID,
		})

		_, myError := K8sClient.NetworkingV1().NetworkPolicies(namespace).Create(ctx, firewall, meta.CreateOptions{})
		err = util.MergeError(err, myError)
	}
	if service != nil && err == nil {
		service.OwnerReferences = append(service.OwnerReferences, meta.OwnerReference{
			APIVersion: "v1",
			Kind:       "Pod",
			Name:       pod.Name,
			UID:        pod.UID,
		})

		_, myError := K8sClient.CoreV1().Services(namespace).Create(ctx, service, meta.CreateOptions{})
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

func firewallName(jobId string) string {
	return "policy-" + jobId
}

func serviceName(jobId string) string {
	return "j-" + jobId
}

func k8PodSelectorForJob(jobId string) meta.LabelSelector {
	l := jobIdLabel(jobId)
	return meta.LabelSelector{
		MatchLabels: map[string]string{
			l.First: l.Second,
		},
	}
}

func jobIdLabel(jobId string) util.Tuple2[string, string] {
	return util.Tuple2[string, string]{"ucloud.dk/jobId", jobId}
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
