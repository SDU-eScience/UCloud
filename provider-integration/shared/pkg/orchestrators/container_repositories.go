package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ContainerRepository struct {
	Resource
	Specification ContainerRepositorySpecification `json:"specification"`
	Status        ContainerRepositoryStatus        `json:"status"`
}

type ContainerRepositorySpecification struct {
	Name string `json:"name"`
	ResourceSpecification
}

type ContainerRepositoryStatus struct {
	ResourceStatus[FSSupport]
}

type ContainerRepositoryFlags struct {
	ResourceFlags
}

type ContainerRepositoryImageLayer struct {
	Digest      string   `json:"digest"`
	MediaType   string   `json:"mediaType"`
	SizeInBytes int64    `json:"sizeInBytes"`
	Platforms   []string `json:"platforms"`
}

type ContainerRepositoryImage struct {
	Kind        string                          `json:"kind"`
	Name        string                          `json:"name"`
	Repository  string                          `json:"repository"`
	Tag         string                          `json:"tag"`
	TagCount    int                             `json:"tagCount"`
	Digest      string                          `json:"digest"`
	MediaType   string                          `json:"mediaType"`
	SizeInBytes int64                           `json:"sizeInBytes"`
	Layers      []ContainerRepositoryImageLayer `json:"layers"`
}

const containerRepositoryNamespace = "containerRepositories"

var ContainerRepositoriesCreate = rpc.Call[fnd.BulkRequest[ContainerRepositorySpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var ContainerRepositoriesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type ContainerRepositoriesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ContainerRepositoryFlags
}

var ContainerRepositoriesBrowse = rpc.Call[ContainerRepositoriesBrowseRequest, fnd.PageV2[ContainerRepository]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ContainerRepositoriesRetrieveRequest struct {
	Id string `json:"id"`
	ContainerRepositoryFlags
}

var ContainerRepositoriesRetrieve = rpc.Call[ContainerRepositoriesRetrieveRequest, ContainerRepository]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var ContainerRepositoriesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type ContainerRepositoriesUpdateLabelsRequest struct {
	Id     string            `json:"id"`
	Labels map[string]string `json:"labels"`
}

var ContainerRepositoriesUpdateLabels = rpc.Call[fnd.BulkRequest[ContainerRepositoriesUpdateLabelsRequest], util.Empty]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateLabels",
}

var ContainerRepositoriesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

type ContainerRepositoriesBrowseImagesRequest struct {
	RepositoryId string              `json:"repositoryId"`
	Repository   util.Option[string] `json:"repository"`
	Tag          util.Option[string] `json:"tag"`
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var ContainerRepositoriesBrowseImages = rpc.Call[ContainerRepositoriesBrowseImagesRequest, fnd.PageV2[ContainerRepositoryImage]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
	Operation:   "images",
}

type ContainerRepositoriesDeleteImageRequest struct {
	RepositoryId string `json:"repositoryId"`
	Repository   string `json:"repository"`
	Tag          string `json:"tag"`
}

var ContainerRepositoriesDeleteImage = rpc.Call[fnd.BulkRequest[ContainerRepositoriesDeleteImageRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRepositoryNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "deleteImage",
}

const containerRepositoryControlNamespace = "containerRepositories/control"

type ContainerRepositoriesControlRetrieveRequest struct {
	Id string `json:"id"`
	ContainerRepositoryFlags
}

type ContainerRepositoriesControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ContainerRepositoryFlags
}

var ContainerRepositoriesControlBrowse = rpc.Call[ContainerRepositoriesControlBrowseRequest, fnd.PageV2[ContainerRepository]]{
	BaseContext: containerRepositoryControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var ContainerRepositoriesControlRetrieve = rpc.Call[ContainerRepositoriesControlRetrieveRequest, ContainerRepository]{
	BaseContext: containerRepositoryControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

var ContainerRepositoriesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[ContainerRepositorySpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRepositoryControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var ContainerRepositoriesControlUpdateLabels = rpc.Call[fnd.BulkRequest[ContainerRepositoriesUpdateLabelsRequest], util.Empty]{
	BaseContext: containerRepositoryControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "updateLabels",
}

const containerRepositoryProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/containerRepositories"

var ContainerRepositoriesProviderCreate = rpc.Call[fnd.BulkRequest[ContainerRepository], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRepositoryProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var ContainerRepositoriesProviderDelete = rpc.Call[fnd.BulkRequest[ContainerRepository], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRepositoryProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var ContainerRepositoriesProviderOnUpdatedLabels = rpc.Call[fnd.BulkRequest[ContainerRepository], util.Empty]{
	BaseContext: containerRepositoryProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "onUpdatedLabels",
}

type ContainerRepositoriesProviderBrowseImagesRequest struct {
	ResolvedRepository ContainerRepository `json:"resolvedRepository"`
	Repository         util.Option[string] `json:"repository"`
	Tag                util.Option[string] `json:"tag"`
	ItemsPerPage       int                 `json:"itemsPerPage"`
	Next               util.Option[string] `json:"next"`
}

var ContainerRepositoriesProviderBrowseImages = rpc.Call[ContainerRepositoriesProviderBrowseImagesRequest, fnd.PageV2[ContainerRepositoryImage]]{
	BaseContext: containerRepositoryProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "browseImages",
}

type ContainerRepositoriesProviderDeleteImageRequest struct {
	ResolvedRepository ContainerRepository `json:"resolvedRepository"`
	Repository         string              `json:"repository"`
	Tag                string              `json:"tag"`
}

var ContainerRepositoriesProviderDeleteImage = rpc.Call[fnd.BulkRequest[ContainerRepositoriesProviderDeleteImageRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRepositoryProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "deleteImage",
}
