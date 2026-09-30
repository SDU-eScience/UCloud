package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const FeaturePublication = "publication"

type Dataset struct {
	Resource
	Specification DatasetSpecification `json:"specification"`
	Status        DatasetStatus        `json:"status"`
	Updates       []DatasetUpdate      `json:"updates,omitempty"`
}

type DatasetSpecification struct {
	SourcePath string `json:"sourcePath"` // UCloud path of the drive/folder backing this dataset, e.g. /<driveId>

	DatasetMetadata
	ResourceSpecification // product references the storage product of the source drive
}

// DatasetMetadata is the user-editable metadata of a dataset. It is sent to the publication target on publish.
type DatasetMetadata struct {
	Title       string   `json:"title"`
	Description string   `json:"description,omitempty"`
	Keywords    []string `json:"keywords,omitempty"`
}

type DatasetStatus struct {
	State     DatasetState `json:"state"`
	TargetId  string       `json:"targetId,omitempty"`  // ID at the publication target, e.g. a Pure dataset UUID
	TargetUrl string       `json:"targetUrl,omitempty"` // Link to the published record at the target

	ResourceStatus[FSSupport] // support is resolved from the source drive's storage product
}

type DatasetState string

const (
	DatasetStateDraft      DatasetState = "DRAFT"
	DatasetStatePublishing DatasetState = "PUBLISHING"
	DatasetStatePublished  DatasetState = "PUBLISHED"
	DatasetStateFailure    DatasetState = "FAILURE"
)

type DatasetUpdate struct {
	Timestamp fnd.Timestamp             `json:"timestamp"`
	State     util.Option[DatasetState] `json:"state,omitempty"`
	Status    util.Option[string]       `json:"status,omitempty"` // human-readable progress/error message
	TargetId  util.Option[string]       `json:"targetId,omitempty"`
	TargetUrl util.Option[string]       `json:"targetUrl,omitempty"`
}

type DatasetFlags struct {
	ResourceFlags
}

// Dataset API (end-user)
// =====================================================================================================================

const datasetNamespace = "datasets"

var DatasetsCreate = rpc.Call[fnd.BulkRequest[DatasetSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var DatasetsDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type DatasetsBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	DatasetFlags
}

var DatasetsBrowse = rpc.Call[DatasetsBrowseRequest, fnd.PageV2[Dataset]]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type DatasetsRetrieveRequest struct {
	Id string
	DatasetFlags
}

var DatasetsRetrieve = rpc.Call[DatasetsRetrieveRequest, Dataset]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var DatasetsPublish = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "publish",
}

type DatasetsUpdateMetadataRequest struct {
	Id          string          `json:"id"`
	NewMetadata DatasetMetadata `json:"newMetadata"`
}

var DatasetsUpdateMetadata = rpc.Call[fnd.BulkRequest[DatasetsUpdateMetadataRequest], util.Empty]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateMetadata",
}

// Dataset Control API
// =====================================================================================================================

const datasetControlNamespace = "datasets/control"

type DatasetsControlRetrieveRequest struct {
	Id string `json:"id"`
	DatasetFlags
}

var DatasetsControlRetrieve = rpc.Call[DatasetsControlRetrieveRequest, Dataset]{
	BaseContext: datasetControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type DatasetsControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	DatasetFlags
}

var DatasetsControlBrowse = rpc.Call[DatasetsControlBrowseRequest, fnd.PageV2[Dataset]]{
	BaseContext: datasetControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var DatasetsControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[DatasetUpdate]], util.Empty]{
	BaseContext: datasetControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

// Dataset Provider API
// =====================================================================================================================

const datasetProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/datasets"

var DatasetsProviderCreate = rpc.Call[fnd.BulkRequest[Dataset], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: datasetProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var DatasetsProviderDelete = rpc.Call[fnd.BulkRequest[Dataset], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var DatasetsProviderPublish = rpc.Call[fnd.BulkRequest[Dataset], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "publish",
}

var DatasetsProviderUpdateMetadata = rpc.Call[fnd.BulkRequest[Dataset], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "updateMetadata",
}
