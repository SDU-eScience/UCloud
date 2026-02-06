package orchestrators

import (
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Script struct {
	// NOTE(Louise): Used to be called Workflow
	Id            string              `json:"id"`
	CreatedAt     fnd.Timestamp       `json:"createdAt"`
	Owner         ScriptOwner         `json:"owner"`
	Specification ScriptSpecification `json:"specification"`
	Status        ScriptStatus        `json:"status"`
	Permissions   ScriptPermissions   `json:"permissions"`
}

type ScriptOwner struct {
	CreatedBy string              `json:"createdBy"`
	ProjectId util.Option[string] `json:"projectId"`
}

type ScriptSpecification struct {
	ApplicationName string                              `json:"applicationName"`
	Language        ScriptLanguage                      `json:"language"`
	Init            util.Option[string]                 `json:"init"`
	Job             util.Option[string]                 `json:"job"`
	Inputs          util.Option[[]ApplicationParameter] `json:"inputs"`
	Readme          util.Option[string]                 `json:"readme"`
}

type ScriptLanguage string

const (
	ScriptLanguageJinja2 ScriptLanguage = "JINJA2"
)

type ScriptAclEntry struct {
	Group      string           `json:"group"`
	Permission ScriptPermission `json:"permission"`
}

type ScriptPermission string

const (
	ScriptPermissionRead  ScriptPermission = "READ"
	ScriptPermissionWrite ScriptPermission = "WRITE"
	ScriptPermissionAdmin ScriptPermission = "ADMIN"
)

type ScriptPermissions struct {
	OpenToWorkspace bool               `json:"openToWorkspace"`
	Myself          []ScriptPermission `json:"myself"`
	Others          []ScriptAclEntry   `json:"others"`
}

type ScriptStatus struct {
	Path string `json:"path"`
}

const scriptBaseContext = "hpc/workflows"

type ScriptCreateRequest struct {
	Path           string              `json:"path"`
	AllowOverwrite bool                `json:"allowOverwrite"`
	Specification  ScriptSpecification `json:"specification"`
}

var ScriptCreate = rpc.Call[fnd.BulkRequest[ScriptCreateRequest], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ScriptBrowseRequest struct {
	ItemsPerPage          int                 `json:"itemsPerPage"`
	Next                  util.Option[string] `json:"next"`
	FilterApplicationName string              `json:"filterApplicationName"`
}

var ScriptBrowse = rpc.Call[ScriptBrowseRequest, fnd.PageV2[Script]]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ScriptRenameRequest struct {
	Id             string `json:"id"`
	NewPath        string `json:"newPath"`
	AllowOverwrite bool   `json:"allowOverwrite"`
}

var ScriptRename = rpc.Call[fnd.BulkRequest[ScriptRenameRequest], util.Empty]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "rename",
	Roles:       rpc.RolesEndUser,
}

var ScriptDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

type ScriptUpdateAclRequest struct {
	Id                 string           `json:"id"`
	IsOpenForWorkspace bool             `json:"isOpenForWorkspace"`
	Entries            []ScriptAclEntry `json:"entries"`
}

var ScriptUpdateAcl = rpc.Call[fnd.BulkRequest[ScriptUpdateAclRequest], util.Empty]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "updateAcl",
	Roles:       rpc.RolesEndUser,
}

var ScriptRetrieve = rpc.Call[fnd.FindByStringId, Script]{
	BaseContext: scriptBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}
