package shared

import (
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var privateNetworkCIDRBlocks = []string{
	"10.0.0.0/8",
	"100.64.0.0/10",
	"127.0.0.0/8",
	"169.254.0.0/16",
	"172.16.0.0/12",
	"192.0.0.0/24",
	"192.0.2.0/24",
	"192.168.0.0/16",
	"198.18.0.0/15",
	"198.51.100.0/24",
	"203.0.113.0/24",
	"224.0.0.0/4",
	"240.0.0.0/4",
}

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

func AllowNetworkToPublicInternet(policy *networking.NetworkPolicy, ports []int32) {
	var portEntries []networking.NetworkPolicyPort
	for _, port := range ports {
		portEntries = append(portEntries, networking.NetworkPolicyPort{
			Protocol: util.Pointer(core.ProtocolTCP),
			Port:     &intstr.IntOrString{Type: intstr.Int, IntVal: port},
		})
	}

	policy.Spec.Egress = append(policy.Spec.Egress, networking.NetworkPolicyEgressRule{
		Ports: portEntries,
		To: []networking.NetworkPolicyPeer{
			{
				IPBlock: &networking.IPBlock{
					CIDR:   "0.0.0.0/0",
					Except: append([]string{}, privateNetworkCIDRBlocks...),
				},
			},
		},
	})
}

func AllowNetworkToClusterDNS(policy *networking.NetworkPolicy) {
	dnsPorts := []networking.NetworkPolicyPort{
		{Protocol: util.Pointer(core.ProtocolUDP), Port: &intstr.IntOrString{Type: intstr.Int, IntVal: 53}},
		{Protocol: util.Pointer(core.ProtocolTCP), Port: &intstr.IntOrString{Type: intstr.Int, IntVal: 53}},
	}
	policy.Spec.Egress = append(policy.Spec.Egress, networking.NetworkPolicyEgressRule{
		Ports: dnsPorts,
		To: []networking.NetworkPolicyPeer{
			{
				NamespaceSelector: &metav1.LabelSelector{MatchLabels: map[string]string{"kubernetes.io/metadata.name": "kube-system"}},
				PodSelector:       &metav1.LabelSelector{MatchLabels: map[string]string{"k8s-app": "kube-dns"}},
			},
			{
				NamespaceSelector: &metav1.LabelSelector{MatchLabels: map[string]string{"kubernetes.io/metadata.name": "kube-system"}},
				PodSelector:       &metav1.LabelSelector{MatchLabels: map[string]string{"k8s-app": "coredns"}},
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
