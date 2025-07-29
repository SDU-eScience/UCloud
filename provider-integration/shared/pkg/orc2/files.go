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
	State            ShareState          `json:"newState"`
}

type ShareUpdate struct {
	NewState         ShareState          `json:"newState"`
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

type UFileSpecification struct {
	Collection string               `json:"collection"`
	Product    apm.ProductReference `json:"product"`
}

type UFile struct {
	Resource
	Specification UFileSpecification `json:"specification"`
	Status        UFileStatus        `json:"status"`
}

type ProviderFile struct {
	Id                string        `json:"id,omitempty"`
	Status            UFileStatus   `json:"status"`
	CreatedAt         fnd.Timestamp `json:"createdAt"`
	LegacySensitivity string        `json:"legacySensitivity,omitempty"`
}

type UFileStatus struct {
	Type FileType     `json:"type"`
	Icon FileIconHint `json:"icon,omitempty"`

	SizeInBytes                  util.Option[int64] `json:"sizeInBytes"`
	SizeIncludingChildrenInBytes util.Option[int64] `json:"sizeIncludingChildrenInBytes"`

	ModifiedAt fnd.Timestamp `json:"modifiedAt"`
	AccessedAt fnd.Timestamp `json:"accessedAt"`

	UnixMode  int `json:"unixMode"`
	UnixOwner int `json:"unixOwner"`
	UnixGroup int `json:"unixGroup"`
}

type FileIconHint string

const (
	FileIconHintNone            FileIconHint = ""
	FileIconHintDirectoryStar   FileIconHint = "DIRECTORY_STAR"
	FileIconHintDirectoryShares FileIconHint = "DIRECTORY_SHARES"
	FileIconHintDirectoryTrash  FileIconHint = "DIRECTORY_TRASH"
	FileIconHintDirectoryJobs   FileIconHint = "DIRECTORY_JOBS"
)

type FileType string

const (
	FileTypeFile      FileType = "FILE"
	FileTypeDirectory FileType = "DIRECTORY"
)

type WriteConflictPolicy string

const (
	WriteConflictPolicyRename      WriteConflictPolicy = "RENAME"
	WriteConflictPolicyReject      WriteConflictPolicy = "REJECT"
	WriteConflictPolicyReplace     WriteConflictPolicy = "REPLACE"
	WriteConflictPolicyMergeRename WriteConflictPolicy = "MERGE_RENAME"
)

type UploadType string

const (
	UploadTypeFile   UploadType = "FILE"
	UploadTypeFolder UploadType = "FOLDER"
)

type UploadProtocol string

const (
	UploadProtocolChunked     UploadProtocol = "CHUNKED"
	UploadProtocolWebSocketV1 UploadProtocol = "WEBSOCKET_V1"
	UploadProtocolWebSocketV2 UploadProtocol = "WEBSOCKET_V2"
)

// File API
// =====================================================================================================================

const filesNamespace = "files"

type FilesSourceAndDestination struct {
	SourcePath      string              `json:"oldId"`
	DestinationPath string              `json:"newId"`
	ConflictPolicy  WriteConflictPolicy `json:"conflictPolicy"`
}

type FileFlags struct {
	ResourceFlags

	IncludePermissions util.Option[bool] `json:"includePermissions"`
	IncludeTimestamps  util.Option[bool] `json:"includeTimestamps"`
	IncludeSizes       util.Option[bool] `json:"includeSizes"`
	IncludeUnixInfo    util.Option[bool] `json:"includeUnixInfo"`
	IncludeMetadata    util.Option[bool] `json:"includeMetadata"`

	FilterByFileExtension util.Option[string] `json:"filterByFileExtension"`
	Path                  util.Option[string] `json:"path"`
	FilterHiddenFiles     util.Option[bool]   `json:"filterHiddenFiles"`

	// AllowUnsupportedInclude removed, just always true
	// TODO sort?
}

var FilesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type FilesSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	FileFlags
}

var FilesSearch = rpc.Call[FilesSearchRequest, fnd.PageV2[UFile]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type FilesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	FileFlags
}

var FilesBrowse = rpc.Call[FilesBrowseRequest, fnd.PageV2[UFile]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type FilesRetrieveRequest struct {
	Id string `json:"id"`
	FileFlags
}

var FilesRetrieve = rpc.Call[FilesRetrieveRequest, UFile]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var FilesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

var FilesMove = rpc.Call[fnd.BulkRequest[FilesSourceAndDestination], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "move",
}

var FilesCopy = rpc.Call[fnd.BulkRequest[FilesSourceAndDestination], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "copy",
}

type FilesCreateUploadRequest struct {
	Id                 string              `json:"id"`
	Type               UploadType          `json:"type"`
	SupportedProtocols []UploadProtocol    `json:"supportedProtocols"`
	ConflictPolicy     WriteConflictPolicy `json:"conflictPolicy"`
}

type FilesCreateUploadResponse struct { // TODO(Dan): Used to be nullable
	Endpoint string         `json:"endpoint"`
	Protocol UploadProtocol `json:"protocol"`
	Token    string         `json:"token"`
}

var FilesCreateUpload = rpc.Call[fnd.BulkRequest[FilesCreateUploadRequest], fnd.BulkResponse[FilesCreateUploadResponse]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "upload",
}

type FilesCreateDownloadResponse struct {
	Endpoint string `json:"endpoint"`
}

var FilesCreateDownload = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[FilesCreateDownloadResponse]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "download",
}

type FilesCreateFolderRequest struct {
	Id             string              `json:"id"`
	ConflictPolicy WriteConflictPolicy `json:"conflictPolicy"`
}

var FilesCreateFolder = rpc.Call[fnd.BulkRequest[FilesCreateFolderRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "folder",
}

var FilesTrash = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "trash",
}

var FilesEmptyTrash = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "emptyTrash",
}

var FilesStreamingSearch = rpc.Call[util.Empty, util.Empty]{} // TODO

// Files provider
// =====================================================================================================================

const fileProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/files"

var FilesProviderDelete = rpc.Call[fnd.BulkRequest[UFile], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesService,
}

type FilesProviderSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	FileFlags
}

var FilesProviderSearch = rpc.Call[FilesProviderSearchRequest, fnd.PageV2[ProviderFile]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesService,
}

type FilesProviderBrowseRequest struct {
	ResolvedCollection Drive `json:"resolvedCollection"`
	Browse             ResourceBrowseRequest[FileFlags]
}

var FilesProviderBrowse = rpc.Call[FilesProviderBrowseRequest, fnd.PageV2[ProviderFile]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "browse",
}

type FilesProviderRetrieveRequest struct {
	ResolvedCollection Drive                              `json:"resolvedCollection"`
	Retrieve           ResourceRetrieveRequest[FileFlags] `json:"retrieve"`
}

var FilesProviderRetrieve = rpc.Call[FilesProviderRetrieveRequest, ProviderFile]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "retrieve",
}

var FilesProviderRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesService,
	Operation:   "products",
}

type FilesProviderMoveOrCopyRequest struct {
	ResolvedOldCollection Drive               `json:"resolvedOldCollection"`
	ResolvedNewCollection Drive               `json:"resolvedNewCollection"`
	OldId                 string              `json:"oldId"`
	NewId                 string              `json:"newId"`
	ConflictPolicy        WriteConflictPolicy `json:"conflictPolicy"`
}

var FilesProviderMove = rpc.Call[fnd.BulkRequest[FilesProviderMoveOrCopyRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "move",
}

var FilesProviderCopy = rpc.Call[fnd.BulkRequest[FilesProviderMoveOrCopyRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "copy",
}

type FilesProviderCreateUploadRequest struct {
	Id                 string              `json:"id"`
	Type               UploadType          `json:"type"`
	SupportedProtocols []UploadProtocol    `json:"supportedProtocols"`
	ConflictPolicy     WriteConflictPolicy `json:"conflictPolicy"`
	ResolvedCollection Drive               `json:"resolvedCollection"`
}

type FilesProviderCreateUploadResponse struct { // TODO(Dan): Used to be nullable
	Endpoint string         `json:"endpoint"`
	Protocol UploadProtocol `json:"protocol"`
	Token    string         `json:"token"`
}

var FilesProviderCreateUpload = rpc.Call[fnd.BulkRequest[FilesProviderCreateUploadRequest], fnd.BulkResponse[FilesProviderCreateUploadResponse]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesService,
	Operation:   "upload",
}

type FilesProviderCreateDownloadRequest struct {
	Id                 string `json:"id"`
	ResolvedCollection Drive  `json:"resolvedCollection"`
}

type FilesProviderCreateDownloadResponse struct {
	Endpoint string `json:"endpoint"`
}

var FilesProviderCreateDownload = rpc.Call[fnd.BulkRequest[FilesProviderCreateDownloadRequest], fnd.BulkResponse[FilesProviderCreateDownloadResponse]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesService,
	Operation:   "download",
}

type FilesProviderCreateFolderRequest struct {
	Id                 string              `json:"id"`
	ConflictPolicy     WriteConflictPolicy `json:"conflictPolicy"`
	ResolvedCollection Drive               `json:"resolvedCollection"`
}

var FilesProviderCreateFolder = rpc.Call[fnd.BulkRequest[FilesProviderCreateFolderRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesService,
	Operation:   "folder",
}

type FilesProviderTrashRequest struct {
	Id                 string `json:"id"`
	ResolvedCollection Drive  `json:"resolvedCollection"`
}

var FilesProviderTrash = rpc.Call[fnd.BulkRequest[FilesProviderTrashRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "trash",
}

var FilesProviderEmptyTrash = rpc.Call[fnd.BulkRequest[FilesProviderTrashRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "emptyTrash",
}

var FilesProviderStreamingSearch = rpc.Call[util.Empty, util.Empty]{} // TODO

// TODO transfer
