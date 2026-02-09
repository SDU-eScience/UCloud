package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const apiTokenContext = "tokens"

// Core models
// =====================================================================================================================

type ApiToken struct {
	Resource
	Specification ApiTokenSpecification `json:"specification"`
	Status        ApiTokenStatus        `json:"status"`
}

type ApiTokenStatus struct {
	Token  util.Option[string] `json:"token"`  // visible only during creation
	Server string              `json:"server"` // URL. May include path if needed.
}

type ApiTokenSpecification struct {
	Title                string               `json:"title"`
	Description          string               `json:"description"`
	Provider             util.Option[string]  `json:"provider"` // null implies UCloud itself
	RequestedPermissions []ApiTokenPermission `json:"requestedPermissions"`
	ExpiresAt            fnd.Timestamp        `json:"expiresAt"`
}

type ApiTokenPermission struct {
	Name   string `json:"name"`
	Action string `json:"action"`
}

type ApiTokenOptions struct {
	AvailablePermissions []ApiTokenPermissionSpecification `json:"availablePermissions"`
}

type ApiTokenPermissionSpecification struct {
	Name        string            `json:"name"`
	Title       string            `json:"title"`
	Description string            `json:"description"`
	Actions     map[string]string `json:"actions"` // name of action to human-readable format
}

type ApiTokenRetrieveOptionsResponse struct {
	ByProvider map[string]ApiTokenOptions `json:"byProvider"`
}

// API
// =====================================================================================================================

var ApiTokenCreate = rpc.Call[ApiTokenSpecification, ApiToken]{
	BaseContext: apiTokenContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ApiTokenBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
}

var ApiTokenBrowse = rpc.Call[ApiTokenBrowseRequest, fnd.PageV2[ApiToken]]{
	BaseContext: apiTokenContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

var ApiTokenRevoke = rpc.Call[fnd.FindByStringId, util.Empty]{
	BaseContext: apiTokenContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "revoke",
	Roles:       rpc.RolesEndUser,
}

var ApiTokenRetrieveOptions = rpc.Call[util.Empty, ApiTokenRetrieveOptionsResponse]{
	BaseContext: apiTokenContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "options",
	Roles:       rpc.RolesEndUser,
}

// NOTE(Dan): There is no ACL endpoint because this API doesn't actually save the token. It saves, at most (in the
// case of UCloud/Core tokens), a hash of the token. As a result, there would be nothing to give access to.
