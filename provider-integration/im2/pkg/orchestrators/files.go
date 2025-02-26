package orchestrators

import (
	"fmt"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/pkg/apm"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/util"
)

var (
	MetricFilesUploadSessionsCurrent = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "ucloud_files_upload_sessions_current",
		Help: "Number of upload sessions that are currently open",
	})
	MetricFilesUploadSessionsTotal = promauto.NewCounter(prometheus.CounterOpts{
		Name: "ucloud_files_upload_sessions_total",
		Help: "Total number of upload sessions that has been opened",
	})
	MetricFilesDownloadSessionsCurrent = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "ucloud_files_download_sessions_current",
		Help: "The number of download sessions that are currently open",
	})
	MetricFilesDownloadSessionsTotal = promauto.NewCounter(prometheus.CounterOpts{
		Name: "ucloud_files_download_sessions_total",
		Help: "Total number of download sessions that has been opened",
	})
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

type Drive struct {
	Resource
	Specification DriveSpecification `json:"specification"`
	Updates       []ResourceUpdate   `json:"updates"`
}

type DriveSpecification struct {
	Title   string               `json:"title"`
	Product apm.ProductReference `json:"product"`
}

type FSSupport struct {
	Product apm.ProductReference `json:"product"`

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
		TrashSupport             bool `json:"trashSupport"`
		IsReadOnly               bool `json:"isReadOnly"`
		SearchSupported          bool `json:"searchSupported"`
		StreamingSearchSupported bool `json:"streamingSearchSupported"`
		SharesSupported          bool `json:"sharesSupported"`
		OpenInTerminal           bool `json:"openInTerminal"`
	} `json:"files"`
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

type UploadProtocol string

const (
	UploadProtocolChunked     UploadProtocol = "CHUNKED"
	UploadProtocolWebSocketV1 UploadProtocol = "WEBSOCKET_V1"
	UploadProtocolWebSocketV2 UploadProtocol = "WEBSOCKET_V2"
)

// API
// =====================================================================================================================

const fileCtrlContext = "/api/files/control/"
const driveCtrlContext = "/api/files/collections/control/"
const driveCtrlNamespace = "files.collections.control."
const shareCtrlNamespace = "shares.control."
const shareCtrlContext = "/api/shares/control/"

func RetrieveDrive(driveId string) (Drive, error) {
	return c.ApiRetrieve[Drive](
		driveCtrlNamespace+"retrieve",
		driveCtrlContext,
		"",
		[]string{"id", driveId},
	)
}

type BrowseDrivesFlags struct {
	FilterProviderIds util.Option[string] `json:"filterProviderIds"`
}

func BrowseDrives(next string, flags BrowseDrivesFlags) (fnd.PageV2[Drive], error) {
	return c.ApiBrowse[fnd.PageV2[Drive]](
		driveCtrlNamespace+"browse",
		driveCtrlContext,
		"",
		append([]string{"next", next}, c.StructToParameters(flags)...),
	)
}

func RegisterDrives(drives []ProviderRegisteredResource[DriveSpecification]) (ids []string, err error) {
	resp, err := c.ApiUpdate[fnd.BulkResponse[fnd.FindByStringId]](
		driveCtrlNamespace+"register",
		driveCtrlContext,
		"register",
		fnd.BulkRequest[ProviderRegisteredResource[DriveSpecification]]{
			Items: drives,
		},
	)

	if err != nil {
		return nil, err
	}

	for _, item := range resp.Responses {
		ids = append(ids, item.Id)
	}
	return ids, nil
}

func RegisterDrive(drive ProviderRegisteredResource[DriveSpecification]) (string, error) {
	ids, err := RegisterDrives([]ProviderRegisteredResource[DriveSpecification]{drive})
	if err != nil {
		return "", err
	}
	if len(ids) != 1 {
		return "", fmt.Errorf("malformed response from UCloud did not receive exactly one ID back")
	}
	return ids[0], nil
}

func UpdateShares(request fnd.BulkRequest[ResourceUpdateAndId[ShareUpdate]]) error {
	_, err := c.ApiUpdate[util.Empty](
		shareCtrlNamespace+"update",
		shareCtrlContext,
		"update",
		request,
	)
	return err
}

func RetrieveShare(id string) (Share, error) {
	return c.ApiRetrieve[Share](
		shareCtrlNamespace+"retrieve",
		shareCtrlContext,
		"",
		[]string{"id", id},
	)
}
