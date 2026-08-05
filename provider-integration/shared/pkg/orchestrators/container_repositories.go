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

const containerRepositoryControlNamespace = "containerRepositories/control"

type ContainerRepositoriesControlRetrieveRequest struct {
	Id string `json:"id"`
	ContainerRepositoryFlags
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
