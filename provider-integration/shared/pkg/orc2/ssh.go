package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const sshBaseContext = "ssh"

var SshCreate = rpc.Call[fnd.BulkRequest[SshKeySpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: sshBaseContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var SshRetrieve = rpc.Call[fnd.FindByStringId, SshKey]{
	BaseContext: sshBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type SshKeysBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var SshBrowse = rpc.Call[SshKeysBrowseRequest, fnd.PageV2[SshKey]]{
	BaseContext: sshBaseContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var SshDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: sshBaseContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

const sshControlBaseContext = "ssh/control"

type SshKeysControlBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var SshControlBrowse = rpc.Call[SshKeysControlBrowseRequest, fnd.PageV2[SshKey]]{
	BaseContext: sshControlBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "browse",
	Roles:       rpc.RolesProvider,
}

const sshProviderBaseContext = "ucloud/" + rpc.ProviderPlaceholder + "/ssh"

type SshProviderKeyUploadedRequest struct {
	Username string   `json:"username"`
	AllKeys  []SshKey `json:"allKeys"`
}

var SshProviderKeyUploaded = rpc.Call[SshProviderKeyUploadedRequest, util.Empty]{
	BaseContext: sshProviderBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "keyUploaded",
	Roles:       rpc.RolesService,
}
