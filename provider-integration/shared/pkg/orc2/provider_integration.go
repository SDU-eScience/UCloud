package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const ProviderIntegrationContext = "providers/integration"

type ProviderIntegrationConnectRequest struct {
	Provider string `json:"provider"`
}

type ProviderIntegrationConnectResponse struct {
	RedirectTo string `json:"redirectTo"`
}

var ProviderIntegrationConnect = rpc.Call[ProviderIntegrationConnectRequest, ProviderIntegrationConnectResponse]{
	BaseContext: ProviderIntegrationContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "connect",
}

type ProviderIntegrationClearConnectionRequest struct {
	Provider string `json:"provider"`
}

var ProviderIntegrationClearConnection = rpc.Call[ProviderIntegrationClearConnectionRequest, util.Empty]{
	BaseContext: ProviderIntegrationContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesAuthenticated,
	Operation:   "clearConnection",
}

type ProviderIntegrationBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

type ProviderIntegrationBrowseResponse struct {
	Provider               string `json:"provider"`
	Connected              bool   `json:"connected"`
	ProviderTitle          string `json:"providerTitle"`
	RequiresMessageSigning bool   `json:"requiresMessageSigning"` // deprecated - always false
	UnmanagedConnection    bool   `json:"unmanagedConnection"`    // deprecated - always false
}

var ProviderIntegrationBrowse = rpc.Call[ProviderIntegrationBrowseRequest, fnd.PageV2[ProviderIntegrationBrowseResponse]]{
	BaseContext: ProviderIntegrationContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

// =====================================================================================================================

const ProviderIntegrationCtrlContext = "providers/integration/control"

type ProviderIntegrationCtrlFindByUser struct {
	Username string `json:"username"`
}

var ProviderIntegrationCtrlApproveConnection = rpc.Call[ProviderIntegrationCtrlFindByUser, util.Empty]{
	BaseContext: ProviderIntegrationCtrlContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RoleProvider,
	Operation:   "approveConnection",
}

var ProviderIntegrationCtrlClearConnection = rpc.Call[ProviderIntegrationCtrlFindByUser, util.Empty]{
	BaseContext: ProviderIntegrationCtrlContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RoleProvider,
	Operation:   "clearConnection",
}

// =====================================================================================================================

const ProviderIntegrationProviderContext = "ucloud/" + rpc.ProviderPlaceholder + "/integration"

type ProviderIntegrationPFindByUser struct {
	Username string `json:"username"`
}

type ProviderIntegrationManifest struct {
	Enabled       bool `json:"enabled"`
	ExpireAfterMs int  `json:"expireAfterMs"`
	// requiresMessageSigning and unmanagedConnects are deprecated
}

var ProviderIntegrationPRetrieveManifest = rpc.Call[util.Empty, ProviderIntegrationManifest]{
	BaseContext: ProviderIntegrationProviderContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesService,
	Operation:   "manifest",
}

var ProviderIntegrationPInit = rpc.Call[ProviderIntegrationPFindByUser, util.Empty]{
	BaseContext: ProviderIntegrationProviderContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "init",
}

type ProviderIntegrationPConnectResponse struct {
	RedirectTo string `json:"redirectTo"`
}

var ProviderIntegrationPConnect = rpc.Call[ProviderIntegrationPFindByUser, ProviderIntegrationPConnectResponse]{
	BaseContext: ProviderIntegrationProviderContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "connect",
}

var ProviderIntegrationPDisconnect = rpc.Call[ProviderIntegrationPFindByUser, util.Empty]{
	BaseContext: ProviderIntegrationProviderContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "unlinked",
}
