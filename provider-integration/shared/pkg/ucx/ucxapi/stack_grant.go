package ucxapi

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
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
var StackGrantCreateService = stackGrantCreateCall[orcapi.ServiceSpecification]("createService")

type StackGrantBrowseIngressesRequest struct {
	StackGrantAuth
}

var StackGrantBrowseIngresses = rpc.Call[StackGrantBrowseIngressesRequest, []orcapi.Ingress]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "browseIngresses",
}

type StackGrantBrowseServicesRequest struct {
	StackGrantAuth
}

var StackGrantBrowseServices = rpc.Call[StackGrantBrowseServicesRequest, []orcapi.Service]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "browseServices",
}

type StackGrantBrowseJobsRequest struct {
	StackGrantAuth
}

var StackGrantBrowseJobs = rpc.Call[StackGrantBrowseJobsRequest, []orcapi.Job]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "browseJobs",
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

type StackGrantServiceProductsRequest struct {
	StackGrantAuth
}

var StackGrantServiceProducts = rpc.Call[StackGrantServiceProductsRequest, []orcapi.ServiceSupport]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "serviceProducts",
}

type StackGrantServiceUpdateMembersRequest struct {
	StackGrantAuth
	Id            string   `json:"id"`
	AddedJobIds   []string `json:"addedJobIds"`
	RemovedJobIds []string `json:"removedJobIds"`
}

var StackGrantServiceUpdateMembers = rpc.Call[StackGrantServiceUpdateMembersRequest, util.Empty]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "serviceUpdateMembers",
}

type StackGrantDeleteIngressRequest struct {
	StackGrantAuth
	ServiceId  string   `json:"serviceId"`
	IngressIds []string `json:"ingressIds"`
}

var StackGrantDeleteIngress = rpc.Call[StackGrantDeleteIngressRequest, util.Empty]{
	BaseContext: stackGrantBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "deleteIngress",
}
