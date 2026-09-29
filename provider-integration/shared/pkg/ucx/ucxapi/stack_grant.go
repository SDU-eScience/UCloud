package ucxapi

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
)

type StackGrantTokenRequest struct {
	JobId string `json:"jobId"`
}

type StackGrantTokenResponse struct {
	Token string `json:"token"`
}

var StackGrantToken = ucx.Rpc[StackGrantTokenRequest, StackGrantTokenResponse]{CallName: "stackGrantToken"}

const stackGrantBaseContext = "internal/stack-grant"

type StackGrantAuth struct {
	Token string `json:"token"`
}

type StackGrantCreateRequest[Spec any] struct {
	StackGrantAuth
	Items []Spec `json:"items"`
}

func stackGrantCreateCall[Spec any](operation string) rpc.Call[StackGrantCreateRequest[Spec], fnd.BulkResponse[fnd.FindByStringId]] {
	return rpc.Call[StackGrantCreateRequest[Spec], fnd.BulkResponse[fnd.FindByStringId]]{
		BaseContext: stackGrantBaseContext,
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesPublic,
		Operation:   operation,
	}
}

var StackGrantCreateIngress = stackGrantCreateCall[orcapi.IngressSpecification]("createIngress")
var StackGrantCreatePublicIp = stackGrantCreateCall[orcapi.PublicIPSpecification]("createPublicIp")
var StackGrantCreatePrivateNetwork = stackGrantCreateCall[orcapi.PrivateNetworkSpecification]("createPrivateNetwork")
var StackGrantCreatePrivateNetworkIp = stackGrantCreateCall[orcapi.PrivateNetworkIpSpecification]("createPrivateNetworkIp")
var StackGrantCreateJob = stackGrantCreateCall[orcapi.JobSpecification]("createJob")

type StackGrantBrowseIngressesRequest struct {
	StackGrantAuth
}

var StackGrantBrowseIngresses = rpc.Call[StackGrantBrowseIngressesRequest, []orcapi.Ingress]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "browseIngresses",
}

type StackGrantIngressProductsRequest struct {
	StackGrantAuth
}

var StackGrantIngressProducts = rpc.Call[StackGrantIngressProductsRequest, []orcapi.IngressSupport]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "ingressProducts",
}
