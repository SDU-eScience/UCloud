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

type ProjectMetadata struct {
	Id         string `json:"id"`
	Title      string `json:"title"`
	PiUsername string `json:"piUsername"`
}

type ProjectSpecification struct {
	Parent              util.Option[string] `json:"parent"`
	Title               string              `json:"title"`
	CanConsumeResources bool                `json:"canConsumeResources"`
}

type ProjectStatus struct {
	Archived                   bool                `json:"archived"`
	IsFavorite                 bool                `json:"isFavorite"`
	Members                    []ProjectMember     `json:"members"`
	Groups                     []ProjectGroup      `json:"groups"`
	Settings                   ProjectSettings     `json:"settings"`
	MyRole                     ProjectRole         `json:"myRole"`
	Path                       string              `json:"path"`
	PersonalProviderProjectFor util.Option[string] `json:"personalProviderProjectFor"`
}

type ProjectSettings struct {
	SubProjects struct {
		AllowRenaming bool `json:"allowRenaming"`
	} `json:"subProjects"`
}

type ProjectToggleSubProjectRenamingSettingRequest struct {
	ProjectId string `json:"projectId"`
}

type ProjectRetrieveSubProjectRenamingResponse struct {
	Allowed bool `json:"allowed"`
}

type ProjectRole string

const (
	ProjectRoleUser  ProjectRole = "USER"
	ProjectRoleAdmin ProjectRole = "ADMIN"
	ProjectRolePI    ProjectRole = "PI"
)

var ProjectRoleOptions = []ProjectRole{
	ProjectRoleUser,
	ProjectRoleAdmin,
	ProjectRolePI,
}

func (p ProjectRole) Power() int {
	switch p {
	case ProjectRolePI:
		return 3
	case ProjectRoleAdmin:
		return 2
	case ProjectRoleUser:
		return 1
	default:
		return 0
	}
}

func (p ProjectRole) Normalize() ProjectRole {
	return util.EnumOrDefault(p, ProjectRoleOptions, ProjectRoleUser)
}

func (p ProjectRole) Satisfies(requirement ProjectRole) bool {
	if p == requirement {
		return true
	}

	power := p.Power()
	requiredPower := requirement.Power()
	if power > 0 && requiredPower > 0 {
		return power >= requiredPower
	} else {
		return false
	}
}

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

type ProjectInviteLink struct {
	Token           string      `json:"token"`
	Expires         Timestamp   `json:"expires"`
	GroupAssignment []string    `json:"groupAssignment"`
	RoleAssignment  ProjectRole `json:"roleAssignment"`
}

type ProjectInviteLinkInfo struct {
	Token    string  `json:"token"`
	IsMember bool    `json:"isMember"`
	Project  Project `json:"project"`
}

type ProjectRetrieveRequest struct {
	Id string
	ProjectFlags
}

const ProjectContextV1 = "projects"
const ProjectContext = "projects/v2"

var ProjectRetrieve = rpc.Call[ProjectRetrieveRequest, Project]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser | rpc.RoleProvider | rpc.RoleService,
}

var ProjectRetrieveMetadata = rpc.Call[FindByStringId, ProjectMetadata]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "metadata",
	Roles:       rpc.RolesEndUser | rpc.RoleProvider | rpc.RoleService,
}

type ProjectSortBy string

const (
	ProjectSortByFavorite ProjectSortBy = "favorite"
	ProjectSortByTitle    ProjectSortBy = "title"
	ProjectSortByParent   ProjectSortBy = "parent"
)

var ProjectSortByOptions = []ProjectSortBy{
	ProjectSortByFavorite,
	ProjectSortByTitle,
	ProjectSortByParent,
}

func (p ProjectSortBy) Normalize() ProjectSortBy {
	return util.EnumOrDefault(p, ProjectSortByOptions, ProjectSortByTitle)
}

type ProjectSortDirection string

const (
	ProjectSortAscending  ProjectSortDirection = "ascending"
	ProjectSortDescending ProjectSortDirection = "descending"
)

var ProjectSortDirectionOptions = []ProjectSortDirection{
	ProjectSortAscending,
	ProjectSortDescending,
}

func (p ProjectSortDirection) Normalize() ProjectSortDirection {
	return util.EnumOrDefault(p, ProjectSortDirectionOptions, ProjectSortAscending)
}

type ProjectBrowseRequest struct {
	ItemsPerPage  int
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
	Roles:       rpc.RolesPrivileged,
}

// TODO this is a new call
type ProjectInternalCreateRequest struct {
	Title        string              `json:"title"`
	BackendId    string              `json:"backendId"`
	PiUsername   string              `json:"piUsername"`
	SubAllocator util.Option[bool]   `json:"subAllocator"`
	Parent       util.Option[string] `json:"parent"`
}

// TODO this is a new call
var ProjectInternalCreate = rpc.Call[ProjectInternalCreateRequest, FindByStringId]{
	BaseContext: ProjectContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "createInternal",
	Roles:       rpc.RoleService,
}

var ProjectToggleFavorite = rpc.Call[BulkRequest[FindByStringId], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "toggleFavorite",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var ProjectToggleSubProjectRenamingSetting = rpc.Call[ProjectToggleSubProjectRenamingSettingRequest, util.Empty]{
	BaseContext: ProjectContextV1,
	Operation:   "toggleRenaming",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var ProjectRetrieveSubProjectRenamingSetting = rpc.Call[util.Empty, ProjectRetrieveSubProjectRenamingResponse]{
	BaseContext: ProjectContextV1,
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
	Operation:   "renameable-sub",
}

type FindByProjectId struct {
	Project string
}

var ProjectRetrieveAllUsersGroup = rpc.Call[BulkRequest[FindByProjectId], BulkResponse[FindByStringId]]{
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
	Operation:   "deleteGroupMember",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectRenamableRequest struct {
	ProjectId string `json:"projectId"`
}

type ProjectRenamableResponse struct {
	Allowed bool `json:"allowed"`
}

var ProjectRenamable = rpc.Call[ProjectRenamableRequest, ProjectRenamableResponse]{
	BaseContext: ProjectContextV1,
	Operation:   "renameable",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
}

const ProjectInviteLinkResource = "link"

var ProjectCreateInviteLink = rpc.Call[util.Empty, ProjectInviteLink]{
	BaseContext: ProjectContext,
	Operation:   ProjectInviteLinkResource,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ProjectBrowseInviteLinksRequest struct {
	ItemsPerPage int
	Next         util.Option[string]
}

var ProjectBrowseInviteLinks = rpc.Call[ProjectBrowseInviteLinksRequest, PageV2[ProjectInviteLink]]{
	BaseContext: ProjectContext,
	Operation:   ProjectInviteLinkResource,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type FindByInviteLink struct {
	Token string `json:"token"`
}

var ProjectRetrieveInviteLink = rpc.Call[FindByInviteLink, ProjectInviteLinkInfo]{
	BaseContext: ProjectContext,
	Operation:   ProjectInviteLinkResource,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var ProjectDeleteInviteLink = rpc.Call[FindByInviteLink, util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "deleteInviteLink",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectUpdateInviteLinkRequest struct {
	Token  string      `json:"token"`
	Role   ProjectRole `json:"role"`
	Groups []string    `json:"groups"`
}

var ProjectUpdateInviteLink = rpc.Call[ProjectUpdateInviteLinkRequest, util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "updateInviteLink",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectAcceptInviteLinkResponse struct {
	Project string `json:"project"`
}

var ProjectAcceptInviteLink = rpc.Call[FindByInviteLink, ProjectAcceptInviteLinkResponse]{
	BaseContext: ProjectContext,
	Operation:   "acceptInviteLink",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

const ProjectInviteResource = "invites"

type ProjectInvite struct {
	CreatedAt    Timestamp `json:"createdAt"`
	InvitedBy    string    `json:"invitedBy"`
	InvitedTo    string    `json:"invitedTo"`
	Recipient    string    `json:"recipient"`
	ProjectTitle string    `json:"projectTitle"`
}

type ProjectBrowseInvitesRequest struct {
	FilterType string `json:"filterType"`
}

var ProjectBrowseInvites = rpc.Call[ProjectBrowseInvitesRequest, PageV2[ProjectInvite]]{
	BaseContext: ProjectContext,
	Operation:   ProjectInviteResource,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ProjectCreateInviteRequest struct {
	Recipient string `json:"recipient"`
}

var ProjectCreateInvite = rpc.Call[BulkRequest[ProjectCreateInviteRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   ProjectInviteResource,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type ProjectAcceptInviteRequest struct {
	Project string `json:"project"`
}

var ProjectAcceptInvite = rpc.Call[BulkRequest[ProjectAcceptInviteRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "acceptInvite",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectDeleteInviteRequest struct {
	Project  string `json:"project"`
	Username string `json:"username"`
}

var ProjectDeleteInvite = rpc.Call[BulkRequest[ProjectDeleteInviteRequest], util.Empty]{
	BaseContext: ProjectContext,
	Operation:   "deleteInvite",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type ProjectRetrieveInformationResponse struct {
	Projects map[string]ProjectInformation `json:"projects"`
}

type ProjectInformation struct {
	Id         string `json:"id"`
	PiUsername string `json:"piUsername"`
	Title      string `json:"title"`
}

// TODO This is a new call returning information about projects to all authenticated users. You do not need to be a
//  member of the project to look up the information. You just need the ID.

var ProjectRetrieveInformation = rpc.Call[BulkRequest[FindByStringId], ProjectRetrieveInformationResponse]{
	BaseContext: ProjectContext,
	Operation:   "retrieveInformation",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}
