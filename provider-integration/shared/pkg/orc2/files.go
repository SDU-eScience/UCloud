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
	ResourceFlags `json:"resourceFlags"`

	IncludePermissions util.Option[bool] `json:"includePermissions"`
	IncludeTimestamps  util.Option[bool] `json:"includeTimestamps"`
	IncludeSizes       util.Option[bool] `json:"includeSizes"`
	IncludeUnixInfo    util.Option[bool] `json:"includeUnixInfo"`
	IncludeMetadata    util.Option[bool] `json:"includeMetadata"`

	FilterByFileExtension util.Option[string] `json:"filterByFileExtension"`
	FilterPath            util.Option[string] `json:"filterPath"`
	FilterHiddenFiles     util.Option[bool]   `json:"filterHiddenFiles"`

	// AllowUnsupportedInclude removed, just always true
}

var FilesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type FilesSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	FileFlags
}

var FilesSearch = rpc.Call[FilesSearchRequest, fnd.PageV2[Drive]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type FilesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	FileFlags
}

var FilesBrowse = rpc.Call[FilesBrowseRequest, fnd.PageV2[Drive]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type FilesRetrieveRequest struct {
	Id        string `json:"id"`
	FileFlags `json:"fileFlags"`
}

var FilesRetrieve = rpc.Call[FilesRetrieveRequest, Drive]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var FilesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

var FilesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

var FilesMove = rpc.Call[fnd.BulkRequest[FilesSourceAndDestination], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "move",
}

var FilesCopy = rpc.Call[fnd.BulkRequest[FilesSourceAndDestination], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
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
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "upload",
}

type FilesCreateDownloadResponse struct {
	Endpoint string `json:"endpoint"`
}

var FilesCreateDownload = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[FilesCreateDownloadResponse]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "download",
}

type FilesCreateFolderRequest struct {
	Id             string              `json:"id"`
	ConflictPolicy WriteConflictPolicy `json:"conflictPolicy"`
}

var FilesCreateFolder = rpc.Call[fnd.BulkRequest[FilesCreateFolderRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
	Operation:   "folder",
}

var FilesTrash = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "trash",
}

var FilesEmptyTrash = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: driveNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "emptyTrash",
}

var FilesStreamingSearch = rpc.Call[util.Empty, util.Empty]{} // TODO
