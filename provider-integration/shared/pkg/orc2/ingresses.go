package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Ingress struct {
	Resource
	Specification IngressSpecification `json:"specification"`
	Status        IngressStatus        `json:"status"`
	Updates       []IngressUpdate      `json:"updates"`
}

type IngressSupport struct {
	Prefix  string               `json:"domainPrefix"`
	Suffix  string               `json:"domainSuffix"`
	Product apm.ProductReference `json:"product"`
}

type IngressSpecification struct {
	Domain  string               `json:"domain"`
	Product apm.ProductReference `json:"product"`
	ResourceSpecification
}

type IngressStatus struct {
	BoundTo []string     `json:"boundTo"`
	State   IngressState `json:"state"`
}

type IngressUpdate struct {
	State     util.Option[IngressState] `json:"state,omitempty"`
	Timestamp fnd.Timestamp             `json:"timestamp"`
	Status    util.Option[string]       `json:"status,omitempty"`
}

type IngressState string

const (
	IngressStatePreparing   IngressState = "PREPARING"
	IngressStateReady       IngressState = "READY"
	IngressStateUnavailable IngressState = "UNAVAILABLE"
)

type IngressFlags struct {
	ResourceFlags
}

// Ingress API
// =====================================================================================================================

const ingressNamespace = "ingresses"

var IngressesCreate = rpc.Call[fnd.BulkRequest[IngressSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var IngressesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type IngressesSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	IngressFlags
}

var IngressesSearch = rpc.Call[IngressesSearchRequest, fnd.PageV2[Ingress]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type IngressesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	IngressFlags
}

var IngressesBrowse = rpc.Call[IngressesBrowseRequest, fnd.PageV2[Ingress]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type IngressesRetrieveRequest struct {
	Id string
	IngressFlags
}

var IngressesRetrieve = rpc.Call[IngressesRetrieveRequest, Ingress]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var IngressesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var IngressesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[IngressSupport]]{
	BaseContext: ingressNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// Ingress Control API
// =====================================================================================================================

const ingressControlNamespace = "ingresses/control"

type IngressesControlRetrieveRequest struct {
	Id string `json:"id"`
	IngressFlags
}

var IngressesControlRetrieve = rpc.Call[IngressesControlRetrieveRequest, Ingress]{
	BaseContext: ingressControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type IngressesControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	IngressFlags
}

var IngressesControlBrowse = rpc.Call[IngressesControlBrowseRequest, fnd.PageV2[Ingress]]{
	BaseContext: ingressControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var IngressesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[PublicIPSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: ingressControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var IngressesControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[IngressUpdate]], util.Empty]{
	BaseContext: ingressControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

// Ingress Provider API
// =====================================================================================================================

const ingressProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/ingresses"

var IngressesProviderCreate = rpc.Call[fnd.BulkRequest[Ingress], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: ingressProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var IngressesProviderDelete = rpc.Call[fnd.BulkRequest[Ingress], fnd.BulkResponse[util.Empty]]{
	BaseContext: ingressProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var IngressesProviderVerify = rpc.Call[fnd.BulkRequest[Ingress], util.Empty]{
	BaseContext: ingressProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "verify",
}

var IngressesProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[IngressSupport]]{
	BaseContext: ingressProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var IngressesProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[Ingress]], fnd.BulkResponse[util.Empty]]{
	BaseContext: ingressProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}
