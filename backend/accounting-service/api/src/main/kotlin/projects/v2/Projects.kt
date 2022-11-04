package dk.sdu.cloud.project.api.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

typealias ProjectRole = dk.sdu.cloud.project.api.ProjectRole

@Serializable
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    var email: String? = null,
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

            Currently, archiving does not mean a lot in UCloud. This is subject to change in the future. For the most
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

        @UCloudApiDoc("""
            A path to this project, conditionally included if `includePath = true`.
            
            The path is a '/' separated string where each component is a project title. The path will not contain
            this project. The path does not start or end with a '/'. If the project is a root, then "" will be returned.
        """)
        var path: String? = null,
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
    val projectTitle: String,
)

@Serializable
data class RenameProjectRequest(
    val id: String,
    val newTitle: String
)
typealias RenameProjectResponse = Unit

@Serializable
data class SetProjectVerificationStatusRequest(
    val projectId: String,
)
typealias SetProjectVerificationStatusResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Projects : CallDescriptionContainer("projects.v2") {
    const val baseContext = "/api/projects/v2"
    const val inviteResource = "invites"
    const val groupResource = "groups"
    const val groupMemberResource = "groupMembers"

    // Project management
    val retrieve = call("retrieve", ProjectsRetrieveRequest.serializer(), Project.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val browse = call("browse", ProjectsBrowseRequest.serializer(), PageV2.serializer(Project.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val create = call("create", BulkRequest.serializer(Project.Specification.serializer()), BulkResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val archive = call("archive", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "archive")
    }

    val unarchive = call("unarchive", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "unarchive")
    }

    val toggleFavorite = call("toggleFavorite", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "toggleFavorite")
    }

    val updateSettings = call("updateSettings", ProjectsUpdateSettingsRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "updateSettings")
    }

    // Internal API
    @UCloudApiInternal(InternalLevel.BETA)
    val retrieveAllUsersGroup = call("retrieveAllUsersGroup", BulkRequest.serializer(FindByProjectId.serializer()), BulkResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "retrieveAllUsersGroup", roles = Roles.SERVICE)
    }

    val renameProject = call("renameProject", BulkRequest.serializer(RenameProjectRequest.serializer()), RenameProjectResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "renameProject")
    }

    val projectVerificationStatus = call("projectVerificationStatus", BulkRequest.serializer(SetProjectVerificationStatusRequest.serializer()), SetProjectVerificationStatusResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "projectVerificationStatus")
    }

    val verifyMembership = call("verifyMembership", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "verifyMembership")
    }

    // Invitations
    val browseInvites = call("browseInvites", ProjectsBrowseInvitesRequest.serializer(), PageV2.serializer(ProjectInvite.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, inviteResource)
    }

    val createInvite = call("createInvite", BulkRequest.serializer(ProjectsCreateInviteRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, inviteResource)
    }

    val acceptInvite = call("acceptInvite", BulkRequest.serializer(FindByProjectId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "acceptInvite")
    }

    val deleteInvite = call("deleteInvite", BulkRequest.serializer(ProjectsDeleteInviteRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deleteInvite")
    }

    // Member management
    val deleteMember = call("deleteMember", BulkRequest.serializer(ProjectsDeleteMemberRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deleteMember")
    }

    val changeRole = call("changeRole", BulkRequest.serializer(ProjectsChangeRoleRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "changeRole")
    }

    // Group management
    val retrieveGroup = call("retrieveGroup", ProjectsRetrieveGroupRequest.serializer(), Group.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, groupResource, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val createGroup = call("createGroup", BulkRequest.serializer(Group.Specification.serializer()), BulkResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, groupResource)
    }

    val renameGroup = call("renameGroup", BulkRequest.serializer(ProjectsRenameGroupRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "renameGroup")
    }

    val deleteGroup = call("deleteGroup", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deleteGroup")
    }

    // Group member management
    val createGroupMember = call("createGroupMember", BulkRequest.serializer(GroupMember.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, groupMemberResource)
    }

    val deleteGroupMember = call("deleteGroupMember", BulkRequest.serializer(GroupMember.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deleteGroupMember")
    }

    // Provider specific endpoints
    val retrieveProviderProject = call("retrieveProviderProject", Unit.serializer(), Project.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "providerProject", roles = Roles.PROVIDER)
    }
}

interface ProjectFlags {
    val includeMembers: Boolean?
    val includeGroups: Boolean?
    val includeFavorite: Boolean?
    val includeArchived: Boolean?
    val includeSettings: Boolean?
    val includePath: Boolean?
}

@Serializable
data class ProjectsRetrieveRequest(
    val id: String,
    override val includeMembers: Boolean? = null,
    override val includeGroups: Boolean? = null,
    override val includeFavorite: Boolean? = null,
    override val includeArchived: Boolean? = null,
    override val includeSettings: Boolean? = null,
    override val includePath: Boolean? = null,
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
    override val includePath: Boolean? = null,

    val sortBy: ProjectsSortBy? = null,
    val sortDirection: SortDirection? = null,
) : ProjectFlags, WithPaginationRequestV2

typealias ProjectsCreateRequest = BulkRequest<Project.Specification>
typealias ProjectsArchiveRequest = BulkRequest<FindByStringId>
typealias ProjectsUnarchiveRequest = BulkRequest<FindByStringId>
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

@Serializable
data class FindByProjectId(val project: String)
typealias ProjectsAcceptInviteRequest = BulkRequest<FindByProjectId>

@Serializable
data class ProjectsDeleteInviteRequestItem(
    val project: String,
    val username: String,
)
typealias ProjectsDeleteInviteRequest = BulkRequest<ProjectsDeleteInviteRequestItem>

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

typealias ProjectsCreateGroupRequest = BulkRequest<Group.Specification>

@Serializable
data class ProjectsRenameGroupRequestItem(
    val group: String,
    val newTitle: String,
)

typealias ProjectsRenameGroupRequest = BulkRequest<ProjectsRenameGroupRequestItem>

@Serializable
data class GroupMember(
    val username: String,
    val group: String
)

typealias ProjectsCreateGroupMemberRequest = BulkRequest<GroupMember>
typealias ProjectsDeleteGroupMemberRequest = BulkRequest<GroupMember>

