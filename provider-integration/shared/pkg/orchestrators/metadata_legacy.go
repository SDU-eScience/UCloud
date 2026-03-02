package orchestrators

import (
	"encoding/json"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan, 24/09/25): Legacy file metadata templates. These are still essential for the file favoriting and
// sensitivity tags, but the feature has been gutted down to the absolute minimum in Core2 to support the features
// that the current (Core1) frontend requires.

type FileMetadataAttached struct {
	Path     string               `json:"path"`
	Metadata FileMetadataDocument `json:"metadata"`
}

type FileMetadataDocument struct {
	Id            string                     `json:"id"`
	Specification FileMetadataDocumentSpec   `json:"specification"`
	CreatedAt     fnd.Timestamp              `json:"createdAt"`
	Status        FileMetadataDocumentStatus `json:"status"`
	CreatedBy     string                     `json:"createdBy"`
	Type          string                     `json:"type"` // Must be set to "metadata"
}

type FileMetadataDocumentSpec struct {
	TemplateId string          `json:"templateId"`
	Version    string          `json:"version"`
	Document   json.RawMessage `json:"document"`
	ChangeLog  string          `json:"changeLog"`
}

type FileMetadataDocumentStatus struct {
	Approval struct {
		Type string `json:"type"` // must be set to "not_required"
	} `json:"approval"`
}

type FileMetadataTemplateNamespace struct {
	Id string `json:"id"`

	Specification struct {
		Name          string `json:"name"`
		NamespaceType string `json:"namespaceType"`
	} `json:"specification"`

	Status struct {
		LatestTitle string `json:"latestTitle"`
	} `json:"status"`
}

// =====================================================================================================================

const fileMetadataDocContext = "files/metadata"

type FileMetadataDocCreateRequest struct {
	FilePath string                   `json:"fileId"`
	Metadata FileMetadataDocumentSpec `json:"metadata"`
}

var FileMetadataDocCreate = rpc.Call[fnd.BulkRequest[FileMetadataDocCreateRequest], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: fileMetadataDocContext,
	Roles:       rpc.RolesEndUser,
	Convention:  rpc.ConventionCreate,
}

var FileMetadataDocDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: fileMetadataDocContext,
	Roles:       rpc.RolesEndUser,
	Convention:  rpc.ConventionDelete,
}

type FileMetadataDocBrowseRequest struct {
	FilterTemplate util.Option[string] `json:"filterTemplate"`
	FilterVersion  util.Option[string] `json:"filterVersion"`
	FilterActive   util.Option[bool]   `json:"filterActive"`
	ItemsPerPage   int                 `json:"itemsPerPage"`
	Next           util.Option[string] `json:"next"`
}

var FileMetadataDocBrowse = rpc.Call[FileMetadataDocBrowseRequest, fnd.PageV2[FileMetadataAttached]]{
	BaseContext: fileMetadataDocContext,
	Roles:       rpc.RolesEndUser,
	Convention:  rpc.ConventionBrowse,
}

// =====================================================================================================================

const fileMetadataNamespaceContext = "files/metadataTemplates"

type FileMetadataNamespaceBrowseRequest struct {
	ItemsPerPage int
	Next         util.Option[string]
	FilterName   util.Option[string]
}

var FileMetadataNamespaceBrowse = rpc.Call[FileMetadataNamespaceBrowseRequest, fnd.PageV2[FileMetadataTemplateNamespace]]{
	BaseContext: fileMetadataNamespaceContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}
