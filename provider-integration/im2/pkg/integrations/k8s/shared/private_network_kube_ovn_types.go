package shared

import (
	k8score "k8s.io/api/core/v1"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"ucloud.dk/shared/pkg/log"
)

// These types are defined locally because the official kube-ovn module forces an incompatible upgrade of the Kubernetes libraries.
type privateNetworkKubeOvnVpc struct {
	k8smeta.TypeMeta   `json:",inline"`
	k8smeta.ObjectMeta `json:"metadata"`
	Spec               privateNetworkKubeOvnVpcSpec   `json:"spec"`
	Status             privateNetworkKubeOvnVpcStatus `json:"status"`
}

type privateNetworkKubeOvnVpcSpec struct {
	Namespaces     []string                                `json:"namespaces"`
	StaticRoutes   []*privateNetworkKubeOvnStaticRoute `json:"staticRoutes"`
	PolicyRoutes   []*privateNetworkKubeOvnPolicyRoute `json:"policyRoutes"`
	VpcPeerings    []*privateNetworkKubeOvnVpcPeering  `json:"vpcPeerings"`
	EnableExternal bool                                `json:"enableExternal"`
	EnableBfd      bool                                `json:"enableBfd"`
}

type privateNetworkKubeOvnStaticRoute struct {
	Policy     string `json:"policy,omitempty"`
	Cidr       string `json:"cidr"`
	NextHopIp  string `json:"nextHopIP"`
	EcmpMode   string `json:"ecmpMode"`
	BfdId      string `json:"bfdId"`
	RouteTable string `json:"routeTable"`
}

type privateNetworkKubeOvnPolicyRoute struct {
	Priority  int    `json:"priority,omitempty"`
	Match     string `json:"match,omitempty"`
	Action    string `json:"action,omitempty"`
	NextHopIp string `json:"nextHopIP,omitempty"`
}

type privateNetworkKubeOvnVpcPeering struct {
	RemoteVpc      string `json:"remoteVpc,omitempty"`
	LocalConnectIp string `json:"localConnectIP,omitempty"`
}

type privateNetworkKubeOvnVpcStatus struct {
	Standby bool `json:"standby"`
}

type privateNetworkKubeOvnSubnet struct {
	k8smeta.TypeMeta   `json:",inline"`
	k8smeta.ObjectMeta `json:"metadata"`
	Spec               privateNetworkKubeOvnSubnetSpec   `json:"spec"`
	Status             privateNetworkKubeOvnSubnetStatus `json:"status"`
}

type privateNetworkKubeOvnSubnetSpec struct {
	Protocol    string   `json:"protocol"`
	Vpc         string   `json:"vpc"`
	CidrBlock   string   `json:"cidrBlock"`
	Gateway     string   `json:"gateway"`
	Provider    string   `json:"provider"`
	NatOutgoing bool     `json:"natOutgoing"`
	EnableDhcp  bool     `json:"enableDHCP"`
	Namespaces  []string `json:"namespaces"`
	ExcludeIps  []string `json:"excludeIps"`
}

type privateNetworkKubeOvnSubnetStatus struct {
	Conditions []privateNetworkKubeOvnCondition `json:"conditions"`
}

type privateNetworkKubeOvnCondition struct {
	Type   string                  `json:"type"`
	Status k8score.ConditionStatus `json:"status"`
}

type privateNetworkKubeOvnIp struct {
	k8smeta.TypeMeta   `json:",inline"`
	k8smeta.ObjectMeta `json:"metadata"`
	Spec               privateNetworkKubeOvnIpSpec `json:"spec"`
}

type privateNetworkKubeOvnIpSpec struct {
	Subnet      string `json:"subnet"`
	PodName     string `json:"podName"`
	Namespace   string `json:"namespace"`
	V4IpAddress string `json:"v4IpAddress"`
	MacAddress  string `json:"macAddress"`
}

func privateNetworkKubeOvnFromUnstructured(
	kind string,
	source *unstructured.Unstructured,
	destination any,
) bool {
	err := runtime.DefaultUnstructuredConverter.FromUnstructured(source.Object, destination)
	if err != nil {
		log.Warn("Failed to decode the Kube-OVN %s %s: %s", kind, source.GetName(), err)
		return false
	}
	return true
}

func privateNetworkKubeOvnToUnstructured(source any) *unstructured.Unstructured {
	object, err := runtime.DefaultUnstructuredConverter.ToUnstructured(source)
	if err != nil {
		log.Fatal("Failed to encode a Kube-OVN private network object: %s", err)
	}
	delete(object, "status")
	if metadata, ok := object["metadata"].(map[string]any); ok {
		delete(metadata, "creationTimestamp")
	}
	return &unstructured.Unstructured{Object: object}
}
