package containers

import (
	"context"
	"encoding/json"
	"time"
	"ucloud.dk/pkg/im/services/k8s/shared"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	v1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func prepareFirewallOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	firewall *networking.NetworkPolicy,
	service *core.Service,
) {
	if pod == nil || firewall == nil || service == nil {
		// Nothing to do if we are not supposed to touch the resources
		return
	}

	isSensitiveProject := shared.IsSensitiveProject(job.Owner.Project)
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
	}

	// Update the networking policy of the peer containers
	if len(peers) > 0 {
		labelSelector := k8PodSelectorForJob(job.Id)

		ingressPatch, err := json.Marshal(
			[]networkPolicyIngressPatch{
				{
					Op:   "add",
					Path: "/spec/ingress/-",
					Value: networking.NetworkPolicyIngressRule{
						From: []networking.NetworkPolicyPeer{{
							PodSelector: &labelSelector,
						}},
					},
				},
			},
		)

		egressPatch, err := json.Marshal(
			[]networkPolicyEgressPatch{
				{
					Op:   "add",
					Path: "/spec/egress/-",
					Value: networking.NetworkPolicyEgressRule{
						To: []networking.NetworkPolicyPeer{{
							PodSelector: &labelSelector,
						}},
					},
				},
			},
		)

		if err != nil {
			log.Info("Failed to marshal networking policy patches")
		}

		networkingPolicies := K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace)
		ctx, _ := context.WithDeadline(context.Background(), time.Now().Add(10*time.Second))

		for _, peer := range peers {
			peerPod := findPodByJobIdAndRank(peer.JobId, 0)
			if peerPod.Present {
				_, err := networkingPolicies.Patch(ctx, firewallName(peer.JobId), types.JSONPatchType, ingressPatch, v1.PatchOptions{})
				if err != nil {
					log.Info("Failed to patch ingress: %v", err)
				}

				_, err = networkingPolicies.Patch(ctx, firewallName(peer.JobId), types.JSONPatchType, egressPatch, v1.PatchOptions{})
				if err != nil {
					log.Info("Failed to patch egress: %v", err)
				}
			}
		}
	}
}

type networkPolicyIngressPatch struct {
	Op    string                              `json:"op"`
	Path  string                              `json:"path"`
	Value networking.NetworkPolicyIngressRule `json:"value"`
}

type networkPolicyEgressPatch struct {
	Op    string                             `json:"op"`
	Path  string                             `json:"path"`
	Value networking.NetworkPolicyEgressRule `json:"value"`
}

const invalidSubnet = "192.0.2.100/32"
