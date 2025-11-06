package orchestrators

import (
	acc "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Provider struct {
	Resource
	Specification ProviderSpecification `json:"specification"`
	RefreshToken  string                `json:"refreshToken"`
	PublicKey     string                `json:"publicKey"`
	Status        struct {
		ResolvedSupport ResolvedSupport[util.Empty] `json:"resolvedSupport"`
		ResolvedProduct acc.ProductV2               `json:"resolvedProduct"`
	} `json:"status"` // Deprecated
}

type ProviderSpecification struct {
	Id      string               `json:"id"`
	Domain  string               `json:"domain"`
	Https   bool                 `json:"https"`
	Port    int                  `json:"port"`
	Product acc.ProductReference `json:"product"` // Deprecated
}

const providerBaseContext = "providers"

var ProviderCreate = rpc.Call[fnd.BulkRequest[ProviderSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesAdmin,
}

type ProviderSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	ResourceFlags
}

var ProviderSearch = rpc.Call[ProviderSearchRequest, fnd.PageV2[Provider]]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type ProviderBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ResourceFlags
}

var ProviderBrowse = rpc.Call[ProviderBrowseRequest, fnd.PageV2[Provider]]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ProviderRetrieveRequest struct {
	Id string `json:"id"`
	ResourceFlags
}

var ProviderRetrieve = rpc.Call[ProviderRetrieveRequest, Provider]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var ProviderUpdate = rpc.Call[fnd.BulkRequest[ProviderSpecification], util.Empty]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "update",
}

var ProviderRenewToken = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "renewToken",
}

var ProviderRetrieveSpecification = rpc.Call[fnd.FindByStringId, ProviderSpecification]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "specification",
}

var ProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: providerBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}
