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

type StackGrantCreateIngressRequest struct {
	StackGrantAuth
	Items []orcapi.IngressSpecification `json:"items"`
}

var StackGrantCreateIngress = rpc.Call[StackGrantCreateIngressRequest, fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "createIngress",
}

type StackGrantCreatePublicIpRequest struct {
	StackGrantAuth
	Items []orcapi.PublicIPSpecification `json:"items"`
}

var StackGrantCreatePublicIp = rpc.Call[StackGrantCreatePublicIpRequest, fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "createPublicIp",
}

type StackGrantCreatePrivateNetworkRequest struct {
	StackGrantAuth
	Items []orcapi.PrivateNetworkSpecification `json:"items"`
}

var StackGrantCreatePrivateNetwork = rpc.Call[StackGrantCreatePrivateNetworkRequest, fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "createPrivateNetwork",
}

type StackGrantCreatePrivateNetworkIpRequest struct {
	StackGrantAuth
	Items []orcapi.PrivateNetworkIpSpecification `json:"items"`
}

var StackGrantCreatePrivateNetworkIp = rpc.Call[StackGrantCreatePrivateNetworkIpRequest, fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "createPrivateNetworkIp",
}

type StackGrantCreateJobRequest struct {
	StackGrantAuth
	Items []orcapi.JobSpecification `json:"items"`
}

var StackGrantCreateJob = rpc.Call[StackGrantCreateJobRequest, fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "createJob",
}

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
