package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): Do not use this in the IM yet. This is done in preparation for Core2. Use the one in APM for IM2.

type Project struct {
	Id            string               `json:"id,omitempty"`
	CreatedAt     Timestamp            `json:"createdAt"`
	ModifiedAt    Timestamp            `json:"modifiedAt"`
	Specification ProjectSpecification `json:"specification"`
	Status        ProjectStatus        `json:"status"`
}

type ProjectSpecification struct {
	Parent              string `json:"parent,omitempty"`
	Title               string `json:"title,omitempty"`
	CanConsumeResources bool   `json:"canConsumeResources,omitempty"`
}

type ProjectStatus struct {
	Archived                   bool                `json:"archived,omitempty"`
	IsFavorite                 bool                `json:"isFavorite,omitempty"`
	Members                    []ProjectMember     `json:"members,omitempty"`
	Groups                     []ProjectGroup      `json:"groups,omitempty"`
	Settings                   ProjectSettings     `json:"settings"`
	MyRole                     ProjectRole         `json:"myRole,omitempty"`
	Path                       string              `json:"path,omitempty"`
	PersonalProviderProjectFor util.Option[string] `json:"personalProviderProjectFor"`
}

type ProjectSettings struct {
	SubProjects struct {
		AllowRenaming bool `json:"allowRenaming,omitempty"`
	} `json:"subProjects,omitempty"`
}

type ProjectRole string

const (
	ProjectRoleUser  ProjectRole = "USER"
	ProjectRoleAdmin ProjectRole = "ADMIN"
	ProjectRolePI    ProjectRole = "PI"
)

type ProjectMember struct {
	Username string      `json:"username,omitempty"`
	Role     ProjectRole `json:"role,omitempty"`
	Email    string      `json:"email,omitempty"`
}

type ProjectGroup struct {
	Id            string                    `json:"id,omitempty"`
	CreatedAt     Timestamp                 `json:"createdAt"`
	Specification ProjectGroupSpecification `json:"specification"`
	Status        ProjectGroupStatus        `json:"status"`
}

type ProjectGroupSpecification struct {
	Project string `json:"project"`
	Title   string `json:"title"`
}

type ProjectGroupStatus struct {
	Members []string `json:"members"`
}

type ProjectFlags struct {
	IncludeMembers  bool
	IncludeGroups   bool
	IncludeFavorite bool
	IncludeArchived bool
	IncludeSettings bool
	IncludePath     bool
}

type ProjectRetrieveRequest struct {
	Id string
	ProjectFlags
}

const ProjectContext = "projects/v2"

var ProjectRetrieve = rpc.Call[ProjectRetrieveRequest, Project]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser | rpc.RoleProvider | rpc.RoleService,
}

type ProjectSortBy string

const (
	ProjectSortByFavorite ProjectSortBy = "favorite"
	ProjectSortByTitle    ProjectSortBy = "title"
	ProjectSortByParent   ProjectSortBy = "parent"
)

type ProjectSortDirection string

const (
	ProjectSortAscending  ProjectSortDirection = "ascending"
	ProjectSortDescending ProjectSortDirection = "descending"
)

type ProjectBrowseRequest struct {
	ItemsPerPage  util.Option[int]
	Next          util.Option[string]
	SortBy        util.Option[ProjectSortBy]
	SortDirection util.Option[ProjectSortDirection]
	ProjectFlags
}

var ProjectBrowse = rpc.Call[ProjectBrowseRequest, PageV2[Project]]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser | rpc.RoleProvider,
}

// TODO archive and unarchive purposefully not on the list

var ProjectCreate = rpc.Call[BulkRequest[ProjectSpecification], BulkResponse[FindByStringId]]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser | rpc.RoleProvider | rpc.RoleService,
}

var ProjectToggleFavorite = rpc.Call[BulkRequest[FindByStringId], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "toggleFavorite",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var ProjectUpdateSettings = rpc.Call[ProjectSettings, util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "updateSettings",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type FindByProjectId struct {
	Project string
}

var ProjectRetrieveAllUsersGroup = rpc.Call[BulkRequest[FindByProviderId], BulkResponse[FindByStringId]]{
	BaseContext: ProjectContext,
	Operation:   "retrieveAllUsersGroup",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}

type ProjectRenameRequest struct {
	Id       string `json:"id"`
	NewTitle string `json:"newTitle"`
}

var ProjectRename = rpc.Call[BulkRequest[ProjectRenameRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "renameProject",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

// TODO project verification purposefully not on the list

type ProjectRemoveMemberRequest struct {
	Username string `json:"username"`
}

var ProjectRemoveMember = rpc.Call[BulkRequest[ProjectRemoveMemberRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "deleteMember",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectMemberChangeRoleRequest struct {
	Username string      `json:"username"`
	Role     ProjectRole `json:"role"`
}

var ProjectMemberChangeRole = rpc.Call[BulkRequest[ProjectMemberChangeRoleRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "changeRole",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

const ProjectGroupResource = "groups"

type ProjectRetrieveGroupRequest struct {
	Id             string `json:"id"`
	IncludeMembers bool   `json:"includeMembers"`
}

var ProjectRetrieveGroup = rpc.Call[ProjectRetrieveGroupRequest, ProjectGroup]{
	BaseContext: ProjectContext,
	Operation:   ProjectGroupResource,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser | rpc.RoleProvider,
}

var ProjectCreateGroup = rpc.Call[BulkRequest[ProjectGroupSpecification], BulkResponse[FindByStringId]]{
	BaseContext: ProjectContext,
	Operation:   ProjectGroupResource,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ProjectRenameGroupRequest struct {
	Group    string `json:"group"`
	NewTitle string `json:"newTitle"`
}

var ProjectRenameGroup = rpc.Call[BulkRequest[ProjectRenameGroupRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "renameGroup",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var ProjectDeleteGroup = rpc.Call[BulkRequest[FindByStringId], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "deleteGroup",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

const ProjectGroupMemberResource = "groupMembers"

type ProjectGroupMember struct {
	Username string `json:"username"`
	Group    string `json:"group"`
}

var ProjectCreateGroupMember = rpc.Call[BulkRequest[ProjectGroupMember], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   ProjectGroupMemberResource,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var ProjectDeleteGroupMember = rpc.Call[BulkRequest[ProjectGroupMember], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   ProjectGroupMemberResource,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}
