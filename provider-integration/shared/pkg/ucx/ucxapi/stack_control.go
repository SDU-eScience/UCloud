package ucxapi

import (
	"encoding/json"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

func stackControlRedactMap(decoded map[string]json.RawMessage) {
	if _, exists := decoded["token"]; exists {
		decoded["token"] = json.RawMessage(`"<redacted>"`)
	}
}

type StackControlTokenRequest struct {
	JobId string `json:"jobId"`
}

type StackControlTokenResponse struct {
	Token string `json:"token"`
}

var StackControlToken = ucx.Rpc[StackControlTokenRequest, StackControlTokenResponse]{CallName: "stackControlToken"}

const StackControlBaseContext = "internal/stack-control"

type StackCredentialAuth struct {
	Token string `json:"token"`
}

func (auth StackCredentialAuth) StackControlToken() string {
	return auth.Token
}

type StackControlRequest interface {
	StackControlToken() string
}

func stackControlAuditTransformer(request any) json.RawMessage {
	encoded, err := json.Marshal(request)
	if err != nil {
		return json.RawMessage("{}")
	}

	var decoded map[string]json.RawMessage
	if err := json.Unmarshal(encoded, &decoded); err != nil {
		return json.RawMessage("{}")
	}

	stackControlRedactMap(decoded)

	redacted, err := json.Marshal(decoded)
	if err != nil {
		return json.RawMessage("{}")
	}
	return redacted
}

type StackControlRequestOf[CtrlReq any] struct {
	StackCredentialAuth
	Request CtrlReq `json:"request"`
}

func stackControlCall[CtrlReq any, Resp any](operation string) rpc.Call[StackControlRequestOf[CtrlReq], Resp] {
	return rpc.Call[StackControlRequestOf[CtrlReq], Resp]{
		BaseContext: StackControlBaseContext,
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesPublic,
		Operation:   operation,
		Audit: rpc.AuditRules{
			Transformer: stackControlAuditTransformer,
		},
	}
}

var StackControlCreateIngress = stackControlCall[orcapi.ControlCreateRequest[orcapi.IngressSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createIngress")
var StackControlCreatePublicIp = stackControlCall[orcapi.ControlCreateRequest[orcapi.PublicIPSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createPublicIp")
var StackControlCreatePrivateNetwork = stackControlCall[orcapi.ControlCreateRequest[orcapi.PrivateNetworkSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createPrivateNetwork")
var StackControlCreatePrivateNetworkIp = stackControlCall[orcapi.ControlCreateRequest[orcapi.PrivateNetworkIpSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createPrivateNetworkIp")
var StackControlCreateJob = stackControlCall[orcapi.ControlCreateRequest[orcapi.JobSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createJob")
var StackControlCreateService = stackControlCall[orcapi.ControlCreateRequest[orcapi.ServiceSpecification], fnd.BulkResponse[fnd.FindByStringId]]("createService")

var StackControlUpdateMembers = stackControlCall[orcapi.ServicesControlUpdateMembersRequest, util.Empty]("updateMembers")
var StackControlDeleteIngress = stackControlCall[orcapi.IngressesControlDeleteRequest, fnd.BulkResponse[util.Empty]]("deleteIngress")

var StackControlBrowseIngresses = stackControlCall[orcapi.IngressesControlBrowseRequest, fnd.PageV2[orcapi.Ingress]]("browseIngresses")
var StackControlBrowseServices = stackControlCall[orcapi.ServicesControlBrowseRequest, fnd.PageV2[orcapi.Service]]("browseServices")
var StackControlBrowseJobs = stackControlCall[orcapi.JobsControlBrowseRequest, fnd.PageV2[orcapi.Job]]("browseJobs")
var StackControlBrowsePublicIps = stackControlCall[orcapi.PublicIpsControlBrowseRequest, fnd.PageV2[orcapi.PublicIp]]("browsePublicIps")
var StackControlBrowsePrivateNetworks = stackControlCall[orcapi.PrivateNetworksControlBrowseRequest, fnd.PageV2[orcapi.PrivateNetwork]]("browsePrivateNetworks")
var StackControlBrowsePrivateNetworkIps = stackControlCall[orcapi.PrivateNetworkIpsControlBrowseRequest, fnd.PageV2[orcapi.PrivateNetworkIp]]("browsePrivateNetworkIps")

var StackControlRetrieveIngress = stackControlCall[orcapi.IngressesControlRetrieveRequest, orcapi.Ingress]("retrieveIngress")
var StackControlRetrieveService = stackControlCall[orcapi.ServicesControlRetrieveRequest, orcapi.Service]("retrieveService")
var StackControlRetrieveJob = stackControlCall[orcapi.JobsControlRetrieveRequest, orcapi.Job]("retrieveJob")
var StackControlRetrievePublicIp = stackControlCall[orcapi.PublicIpsControlRetrieveRequest, orcapi.PublicIp]("retrievePublicIp")
var StackControlRetrievePrivateNetwork = stackControlCall[orcapi.PrivateNetworksControlRetrieveRequest, orcapi.PrivateNetwork]("retrievePrivateNetwork")
var StackControlRetrievePrivateNetworkIp = stackControlCall[orcapi.PrivateNetworkIpsControlRetrieveRequest, orcapi.PrivateNetworkIp]("retrievePrivateNetworkIp")

var StackControlUpdateJobLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.JobsUpdateLabelsRequest], util.Empty]("updateJobLabels")
var StackControlUpdateServiceLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.ServicesUpdateLabelsRequest], util.Empty]("updateServiceLabels")
var StackControlUpdateIngressLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.IngressesUpdateLabelsRequest], util.Empty]("updateIngressLabels")
var StackControlUpdatePublicIpLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.PublicIpsUpdateLabelsRequest], util.Empty]("updatePublicIpLabels")
var StackControlUpdatePrivateNetworkLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.PrivateNetworksUpdateLabelsRequest], util.Empty]("updatePrivateNetworkLabels")
var StackControlUpdatePrivateNetworkIpLabels = stackControlCall[orcapi.ControlMutateRequest[orcapi.PrivateNetworkIpsUpdateLabelsRequest], util.Empty]("updatePrivateNetworkIpLabels")

var StackControlTerminateJobs = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("terminateJobs")
var StackControlSuspendJobs = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("suspendJobs")
var StackControlUnsuspendJobs = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("unsuspendJobs")
var StackControlExtendJobs = stackControlCall[orcapi.ControlMutateRequest[orcapi.JobsExtendRequestItem], fnd.BulkResponse[util.Empty]]("extendJobs")
var StackControlRenameJobs = stackControlCall[orcapi.ControlMutateRequest[orcapi.JobRenameRequest], util.Empty]("renameJobs")

var StackControlDeleteService = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("deleteService")
var StackControlDeletePublicIp = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("deletePublicIp")
var StackControlDeletePrivateNetwork = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("deletePrivateNetwork")
var StackControlDeletePrivateNetworkIp = stackControlCall[orcapi.ControlMutateRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]("deletePrivateNetworkIp")

var StackControlUpdateServiceSpec = stackControlCall[orcapi.ControlMutateRequest[orcapi.ResourceUpdateAndId[orcapi.ServicesUpdateRequest]], util.Empty]("updateServiceSpec")
var StackControlSetIngressTarget = stackControlCall[orcapi.ControlMutateRequest[orcapi.IngressesSetTargetRequest], util.Empty]("setIngressTarget")
var StackControlUpdatePublicIpFirewall = stackControlCall[orcapi.ControlMutateRequest[orcapi.PublicIpUpdateFirewallRequest], util.Empty]("updatePublicIpFirewall")

type StackControlProductsRequest struct {
	StackCredentialAuth
}

func stackControlProductsCall[Resp any](operation string) rpc.Call[StackControlProductsRequest, Resp] {
	return rpc.Call[StackControlProductsRequest, Resp]{
		BaseContext: StackControlBaseContext,
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesPublic,
		Operation:   operation,
		Audit: rpc.AuditRules{
			Transformer: stackControlAuditTransformer,
		},
	}
}

var StackControlIngressProducts = stackControlProductsCall[[]orcapi.IngressSupport]("ingressProducts")
var StackControlServiceProducts = stackControlProductsCall[[]orcapi.ServiceSupport]("serviceProducts")
var StackControlJobProducts = stackControlProductsCall[[]orcapi.JobSupport]("jobProducts")
var StackControlPublicIpProducts = stackControlProductsCall[[]orcapi.PublicIpSupport]("publicIpProducts")
var StackControlPrivateNetworkProducts = stackControlProductsCall[[]orcapi.PrivateNetworkSupport]("privateNetworkProducts")
var StackControlPrivateNetworkIpProducts = stackControlProductsCall[[]orcapi.PrivateNetworkIpSupport]("privateNetworkIpProducts")
