package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type License struct {
	Resource
	Specification LicenseSpecification `json:"specification"`
	Status        LicenseStatus        `json:"status"`
	Updates       []LicenseUpdate      `json:"updates,omitempty"`
}

type LicenseSpecification struct {
	Product apm.ProductReference `json:"product"`
}

type LicenseStatus struct {
	State   LicenseState `json:"state"`
	BoundTo []string     `json:"boundTo"`
	ResourceStatus[LicenseSupport]
}

type LicenseUpdate struct {
	Timestamp fnd.Timestamp             `json:"timestamp"`
	State     util.Option[LicenseState] `json:"state,omitempty"`
	Binding   util.Option[JobBinding]   `json:"binding"`
	Status    util.Option[string]       `json:"status"`
}

type LicenseState string

const (
	LicenseStatePreparing   LicenseState = "PREPARING"
	LicenseStateReady       LicenseState = "READY"
	LicenseStateUnavailable LicenseState = "UNAVAILABLE"
)

type LicenseSupport struct {
	Product apm.ProductReference `json:"product"`
}

type LicenseFlags struct {
	ResourceFlags
}

// License API
// =====================================================================================================================

const licenseNamespace = "licenses"

var LicensesCreate = rpc.Call[fnd.BulkRequest[LicenseSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var LicensesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type LicenseRenameRequest struct {
	Id       string `json:"id"`
	NewTitle string `json:"newTitle"`
}

type LicensesSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	LicenseFlags
}

var LicensesSearch = rpc.Call[LicensesSearchRequest, fnd.PageV2[License]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type LicensesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	LicenseFlags
}

var LicensesBrowse = rpc.Call[LicensesBrowseRequest, fnd.PageV2[License]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type LicensesRetrieveRequest struct {
	Id string
	LicenseFlags
}

var LicensesRetrieve = rpc.Call[LicensesRetrieveRequest, License]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var LicensesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var LicensesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[LicenseSupport]]{
	BaseContext: licenseNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// License Control API
// =====================================================================================================================

const licenseControlNamespace = "licenses/control"

type LicensesControlRetrieveRequest struct {
	Id string `json:"id"`
	LicenseFlags
}

var LicensesControlRetrieve = rpc.Call[LicensesControlRetrieveRequest, License]{
	BaseContext: licenseControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type LicensesControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	LicenseFlags
}

var LicensesControlBrowse = rpc.Call[LicensesControlBrowseRequest, fnd.PageV2[License]]{
	BaseContext: licenseControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var LicensesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[LicenseSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: licenseControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var LicensesControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[LicenseUpdate]], util.Empty]{
	BaseContext: licenseControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

// License Provider API
// =====================================================================================================================

const licenseProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/licenses"

var LicensesProviderCreate = rpc.Call[fnd.BulkRequest[License], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: licenseProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var LicensesProviderDelete = rpc.Call[fnd.BulkRequest[License], fnd.BulkResponse[util.Empty]]{
	BaseContext: licenseProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var LicensesProviderVerify = rpc.Call[fnd.BulkRequest[License], util.Empty]{
	BaseContext: licenseProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "verify",
}

var LicensesProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[LicenseSupport]]{
	BaseContext: licenseProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var LicensesProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[License]], fnd.BulkResponse[util.Empty]]{
	BaseContext: licenseProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}
