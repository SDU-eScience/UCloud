package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type PrivateNetworkIpSpecification struct {
	Network string `json:"network"`
	IpAddress util.Option[string] `json:"ipAddress"`

	ResourceSpecification
}

type PrivateNetworkIpStatus struct {
	IpAddress util.Option[string] `json:"ipAddress"`

	ResourceStatus[PrivateNetworkIpSupport]
}

type PrivateNetworkIpSupport struct {
	Product apm.ProductReference `json:"product"`
}

type PrivateNetworkIpFlags struct {
	ResourceFlags
}

type PrivateNetworkIp struct {
	Resource
	Specification PrivateNetworkIpSpecification `json:"specification"`
	Status        PrivateNetworkIpStatus        `json:"status"`
}

const privateNetworkIpContext = "private-network-ips"

// Private Network IP API
// =====================================================================================================================

var PrivateNetworkIpsCreate = rpc.Call[fnd.BulkRequest[PrivateNetworkIpSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var PrivateNetworkIpsDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworkIpsSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	PrivateNetworkIpFlags
}

var PrivateNetworkIpsSearch = rpc.Call[PrivateNetworkIpsSearchRequest, fnd.PageV2[PrivateNetworkIp]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworkIpsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkIpFlags
}

var PrivateNetworkIpsBrowse = rpc.Call[PrivateNetworkIpsBrowseRequest, fnd.PageV2[PrivateNetworkIp]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworkIpsRetrieveRequest struct {
	Id string
	PrivateNetworkIpFlags
}

var PrivateNetworkIpsRetrieve = rpc.Call[PrivateNetworkIpsRetrieveRequest, PrivateNetworkIp]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var PrivateNetworkIpsUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type PrivateNetworkIpsUpdateLabelsRequest struct {
	Id     string            `json:"id"`
	Labels map[string]string `json:"labels"`
}

var PrivateNetworkIpsUpdateLabels = rpc.Call[fnd.BulkRequest[PrivateNetworkIpsUpdateLabelsRequest], util.Empty]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateLabels",
}

var PrivateNetworkIpsRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[PrivateNetworkIpSupport]]{
	BaseContext: privateNetworkIpContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// Private Network IP Control API
// =====================================================================================================================

const privateNetworkIpControlNamespace = "private-network-ips/control"

type PrivateNetworkIpsControlRetrieveRequest struct {
	Id string `json:"id"`
	PrivateNetworkIpFlags
}

var PrivateNetworkIpsControlRetrieve = rpc.Call[PrivateNetworkIpsControlRetrieveRequest, PrivateNetworkIp]{
	BaseContext: privateNetworkIpControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type PrivateNetworkIpsControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkIpFlags
}

var PrivateNetworkIpsControlBrowse = rpc.Call[PrivateNetworkIpsControlBrowseRequest, fnd.PageV2[PrivateNetworkIp]]{
	BaseContext: privateNetworkIpControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var PrivateNetworkIpsControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[PrivateNetworkIpSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: privateNetworkIpControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

type PrivateNetworkIpUpdate struct {
	IpAddress util.Option[string] `json:"ipAddress"`
	Timestamp fnd.Timestamp       `json:"timestamp"`
}

var PrivateNetworkIpsControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[PrivateNetworkIpUpdate]], util.Empty]{
	BaseContext: privateNetworkIpControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

var PrivateNetworkIpsControlUpdateLabels = rpc.Call[fnd.BulkRequest[PrivateNetworkIpsUpdateLabelsRequest], util.Empty]{
	BaseContext: privateNetworkIpControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "updateLabels",
}

// Private Network IP Provider API
// =====================================================================================================================

const privateNetworkIpProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/private-network-ips"

var PrivateNetworkIpsProviderCreate = rpc.Call[fnd.BulkRequest[PrivateNetworkIp], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: privateNetworkIpProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var PrivateNetworkIpsProviderDelete = rpc.Call[fnd.BulkRequest[PrivateNetworkIp], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkIpProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var PrivateNetworkIpsProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[PrivateNetworkIpSupport]]{
	BaseContext: privateNetworkIpProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var PrivateNetworkIpsProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[PrivateNetworkIp]], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkIpProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}

var PrivateNetworkIpsProviderOnUpdatedLabels = rpc.Call[fnd.BulkRequest[PrivateNetworkIp], util.Empty]{
	BaseContext: privateNetworkIpProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "onUpdatedLabels",
}
