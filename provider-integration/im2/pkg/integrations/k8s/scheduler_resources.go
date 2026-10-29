package k8s

import (
	"strconv"
	"strings"

	core "k8s.io/api/core/v1"
	"ucloud.dk/pkg/integrations/k8s/shared"
)

type schedulerReplicaKey struct {
	JobId string
	Rank  int
	Node  string
}

func schedulerPodReplicaKey(pod *core.Pod) (schedulerReplicaKey, bool) {
	jobId := pod.Labels["ucloud.dk/jobId"]
	rank, err := strconv.Atoi(pod.Labels["ucloud.dk/rank"])
	if jobId == "" || err != nil {
		return schedulerReplicaKey{}, false
	}
	return schedulerReplicaKey{
		JobId: jobId,
		Rank:  rank,
		Node:  pod.Spec.NodeName,
	}, true
}

func schedulerPodDimensions(pod *core.Pod) shared.SchedulerDimensions {
	requests := core.ResourceList{}
	add := func(target, source core.ResourceList) {
		for name, quantity := range source {
			value := target[name]
			value.Add(quantity)
			target[name] = value
		}
	}
	maximum := func(target, source core.ResourceList) {
		for name, quantity := range source {
			value := target[name]
			if quantity.Cmp(value) > 0 {
				target[name] = quantity.DeepCopy()
			}
		}
	}
	for _, container := range pod.Spec.Containers {
		add(requests, container.Resources.Requests)
	}
	initRequests := core.ResourceList{}
	sidecarRequests := core.ResourceList{}
	for _, container := range pod.Spec.InitContainers {
		current := core.ResourceList{}
		add(current, sidecarRequests)
		add(current, container.Resources.Requests)
		maximum(initRequests, current)
		if container.RestartPolicy != nil && *container.RestartPolicy == core.ContainerRestartPolicyAlways {
			add(sidecarRequests, container.Resources.Requests)
		}
	}
	add(requests, sidecarRequests)
	maximum(requests, initRequests)
	add(requests, pod.Spec.Overhead)

	dimensions := shared.SchedulerDimensions{
		CpuMillis:     int(requests.Cpu().MilliValue()),
		MemoryInBytes: int(requests.Memory().Value()),
		Resources:     map[string]int{},
	}
	for name, quantity := range requests {
		if strings.Contains(string(name), "/") {
			dimensions.Resources[string(name)] = int(quantity.Value())
		}
	}
	return dimensions
}
