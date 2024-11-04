package k8s

import (
	"fmt"
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"strconv"
	"strings"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type UnboundJobResource struct {
	Pod      *core.Pod
	Firewall *networking.NetworkPolicy
	Service  *core.Service
}

func CreateJobResource(job *orc.Job, jobFolder string) UnboundJobResource {
	// TODO Get these from configuration
	tolerationKv := util.Option[util.Tuple2[string, string]]{}
	priorityClass := util.Option[string]{}
	customRuntimesByCategory := map[string]string{}

	application := &job.Status.ResolvedApplication.Invocation
	tool := &job.Status.ResolvedApplication.Invocation.Tool.Tool

	// Setting up network policy and service
	// -----------------------------------------------------------------------------------------------------------------
	firewall := &networking.NetworkPolicy{
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
	service := &core.Service{
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

	// Setting up the basics
	// -----------------------------------------------------------------------------------------------------------------
	pod := &core.Pod{
		TypeMeta: meta.TypeMeta{},
		ObjectMeta: meta.ObjectMeta{
			Annotations: make(map[string]string),
			Labels:      make(map[string]string),
		},
		Spec: core.PodSpec{},
	}

	spec := &pod.Spec
	spec.RestartPolicy = core.RestartPolicyNever
	spec.AutomountServiceAccountToken = util.FalsePointer

	spec.Containers = append(spec.Containers, core.Container{
		Name: "user-job",
	})

	userContainer := &spec.Containers[0]
	userContainer.Image = tool.Description.Image
	userContainer.ImagePullPolicy = core.PullIfNotPresent
	userContainer.Resources.Limits = map[core.ResourceName]resource.Quantity{}
	userContainer.Resources.Requests = map[core.ResourceName]resource.Quantity{}

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

	spec.NodeSelector = map[string]string{
		// TODO(Dan): Map this according to configuration
		"ucloud.dk/machine": job.Specification.Product.Category,
	}

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

	// Invocation
	// -----------------------------------------------------------------------------------------------------------------

	// Mounts
	// -----------------------------------------------------------------------------------------------------------------
	prepareMountsOnJobCreate(job, pod, userContainer, jobFolder)

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
			multiNodeScript.WriteString(fmt.Sprintf(format, args...))
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
		appendLine("echo $UCLOUD_RANK > /etc/ucloud/rank.txt")
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
	pod.Labels["volcano.sh/job-name"] = fmt.Sprintf("j-%v", job.Id)
	pod.Annotations["volcano.sh/job-name"] = fmt.Sprintf("j-%v", job.Id)

	idLabel := jobIdLabel(job.Id)
	pod.Annotations[idLabel.First] = idLabel.Second
	pod.Labels[idLabel.First] = idLabel.Second
	if job.Owner.Project != "" {
		pod.Labels["ucloud.dk/workspaceId"] = job.Owner.Project
	}

	return UnboundJobResource{
		Pod:      pod,
		Firewall: firewall,
		Service:  service,
	}
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
