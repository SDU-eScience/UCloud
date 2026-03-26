package shared

import (
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func AllowNetworkFromSubnet(policy *networking.NetworkPolicy, subnet string) {
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

func AllowNetworkToSubnet(policy *networking.NetworkPolicy, subnet string) {
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

func AllowNetworkFromWorld(policy *networking.NetworkPolicy, proto []orc.PortRangeAndProto) {
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

func ServiceName(jobId string) string {
	return "j-" + jobId
}

func FirewallName(jobId string) string {
	return "policy-" + jobId
}
