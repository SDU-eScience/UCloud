package orchestrators

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const appUcxContext = "hpc/apps/ucx"

// AppUcxConnectRequest is passed as part of the SysHello message.
type AppUcxConnectRequest struct {
	Name     string              `json:"name"`
	Version  string              `json:"version"`
	Provider util.Option[string] `json:"provider"`
}

var AppUcxConnect = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContext,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connect",
	Roles:       rpc.RolesPublic,
}

const appUcxContextProvider = "ucloud/" + rpc.ProviderPlaceholder + "/hpc/apps/ucx"

// AppUcxConnectProviderRequest is passed as part of the SysHello message.
type AppUcxConnectProviderRequest struct {
	Application Application `json:"application"`
}

var AppUcxConnectProvider = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContext,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connect",
	Roles:       rpc.RolesPublic,
}
