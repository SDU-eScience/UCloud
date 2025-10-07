package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Share struct {
	Resource
	Specification ShareSpecification `json:"specification"`
	Status        ShareStatus        `json:"status"`
	Updates       []ShareUpdate      `json:"updates"`
}

type ShareSpecification struct {
	SharedWith     string               `json:"sharedWith"`
	SourceFilePath string               `json:"sourceFilePath"`
	Permissions    []Permission         `json:"permissions"`
	Product        apm.ProductReference `json:"product"`
}

type ShareStatus struct {
	ShareAvailableAt util.Option[string] `json:"shareAvailableAt"`
	State            ShareState          `json:"state"`
}

type ShareUpdate struct {
	ShareAvailableAt util.Option[string] `json:"shareAvailableAt"`
	Timestamp        fnd.Timestamp       `json:"timestamp"`
	Status           util.Option[string] `json:"status"`
}

type ShareState string

const (
	ShareStateApproved ShareState = "APPROVED"
	ShareStateRejected ShareState = "REJECTED"
	ShareStatePending  ShareState = "PENDING"
)

type ShareSupport struct {
	Product apm.ProductReference `json:"product"`
	Type    ShareType            `json:"type"`
}

type ShareType string

const ShareTypeManaged ShareType = "UCLOUD_MANAGED_COLLECTION"

type ShareFlags struct {
	ResourceFlags
	FilterIngoing bool `json:"filterIngoing"`
}

// Share API
// =====================================================================================================================

const shareNamespace = "shares"

var SharesCreate = rpc.Call[fnd.BulkRequest[ShareSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var SharesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type SharesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ShareFlags
}

var SharesBrowse = rpc.Call[SharesBrowseRequest, fnd.PageV2[Share]]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type SharesRetrieveRequest struct {
	Id string
	ShareFlags
}

var SharesRetrieve = rpc.Call[SharesRetrieveRequest, Share]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var SharesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[ShareSupport]]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

var SharesApprove = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "approve",
}

var SharesReject = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "reject",
}

type SharesUpdatePermissionsRequest struct {
	Id          string       `json:"id"`
	Permissions []Permission `json:"permissions"`
}

var SharesUpdatePermissions = rpc.Call[fnd.BulkRequest[SharesUpdatePermissionsRequest], util.Empty]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "permissions",
}

type SharesBrowseOutgoingRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

type ShareGroupOutgoing struct {
	SourceFilePath string               `json:"sourceFilePath"`
	StorageProduct apm.ProductReference `json:"storageProduct"`
	SharePreview   []SharePreview       `json:"sharePreview"`
}

type SharePreview struct {
	SharedWith  string       `json:"sharedWith"`
	Permissions []Permission `json:"permissions"`
	State       ShareState   `json:"state"`
	ShareId     string       `json:"shareId"`
}

var SharesBrowseOutgoing = rpc.Call[SharesBrowseOutgoingRequest, fnd.PageV2[ShareGroupOutgoing]]{
	BaseContext: shareNamespace,
	Convention:  rpc.ConventionBrowse,
	Operation:   "outgoing",
	Roles:       rpc.RolesEndUser,
}

// Share Control API
// =====================================================================================================================

const shareControlNamespace = "shares/control"

type SharesControlRetrieveRequest struct {
	Id string `json:"id"`
	ShareFlags
}

var SharesControlRetrieve = rpc.Call[SharesControlRetrieveRequest, Share]{
	BaseContext: shareControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type SharesControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ShareFlags
}

var SharesControlBrowse = rpc.Call[SharesControlBrowseRequest, fnd.PageV2[Share]]{
	BaseContext: shareControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var SharesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[ShareSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: shareControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var SharesControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[ShareUpdate]], util.Empty]{
	BaseContext: shareControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

// Share Provider API
// =====================================================================================================================

const shareProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/shares"

var SharesProviderCreate = rpc.Call[fnd.BulkRequest[Share], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: shareProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var SharesProviderDelete = rpc.Call[fnd.BulkRequest[Share], fnd.BulkResponse[util.Empty]]{
	BaseContext: shareProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

// Share links API
// =====================================================================================================================

type ShareLink struct {
	Token       string        `json:"token"`
	Expires     fnd.Timestamp `json:"expires"`
	Permissions []Permission  `json:"permissions"`
	Path        string        `json:"path"`
	SharedBy    string        `json:"sharedBy"`
}

const shareLinksNamespace = "shares/links"

type ShareLinkCreateRequest struct {
	Path string `json:"path"`
}

var ShareLinkCreate = rpc.Call[ShareLinkCreateRequest, ShareLink]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ShareLinkBrowseRequest struct {
	Path         string              `json:"path"`
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var ShareLinkBrowse = rpc.Call[ShareLinkBrowseRequest, fnd.PageV2[ShareLink]]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ShareLinkRetrieveRequest struct {
	Token string `json:"token"`
}

type ShareLinkRetrieveResponse struct {
	Token    string `json:"token"`
	Path     string `json:"path"`
	SharedBy string `json:"sharedBy"`
}

var ShareLinkRetrieve = rpc.Call[ShareLinkRetrieveRequest, ShareLinkRetrieveResponse]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type ShareLinkDeleteRequest struct {
	Token string `json:"token"`
	Path  string `json:"path"`
}

var ShareLinkDelete = rpc.Call[ShareLinkDeleteRequest, util.Empty]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "delete",
	Roles:       rpc.RolesEndUser,
}

type ShareLinkUpdateRequest struct {
	Token       string       `json:"token"`
	Path        string       `json:"path"`
	Permissions []Permission `json:"permissions"`
}

var ShareLinkUpdate = rpc.Call[ShareLinkUpdateRequest, util.Empty]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "update",
	Roles:       rpc.RolesEndUser,
}

type ShareLinkAcceptRequest struct {
	Token string `json:"token"`
}

var ShareLinkAccept = rpc.Call[ShareLinkAcceptRequest, Share]{
	BaseContext: shareLinksNamespace,
	Convention:  rpc.ConventionUpdate,
	Operation:   "accept",
	Roles:       rpc.RolesEndUser,
}
