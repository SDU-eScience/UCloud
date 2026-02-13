package orchestrators

import (
	acc "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Drive struct {
	Resource
	Specification DriveSpecification `json:"specification"`
	Updates       []ResourceUpdate   `json:"updates"`
	Status        DriveStatus        `json:"status"`
}

type DriveStatus struct {
	PreferredDrive bool `json:"preferredDrive"`
	ResourceStatus[FSSupport]
}

func DriveIdFromUCloudPath(path string) (string, bool) {
	components := util.Components(path)
	if len(components) == 0 {
		return "", false
	}

	driveId := components[0]
	return driveId, true
}

type DriveSpecification struct {
	Title   string               `json:"title"`
	Product acc.ProductReference `json:"product"`
}

type FSSupport struct {
	Product acc.ProductReference `json:"product"`

	Stats struct {
		SizeInBytes                  bool `json:"sizeInBytes"`
		SizeIncludingChildrenInBytes bool `json:"sizeIncludingChildrenInBytes"`
		ModifiedAt                   bool `json:"modifiedAt"`
		CreatedAt                    bool `json:"createdAt"`
		AccessedAt                   bool `json:"accessedAt"`
		UnixPermissions              bool `json:"unixPermissions"`
		UnixOwner                    bool `json:"unixOwner"`
		UnixGroup                    bool `json:"unixGroup"`
	} `json:"stats"`

	Collection struct {
		AclModifiable  bool `json:"aclModifiable"`
		UsersCanCreate bool `json:"usersCanCreate"`
		UsersCanDelete bool `json:"usersCanDelete"`
		UsersCanRename bool `json:"usersCanRename"`
	} `json:"collection"`

	Files struct {
		AclModifiable            bool `json:"aclModifiable"`
		TrashSupported           bool `json:"trashSupported"`
		IsReadOnly               bool `json:"isReadOnly"`
		SearchSupported          bool `json:"searchSupported"`
		StreamingSearchSupported bool `json:"streamingSearchSupported"`
		SharesSupported          bool `json:"sharesSupported"`
		OpenInTerminal           bool `json:"openInTerminal"`
	} `json:"files"`
}

type MemberFilesFilter string

const (
	MemberFilesShowMine    MemberFilesFilter = "SHOW_ONLY_MINE"
	MemberFilesShowMembers MemberFilesFilter = "SHOW_ONLY_MEMBER_FILES"
	MemberFilesNoFilter    MemberFilesFilter = "DONT_FILTER_COLLECTIONS"
)

type DriveFlags struct {
	ResourceFlags
	FilterMemberFiles util.Option[MemberFilesFilter] `json:"filterMemberFiles"`
}

// Drive API
// =====================================================================================================================

const driveNamespace = "files/collections"

var DrivesCreate = rpc.Call[fnd.BulkRequest[DriveSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var DrivesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type DriveRenameRequest struct {
	Id       string `json:"id"`
	NewTitle string `json:"newTitle"`
}

var DrivesRename = rpc.Call[fnd.BulkRequest[DriveRenameRequest], util.Empty]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "rename",
}

type DrivesSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	DriveFlags
}

var DrivesSearch = rpc.Call[DrivesSearchRequest, fnd.PageV2[Drive]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type DrivesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	DriveFlags
}

var DrivesBrowse = rpc.Call[DrivesBrowseRequest, fnd.PageV2[Drive]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type DrivesRetrieveRequest struct {
	Id string
	DriveFlags
}

var DrivesRetrieve = rpc.Call[DrivesRetrieveRequest, Drive]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var DrivesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var DrivesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// Drive Control API
// =====================================================================================================================

const driveControlNamespace = "files/collections/control"

type DrivesControlRetrieveRequest struct {
	Id string `json:"id"`
	DriveFlags
}

var DrivesControlRetrieve = rpc.Call[DrivesControlRetrieveRequest, Drive]{
	BaseContext: driveControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type DrivesControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	DriveFlags
}

var DrivesControlBrowse = rpc.Call[DrivesControlBrowseRequest, fnd.PageV2[Drive]]{
	BaseContext: driveControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var DrivesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[DriveSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: driveControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

// Drive Provider API
// =====================================================================================================================

const driveProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/files/collections"

var DrivesProviderCreate = rpc.Call[fnd.BulkRequest[Drive], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var DrivesProviderDelete = rpc.Call[fnd.BulkRequest[Drive], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var DrivesProviderVerify = rpc.Call[fnd.BulkRequest[Drive], util.Empty]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "verify",
}

var DrivesProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[FSSupport]]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

var DrivesProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[Drive]], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}

var DrivesProviderRename = rpc.Call[fnd.BulkRequest[DriveRenameRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "rename",
}
