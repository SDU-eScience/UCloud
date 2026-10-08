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

// DatasetEntry is a file or folder which has been selected for inclusion in a dataset.
type DatasetEntry struct {
	Id   string `json:"id"`   // UCloud id of the selected file or folder
	Path string `json:"path"` // UCloud path of the selected file or folder, e.g. /123/folder/data.csv
}

type DatasetSpecification struct {
	// Entries is the initial selection of files and folders which are copied (snapshotted) into this dataset.
	// The selection can be modified later with the updateEntries operation until the dataset is published.
	Entries []DatasetEntry `json:"entries"`

	DatasetMetadata
	ResourceSpecification // product references the storage product of the drive which holds the datasets of the project
}

// DatasetMetadata is the user-editable metadata of a dataset. It is sent to the publication target on publish.
type DatasetMetadata struct {
	Title       string   `json:"title"`
	Description string   `json:"description,omitempty"`
	Keywords    []string `json:"keywords,omitempty"`
}

type DatasetStatus struct {
	State DatasetState `json:"state"`

	// Path is the UCloud path of the folder which backs this dataset, e.g. /<datasetsDriveId>/<datasetId>.
	// The folder is created inside the drive which holds all datasets of the owning project and every selected
	// entry is copied into it. The folder can be browsed with the regular files API.
	Path string `json:"path,omitempty"`

	TargetId  string `json:"targetId,omitempty"`  // ID at the publication target, e.g. a Pure dataset UUID
	TargetUrl string `json:"targetUrl,omitempty"` // Link to the published record at the target

	ResourceStatus[FSSupport] // support is resolved from the storage product of the datasets drive
}

type DatasetState string

const (
	// DatasetStateDraft: the dataset is being populated. Entries can still be added and removed and copies
	// of recently added entries may be in-flight.
	DatasetStateDraft DatasetState = "DRAFT"
	// DatasetStateReady: every selected entry has been copied into the dataset, it can now be published.
	DatasetStateReady DatasetState = "READY"
	// DatasetStatePublishing: the dataset is being transferred to the publication target.
	DatasetStatePublishing DatasetState = "PUBLISHING"
	// DatasetStatePublished: the dataset is immutable, entries can no longer be added or removed.
	DatasetStatePublished DatasetState = "PUBLISHED"
	DatasetStateFailure   DatasetState = "FAILURE"
)

type DatasetUpdate struct {
	Timestamp fnd.Timestamp             `json:"timestamp"`
	State     util.Option[DatasetState] `json:"state,omitempty"`
	Status    util.Option[string]       `json:"status,omitempty"` // human-readable progress/error message
	Path      util.Option[string]       `json:"path,omitempty"`   // UCloud path of the folder backing the dataset
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

// DatasetsPublish publishes a dataset to its publication target. Publishing is only allowed once all selected
// files and folders have been copied into the dataset, that is while the dataset is in the READY state.
var DatasetsPublish = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "publish",
}

type DatasetsUpdateEntriesRequest struct {
	Id             string         `json:"id"`
	AddedEntries   []DatasetEntry `json:"addedEntries,omitempty"`   // files/folders which are copied into the dataset
	DeletedEntries []DatasetEntry `json:"deletedEntries,omitempty"` // files/folders which are removed from the dataset
}

// DatasetsUpdateEntries changes the selection of files and folders of an existing dataset. This is only
// allowed while the dataset has not been published. Adding an entry which is already part of the dataset
// refreshes its copy. Adding new entries moves the dataset back to DRAFT until they have been copied.
var DatasetsUpdateEntries = rpc.Call[fnd.BulkRequest[DatasetsUpdateEntriesRequest], util.Empty]{
	BaseContext: datasetNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateEntries",
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

// DatasetsProviderUpdateEntries is invoked by the orchestrator whenever the entry selection of a dataset has
// changed. The provider must apply the changes to the folder backing the dataset and report progress and the
// resulting state (DRAFT while copying, READY when complete) via the control API.
var DatasetsProviderUpdateEntries = rpc.Call[fnd.BulkRequest[Dataset], fnd.BulkResponse[util.Empty]]{
	BaseContext: datasetProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "updateEntries",
}
