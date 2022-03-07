package dk.sdu.cloud.project.api.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import kotlinx.serialization.Serializable

typealias ProjectRole = dk.sdu.cloud.project.api.ProjectRole

@Serializable
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class Project(
    val id: String,
    val createdAt: Long,
    val specification: Specification,
    val status: Status,
) {
    @Serializable
    data class Status(
        @UCloudApiDoc("""
            A flag which indicates if the project is currently archived.

            Currently archiving does not mean a lot in UCloud. This is subject to change in the future. For the most
            part, archived projects simply do not appear when using a `browse`, unless `includeArchived = true`.
        """)
        val archived: Boolean,

        @UCloudApiDoc("""
            A flag which indicates if the current user has marked this as one of their favorite projects.
        """)
        val isFavorite: Boolean? = null,

        @UCloudApiDoc("""
            A list of project members, conditionally included if `includeMembers = true`.

            NOTE: This list will contain all members of a project, always. There are currently no plans for a
            pagination API. This might change in the future if it becomes plausible that projects have many thousands
            of members.
        """)
        val members: List<ProjectMember>? = null,

        @UCloudApiDoc("""
            A list of groups, conditionally included if `includeGroups = true`.

            NOTE: This list will contain all groups of a project, always. There are currently no plans for a pagination
            API. This might change in the future if it becomes plausible that projects have many thousands of groups.
        """)
        val groups: List<Group>? = null,

        @UCloudApiDoc("""
            The settings of this project, conditionally included if `includeSettings = true`.
        """)
        val settings: Settings? = null,

        @UCloudApiDoc("""
            The role of the current user, this value is always included.

            This is typically not-null, but it can be null if the request was made by an actor which has access to the
            project without being a member. Common examples include: `Actor.System` and a relevant provider.
        """)
        val myRole: ProjectRole? = null,
    )

    @Serializable
    data class Specification(
        val parent: String?,
        val title: String,
    ) {
        init {
            checkSingleLine(::title, title, maximumSize = 128)
        }
    }

    @Serializable
    data class Settings(
        val subprojects: SubProjects? = null,
    ) {
        @Serializable
        data class SubProjects(
            val allowRenaming: Boolean = false
        )
    }
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class Group(
    val id: String,
    val createdAt: Long,
    val specification: Specification,
    val status: Status
) {
    @Serializable
    data class Specification(
        val project: String,
        val title: String,
    ) {
        init {
            checkSingleLine(::title, title, maximumSize = 128)
        }
    }

    @Serializable
    data class Status(
        val members: List<String>? = null
    )
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectInvite(
    val createdAt: Long,
    val invitedBy: String,
    val invitedTo: String,
    val recipient: String,
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Projects : CallDescriptionContainer("projects.v2") {
    const val baseContext = "/api/projects/v2"
    const val inviteResource = "invites"
    const val groupResource = "groups"
    const val groupMemberResource = "groupMembers"

    // Project management
    val retrieve = call<ProjectsRetrieveRequest, Project, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val browse = call<ProjectsBrowseRequest, PageV2<Project>, CommonErrorMessage>("browse") {
        httpBrowse(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val create = call<ProjectsCreateRequest, BulkResponse<FindByStringId>, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val archive = call<ProjectsArchiveRequest, Unit, CommonErrorMessage>("archive") {
        httpUpdate(baseContext, "archive")
    }

    val toggleFavorite = call<ProjectsToggleFavoriteRequest, Unit, CommonErrorMessage>("toggleFavorite") {
        httpUpdate(baseContext, "toggleFavorite")
    }

    val updateSettings = call<ProjectsUpdateSettingsRequest, Unit, CommonErrorMessage>("updateSettings") {
        httpUpdate(baseContext, "updateSettings")
    }

    val verifyMembership = call<ProjectsVerifyMembershipRequest, Unit, CommonErrorMessage>("verifyMembership") {
        httpUpdate(baseContext, "verifyMembership")
    }

    // Invitations
    val browseInvites = call<ProjectsBrowseInvitesRequest, PageV2<ProjectInvite>, CommonErrorMessage>("browseInvites") {
        httpBrowse(baseContext, inviteResource)
    }

    val createInvite = call<ProjectsCreateInviteRequest, Unit, CommonErrorMessage>("createInvite") {
        httpCreate(baseContext, inviteResource)
    }

    val acceptInvite = call<ProjectsAcceptInviteRequest, Unit, CommonErrorMessage>("acceptInvite") {
        httpUpdate(baseContext, "acceptInvite")
    }

    val deleteInvite = call<ProjectsDeleteInviteRequest, Unit, CommonErrorMessage>("deleteInvite") {
        httpUpdate(baseContext, "deleteInvite")
    }

    // Member management
    val deleteMember = call<ProjectsDeleteMemberRequest, Unit, CommonErrorMessage>("deleteMember") {
        httpUpdate(baseContext, "deleteMember")
    }

    val changeRole = call<ProjectsChangeRoleRequest, Unit, CommonErrorMessage>("changeRole") {
        httpUpdate(baseContext, "changeRole")
    }

    // Group management
    val retrieveGroup = call<ProjectsRetrieveGroupRequest, Group, CommonErrorMessage>("retrieveGroup") {
        httpRetrieve(baseContext, groupResource, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val createGroup = call<ProjectsCreateGroupRequest, BulkResponse<FindByStringId>, CommonErrorMessage>("createGroup") {
        httpCreate(baseContext, groupResource)
    }

    // Group member management
    val createGroupMember = call<ProjectsCreateGroupMemberRequest, Unit, CommonErrorMessage>("createGroupMember") {
        httpCreate(baseContext, groupMemberResource)
    }

    val deleteGroupMember = call<ProjectsDeleteGroupMemberRequest, Unit, CommonErrorMessage>("deleteGroupMember") {
        httpUpdate(baseContext, "deleteGroupMember")
    }
}

interface ProjectFlags {
    val includeMembers: Boolean?
    val includeGroups: Boolean?
    val includeFavorite: Boolean?
    val includeArchived: Boolean?
    val includeSettings: Boolean?
}

@Serializable
data class ProjectsRetrieveRequest(
    val id: String,
    override val includeMembers: Boolean? = null,
    override val includeGroups: Boolean? = null,
    override val includeFavorite: Boolean? = null,
    override val includeArchived: Boolean? = null,
    override val includeSettings: Boolean? = null,
) : ProjectFlags

@Serializable
enum class ProjectsSortBy {
    favorite,
    title,
    parent,
}

@Serializable
data class ProjectsBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val includeMembers: Boolean? = null,
    override val includeGroups: Boolean? = null,
    override val includeFavorite: Boolean? = null,
    override val includeArchived: Boolean? = null,
    override val includeSettings: Boolean? = null,

    val sortBy: ProjectsSortBy = ProjectsSortBy.title,
    val sortDirection: SortDirection = SortDirection.ascending
) : ProjectFlags, WithPaginationRequestV2

typealias ProjectsCreateRequest = BulkRequest<Project.Specification>
typealias ProjectsArchiveRequest = BulkRequest<FindByStringId>
typealias ProjectsToggleFavoriteRequest = BulkRequest<FindByStringId>
typealias ProjectsUpdateSettingsRequest = Project.Settings
typealias ProjectsVerifyMembershipRequest = BulkRequest<FindByStringId>

@Serializable
enum class ProjectInviteType {
    INGOING,
    OUTGOING,
}

interface ProjectInviteFlags {
    val filterType: ProjectInviteType?
}

@Serializable
data class ProjectsBrowseInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterType: ProjectInviteType? = null,
) : ProjectInviteFlags, WithPaginationRequestV2

@Serializable
data class ProjectsCreateInviteRequestItem(val recipient: String)
typealias ProjectsCreateInviteRequest = BulkRequest<ProjectsCreateInviteRequestItem>

typealias ProjectsAcceptInviteRequest = BulkRequest<FindByStringId>
typealias ProjectsDeleteInviteRequest = BulkRequest<FindByStringId>

@Serializable
data class ProjectsDeleteMemberRequestItem(val username: String)
typealias ProjectsDeleteMemberRequest = BulkRequest<ProjectsDeleteMemberRequestItem>

@Serializable
data class ProjectsChangeRoleRequestItem(val username: String, val role: ProjectRole)
typealias ProjectsChangeRoleRequest = BulkRequest<ProjectsChangeRoleRequestItem>

interface ProjectGroupFlags {
    val includeMembers: Boolean?
}

@Serializable
data class ProjectsRetrieveGroupRequest(
    val id: String,
    override val includeMembers: Boolean? = null
) : ProjectGroupFlags

@Serializable
data class ProjectsBrowseGroupsRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val includeMembers: Boolean? = null
) : ProjectGroupFlags, WithPaginationRequestV2

typealias ProjectsCreateGroupRequest = Group.Specification

@Serializable
data class GroupMember(
    val username: String,
    val group: String
)

typealias ProjectsCreateGroupMemberRequest = BulkRequest<GroupMember>
typealias ProjectsDeleteGroupMemberRequest = BulkResponse<GroupMember>

