package orchestrators

import (
	"fmt"
	"ucloud.dk/pkg/apm"
	c "ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
)

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
	ProductSupport

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

	// TODO This technically breaks with UCloud's current API because we are sending 0 instead of null.
	//   I think we should change the core/frontend to consider FSSupport when reading 0 values here.

	SizeInBytes                  int64 `json:"sizeInBytes"`
	SizeIncludingChildrenInBytes int64 `json:"sizeIncludingChildrenInBytes"`

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

// API
// =====================================================================================================================

const fileCtrlContext = "/api/files/control/"
const driveCtrlContext = "/api/files/collections/control/"
const driveCtrlNamespace = "files.collections.control."

func RetrieveDrive(driveId string) (Drive, error) {
	return c.ApiRetrieve[Drive](
		driveCtrlNamespace+"retrieve",
		driveCtrlContext,
		"",
		[]string{"id", driveId},
	)
}

func BrowseDrives(next string) (fnd.PageV2[Drive], error) {
	return c.ApiBrowse[fnd.PageV2[Drive]](
		driveCtrlNamespace+"browse",
		driveCtrlContext,
		"",
		[]string{"next", next},
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
