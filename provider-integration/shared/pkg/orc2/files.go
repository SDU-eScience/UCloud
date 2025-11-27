package orchestrators

import (
	"encoding/json"
	"fmt"

	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

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

	Metadata FileMetadata `json:"metadata"`
}

type FileMetadata struct {
	// TODO(Dan): Templates removed on purpose here. Hopefully this doesn't break the frontend.
	Metadata map[string][]FileMetadataDocument `json:"metadata"`
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

type FilesStreamingSearchRequest struct {
	Flags         FileFlags `json:"flags"`
	Query         string    `json:"query"`
	CurrentFolder string    `json:"currentFolder"`
}

type FilesStreamingSearchResult struct {
	Type  string  `json:"type"` // result or end_of_results
	Batch []UFile `json:"batch"`
}

var FilesStreamingSearch = rpc.Call[util.Empty, util.Empty]{
	BaseContext: filesNamespace,
	Roles:       rpc.RolesPublic,
	Convention:  rpc.ConventionWebSocket,
}

type FilesTransferRequest struct {
	SourcePath      string `json:"sourcePath"`
	DestinationPath string `json:"destinationPath"`
}

var FilesTransfer = rpc.Call[fnd.BulkRequest[FilesTransferRequest], util.Empty]{
	BaseContext: filesNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "transfer",
}

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

type FilesProviderStreamingSearchRequest struct {
	Query         string                  `json:"query"`
	Owner         ResourceOwner           `json:"owner"`
	Flags         FileFlags               `json:"flags"`
	Category      apm.ProductCategoryIdV2 `json:"category"`
	CurrentFolder util.Option[string]     `json:"currentFolder"`
}

type FilesProviderStreamingSearchResult struct {
	Type  string         `json:"type"` // result or end_of_results
	Batch []ProviderFile `json:"batch"`
}

func FilesProviderStreamingSearchEndpoint(providerId string) string {
	return fmt.Sprintf("/ucloud/%s/files", providerId)
}

type FilesProviderTransferRequestType string

const (
	FilesProviderTransferReqTypeInitiateSource      FilesProviderTransferRequestType = "InitiateSource"
	FilesProviderTransferReqTypeInitiateDestination FilesProviderTransferRequestType = "InitiateDestination"
	FilesProviderTransferReqTypeStart               FilesProviderTransferRequestType = "Start"
)

type FilesProviderTransferRequest struct {
	Type FilesProviderTransferRequestType `json:"type"`

	FilesProviderTransferRequestInitiateSource      `json:",omitempty"`
	FilesProviderTransferRequestInitiateDestination `json:",omitempty"`
	FilesProviderTransferRequestStart               `json:",omitempty"`
}

type FilesProviderTransferRequestInitiateSource struct {
	SourcePath          string `json:"sourcePath,omitempty"`
	SourceDrive         Drive  `json:"sourceCollection,omitempty"`
	DestinationProvider string `json:"destinationProvider,omitempty"`
}

type FilesProviderTransferRequestInitiateDestination struct {
	DestinationPath    string   `json:"destinationPath,omitempty"`
	DestinationDrive   Drive    `json:"destinationCollection,omitempty"`
	SourceProvider     string   `json:"sourceProvider,omitempty"`
	SupportedProtocols []string `json:"supportedProtocols,omitempty"`
}

type FilesProviderTransferRequestStart struct {
	Session            string          `json:"session,omitempty"`
	SelectedProtocol   string          `json:"selectedProtocol,omitempty"`
	ProtocolParameters json.RawMessage `json:"protocolParameters,omitempty"`
}

type FilesProviderTransferResponse struct {
	Type FilesProviderTransferRequestType `json:"type"`

	FilesProviderTransferResponseInitiateSource      `json:",omitempty"`
	FilesProviderTransferResponseInitiateDestination `json:",omitempty"`
	FilesProviderTransferResponseStart               `json:",omitempty"`
}

type FilesProviderTransferResponseInitiateSource struct {
	Session            string   `json:"session,omitempty"`
	SupportedProtocols []string `json:"supportedProtocols,omitempty"`
}

type FilesProviderTransferResponseInitiateDestination struct {
	SelectedProtocol   string          `json:"selectedProtocol,omitempty"`
	ProtocolParameters json.RawMessage `json:"protocolParameters,omitempty"`
}

type FilesProviderTransferResponseStart struct {
}

var FilesProviderTransfer = rpc.Call[FilesProviderTransferRequest, FilesProviderTransferResponse]{
	BaseContext: fileProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "transfer",
}
