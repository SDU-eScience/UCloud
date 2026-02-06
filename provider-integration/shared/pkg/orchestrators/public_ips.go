package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type PublicIPSpecification struct {
	Product  apm.ProductReference  `json:"product"`
	Firewall util.Option[Firewall] `json:"firewall"`
}

type Firewall struct {
	OpenPorts []PortRangeAndProto `json:"openPorts"`
}

type FirewallAndIp struct {
	Ip       PublicIp `json:"networkIp"`
	Firewall Firewall `json:"firewall"`
}

type PortRangeAndProto struct {
	Start    int        `json:"start"`
	End      int        `json:"end"`
	Protocol IpProtocol `json:"protocol"`
}

type IpProtocol string

const (
	IpProtocolTcp IpProtocol = "TCP"
	IpProtocolUdp IpProtocol = "UDP"
)

var IpProtocolOptions = []IpProtocol{
	IpProtocolTcp,
	IpProtocolUdp,
}

type PublicIpUpdate struct {
	State           util.Option[PublicIpState] `json:"state"`
	ChangeIpAddress util.Option[bool]          `json:"changeIpAddress"`
	NewIpAddress    util.Option[string]        `json:"newIpAddress"`
	Timestamp       fnd.Timestamp              `json:"timestamp"`
	Binding         util.Option[JobBinding]    `json:"binding"`
}

type PublicIpState string

const (
	PublicIpStatePreparing   PublicIpState = "PREPARING"
	PublicIpStateReady       PublicIpState = "READY"
	PublicIpStateUnavailable PublicIpState = "UNAVAILABLE"
)

type JobBinding struct {
	Kind JobBindingKind `json:"kind"`
	Job  string         `json:"job"`
}

type JobBindingKind string

const (
	JobBindingKindBind   JobBindingKind = "BIND"
	JobBindingKindUnbind JobBindingKind = "UNBIND"
)

type PublicIpStatus struct {
	State     PublicIpState       `json:"state"`
	BoundTo   []string            `json:"boundTo"`
	IpAddress util.Option[string] `json:"ipAddress"`
	ResourceStatus[PublicIpSupport]
}

type PublicIp struct {
	Resource
	Specification PublicIPSpecification `json:"specification"`
	Status        PublicIpStatus        `json:"status"`
	Updates       []PublicIpUpdate      `json:"updates,omitempty"`
}

type PublicIpSupport struct {
	Product  apm.ProductReference `json:"product"`
	Firewall FirewallSupport      `json:"firewall"`
}

type FirewallSupport struct {
	Enabled bool `json:"enabled"`
}

type PublicIpFlags struct {
	ResourceFlags
}

// Public IP API
// =====================================================================================================================

const publicIpNamespace = "networkips"

var PublicIpsCreate = rpc.Call[fnd.BulkRequest[PublicIPSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var PublicIpsDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type PublicIpsSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	PublicIpFlags
}

var PublicIpsSearch = rpc.Call[PublicIpsSearchRequest, fnd.PageV2[PublicIp]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type PublicIpsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PublicIpFlags
}

var PublicIpsBrowse = rpc.Call[PublicIpsBrowseRequest, fnd.PageV2[PublicIp]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type PublicIpsRetrieveRequest struct {
	Id string
	PublicIpFlags
}

var PublicIpsRetrieve = rpc.Call[PublicIpsRetrieveRequest, PublicIp]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var PublicIpsUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var PublicIpsRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[PublicIpSupport]]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

type PublicIpUpdateFirewallRequest struct {
	Id       string   `json:"id"`
	Firewall Firewall `json:"firewall"`
}

var PublicIpsUpdateFirewall = rpc.Call[fnd.BulkRequest[PublicIpUpdateFirewallRequest], util.Empty]{
	BaseContext: publicIpNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "firewall",
}

// Public IP Control API
// =====================================================================================================================

const publicIpControlNamespace = "networkips/control"

type PublicIpsControlRetrieveRequest struct {
	Id string `json:"id"`
	PublicIpFlags
}

var PublicIpsControlRetrieve = rpc.Call[PublicIpsControlRetrieveRequest, PublicIp]{
	BaseContext: publicIpControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type PublicIpsControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PublicIpFlags
}

var PublicIpsControlBrowse = rpc.Call[PublicIpsControlBrowseRequest, fnd.PageV2[PublicIp]]{
	BaseContext: publicIpControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var PublicIpsControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[PublicIPSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: publicIpControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var PublicIpsControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[PublicIpUpdate]], util.Empty]{
	BaseContext: publicIpControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

// Public IP Provider API
// =====================================================================================================================

const publicIpProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/networkips"

var PublicIpsProviderCreate = rpc.Call[fnd.BulkRequest[PublicIp], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var PublicIpsProviderDelete = rpc.Call[fnd.BulkRequest[PublicIp], fnd.BulkResponse[util.Empty]]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var PublicIpsProviderVerify = rpc.Call[fnd.BulkRequest[PublicIp], util.Empty]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "verify",
}

var PublicIpsProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[PublicIpSupport]]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var PublicIpsProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[PublicIp]], fnd.BulkResponse[util.Empty]]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}

type PublicIpProviderUpdateFirewallRequest struct {
	PublicIp PublicIp `json:"networkIp"`
	Firewall Firewall `json:"firewall"`
}

var PublicIpsProviderUpdateFirewall = rpc.Call[fnd.BulkRequest[PublicIpProviderUpdateFirewallRequest], util.Empty]{
	BaseContext: publicIpProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "firewall",
}
