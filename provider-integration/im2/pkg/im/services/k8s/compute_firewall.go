package k8s

import (
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	orc "ucloud.dk/pkg/orchestrators"
)

func prepareFirewallOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	firewall *networking.NetworkPolicy,
	service *core.Service,
) {
	// TODO get this from configuration
	isSensitiveProject := false

	if isSensitiveProject {
		pod.ObjectMeta.Labels["ucloud.dk/firewallSensitive"] = "true"
	}

	// NOTE(Dan): Kubernetes will insert null instead of an empty list if we pass an empty list
	// The JSON patch below will only work if the list is present and we cannot insert an empty list
	// if it is not already present via JSON patch. As a result, we will insert a dummy entry which
	// (hopefully) shouldn't have any effect.

	// NOTE(Dan): The IP listed below is reserved for documentation (TEST-NET-1,
	// see https://tools.ietf.org/html/rfc5737). Let's hope no one gets the bright idea to actually
	// use this subnet in practice.
	allowNetworkFromSubnet(firewall, invalidSubnet)
	allowNetworkToSubnet(firewall, invalidSubnet)

	var peers []orc.AppParameterValue

	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypePeer {
			peers = append(peers, v)
		}
	}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypePeer {
			peers = append(peers, v)
		}
	}

	for _, peer := range peers {
		allowNetworkFrom(firewall, peer.JobId)
		allowNetworkTo(firewall, peer.JobId)

		peerPod := findPodByJobIdAndRank(peer.JobId, 0)
		if peerPod.Present {
			pod.Spec.HostAliases = append(pod.Spec.HostAliases, core.HostAlias{
				IP:        peerPod.Value.Status.PodIP,
				Hostnames: []string{peer.Hostname},
			})
		}

		// TODO Modify peer containers
	}
}

const invalidSubnet = "192.0.2.100/32"
