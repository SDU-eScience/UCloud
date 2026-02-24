package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type PrivateNetwork struct {
	Resource
	Specification PrivateNetworkSpecification `json:"specification"`
	Status        PrivateNetworkStatus        `json:"status"`
}

type PrivateNetworkSpecification struct {
	// Name must be a non-empty string with no new lines or invisible characters. It must not be longer than 256
	// characters.
	Name string `json:"name"`

	// Subdomain must be a valid DNS hostname. No dots are allowed in this. It must not be longer than 256 characters.
	Subdomain string `json:"subdomain"`

	ResourceSpecification
}

type PrivateNetworkStatus struct {
	// Members correspond to all jobs currently participating in the network. Each element references a job by its ID.
	// Only jobs which are in a non-terminal state show up in this list. Thus jobs which are in SUCCESS, FAILURE or
	// EXPIRED will not show up. Jobs which are IN_QUEUE, SUSPENDED or RUNNING will appear.
	Members []string `json:"members"`
}

type PrivateNetworkSupport struct {
	Product apm.ProductReference `json:"product"`
}

type PrivateNetworkFlags struct {
	ResourceFlags
}

const privateNetworkContext = "private-networks"

// Private Network API
// =====================================================================================================================

var PrivateNetworksCreate = rpc.Call[fnd.BulkRequest[PrivateNetworkSpecification], fnd.BulkResponse[PrivateNetwork]]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var PrivateNetworksDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworksSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	PrivateNetworkFlags
}

var PrivateNetworksSearch = rpc.Call[PrivateNetworksSearchRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworksBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkFlags
}

var PrivateNetworksBrowse = rpc.Call[PrivateNetworksBrowseRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type PrivateNetworksRetrieveRequest struct {
	Id string
	PrivateNetworkFlags
}

var PrivateNetworksRetrieve = rpc.Call[PrivateNetworksRetrieveRequest, PrivateNetwork]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var PrivateNetworksUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var PrivateNetworksRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[PrivateNetworkSupport]]{
	BaseContext: privateNetworkContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// Private Network Control API
// =====================================================================================================================

const privateNetworkControlNamespace = "private-networks/control"

type PrivateNetworksControlRetrieveRequest struct {
	Id string `json:"id"`
	PrivateNetworkFlags
}

var PrivateNetworksControlRetrieve = rpc.Call[PrivateNetworksControlRetrieveRequest, PrivateNetwork]{
	BaseContext: privateNetworkControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type PrivateNetworksControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkFlags
}

var PrivateNetworksControlBrowse = rpc.Call[PrivateNetworksControlBrowseRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext: privateNetworkControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var PrivateNetworksControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[PrivateNetworkSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: privateNetworkControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

// Private Network Provider API
// =====================================================================================================================

const privateNetworkProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/private-networks"

var PrivateNetworksProviderCreate = rpc.Call[fnd.BulkRequest[PrivateNetwork], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: privateNetworkProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var PrivateNetworksProviderDelete = rpc.Call[fnd.BulkRequest[PrivateNetwork], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var PrivateNetworksProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[PrivateNetworkSupport]]{
	BaseContext: privateNetworkProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var PrivateNetworksProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[PrivateNetwork]], fnd.BulkResponse[util.Empty]]{
	BaseContext: privateNetworkProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}
