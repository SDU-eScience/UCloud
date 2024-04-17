package orchestrators

import (
	"ucloud.dk/pkg/apm"
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
	}

	Collection struct {
		AclModifiable  bool `json:"aclModifiable"`
		UsersCanCreate bool `json:"usersCanCreate"`
		UsersCanDelete bool `json:"usersCanDelete"`
		UsersCanRename bool `json:"usersCanRename"`
	}

	Files struct {
		AclModifiable            bool `json:"aclModifiable"`
		TrashSupport             bool `json:"trashSupport"`
		IsReadOnly               bool `json:"isReadOnly"`
		SearchSupported          bool `json:"searchSupported"`
		StreamingSearchSupported bool `json:"streamingSearchSupported"`
		SharesSupported          bool `json:"sharesSupported"`
	}
}

type ProviderFile struct {
	Id                string
	Status            UFileStatus
	CreatedAt         fnd.Timestamp
	LegacySensitivity string
}

type UFileStatus struct {
	Type FileType     `json:"type"`
	Icon FileIconHint `json:"icon,omitempty"`

	// TODO This technically breaks with UCloud's current API because we are sending 0 instead of null.
	//   I think we should change the core/frontend to consider FSSupport when reading 0 values here.

	SizeInBytes                  int64
	SizeIncludingChildrenInBytes int64

	ModifiedAt fnd.Timestamp
	AccessedAt fnd.Timestamp

	UnixMode  int
	UnixOwner int
	UnixGroup int
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
