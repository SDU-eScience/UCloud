package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ContainerRegistry struct {
	Resource
	Specification ContainerRegistrySpecification `json:"specification"`
	Status        ContainerRegistryStatus        `json:"status"`
}

type ContainerRegistrySpecification struct {
	Name string `json:"name"`
	ResourceSpecification
}

type ContainerRegistryStatus struct {
	ResourceStatus[FSSupport]
}

type ContainerRegistryFlags struct {
	ResourceFlags
}

const containerRegistryNamespace = "containerRegistries"

var ContainerRegistriesCreate = rpc.Call[fnd.BulkRequest[ContainerRegistrySpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var ContainerRegistriesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type ContainerRegistriesRetrieveRequest struct {
	Id string `json:"id"`
	ContainerRegistryFlags
}

var ContainerRegistriesRetrieve = rpc.Call[ContainerRegistriesRetrieveRequest, ContainerRegistry]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var ContainerRegistriesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type ContainerRegistriesUpdateLabelsRequest struct {
	Id     string            `json:"id"`
	Labels map[string]string `json:"labels"`
}

var ContainerRegistriesUpdateLabels = rpc.Call[fnd.BulkRequest[ContainerRegistriesUpdateLabelsRequest], util.Empty]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateLabels",
}

var ContainerRegistriesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[FSSupport]]{
	BaseContext: containerRegistryNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

const containerRegistryControlNamespace = "containerRegistries/control"

type ContainerRegistriesControlRetrieveRequest struct {
	Id string `json:"id"`
	ContainerRegistryFlags
}

var ContainerRegistriesControlRetrieve = rpc.Call[ContainerRegistriesControlRetrieveRequest, ContainerRegistry]{
	BaseContext: containerRegistryControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

var ContainerRegistriesControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[ContainerRegistrySpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRegistryControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "register",
}

var ContainerRegistriesControlUpdateLabels = rpc.Call[fnd.BulkRequest[ContainerRegistriesUpdateLabelsRequest], util.Empty]{
	BaseContext: containerRegistryControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "updateLabels",
}

const containerRegistryProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/containerRegistries"

var ContainerRegistriesProviderCreate = rpc.Call[fnd.BulkRequest[ContainerRegistry], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: containerRegistryProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var ContainerRegistriesProviderDelete = rpc.Call[fnd.BulkRequest[ContainerRegistry], fnd.BulkResponse[util.Empty]]{
	BaseContext: containerRegistryProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var ContainerRegistriesProviderOnUpdatedLabels = rpc.Call[fnd.BulkRequest[ContainerRegistry], util.Empty]{
	BaseContext: containerRegistryProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "onUpdatedLabels",
}
