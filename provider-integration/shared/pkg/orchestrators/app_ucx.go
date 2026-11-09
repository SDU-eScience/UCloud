package orchestrators

import (
	"encoding/json"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const appUcxContext = "hpc/apps/ucx"

type UcxClientKind string

const (
	UcxClientBrowser  UcxClientKind = "browser"
	UcxClientHeadless UcxClientKind = "headless"
)

const (
	UcxUpdatesUi       = "ui"
	UcxUpdatesMessages = "messages"
	UcxUpdatesNone     = "none"
)

// AppUcxConnectRequest is passed as part of the SysHello message.
type AppUcxConnectRequest struct {
	Name       string              `json:"name"`
	Version    string              `json:"version"`
	Provider   util.Option[string] `json:"provider"`
	Handoff    json.RawMessage     `json:"handoff,omitempty"`
	ClientKind UcxClientKind       `json:"clientKind,omitempty"`
	Updates    string              `json:"updates,omitempty"`
}

var AppUcxConnect = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContext,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connect",
	Roles:       rpc.RolesPublic,
}

// AppUcxConnectJobRequest is passed as part of the SysHello message.
type AppUcxConnectJobRequest struct {
	JobId      string        `json:"jobId"`
	ClientKind UcxClientKind `json:"clientKind,omitempty"`
	Updates    string        `json:"updates,omitempty"`
}

var AppUcxConnectJob = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContext,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connectJob",
	Roles:       rpc.RolesPublic,
}

const appUcxContextProvider = "ucloud/" + rpc.ProviderPlaceholder + "/hpc/apps/ucx"

// AppUcxConnectProviderRequest is passed as part of the SysHello message.
type AppUcxConnectProviderRequest struct {
	Application Application     `json:"application"`
	Owner       ResourceOwner   `json:"owner"`
	Provider    string          `json:"provider"`
	Handoff     json.RawMessage `json:"handoff,omitempty"`
	ClientKind  UcxClientKind   `json:"clientKind,omitempty"`
	Updates     string          `json:"updates,omitempty"`
}

var AppUcxConnectProvider = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContextProvider,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connect",
	Roles:       rpc.RolesPublic,
}

type AppUcxConnectJobProviderRequest struct {
	Job              Job                 `json:"job"`
	Port             int                 `json:"port"`
	StackStateFolder util.Option[string] `json:"stackStateFolder,omitempty"`
	ClientKind       UcxClientKind       `json:"clientKind,omitempty"`
	Updates          string              `json:"updates,omitempty"`
}

var AppUcxConnectJobProvider = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appUcxContextProvider,
	Convention:  rpc.ConventionWebSocket,
	Operation:   "connectJob",
	Roles:       rpc.RolesPublic,
}
