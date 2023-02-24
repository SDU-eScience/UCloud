package dk.sdu.cloud.project.api.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

typealias ProjectRole = dk.sdu.cloud.project.api.ProjectRole

@Serializable
@UCloudApiStable
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    var email: String? = null,
)

@Serializable
@UCloudApiStable
@UCloudApiOwnedBy(Projects::class)
data class Project(
    val id: String,
    val createdAt: Long,
    val specification: Specification,
    val status: Status,
) {
    @Serializable
    @UCloudApiStable
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
    @UCloudApiStable
    data class Specification(
        val parent: String?,
        val title: String,
        val canConsumeResources: Boolean = true,
    ) {
        init {
            checkSingleLine(::title, title, maximumSize = 128)
        }
    }

    @Serializable
    @UCloudApiStable
    data class Settings(
        val subprojects: SubProjects? = null,
    ) {
        @Serializable
        @UCloudApiStable
        data class SubProjects(
            val allowRenaming: Boolean = false
        )
    }
}

@Serializable
@UCloudApiStable
data class Group(
    val id: String,
    val createdAt: Long,
    val specification: Specification,
    val status: Status
) {
    @Serializable
    @UCloudApiStable
    data class Specification(
        val project: String,
        val title: String,
    ) {
        init {
            checkSingleLine(::title, title, maximumSize = 128)
        }
    }

    @Serializable
    @UCloudApiStable
    data class Status(
        val members: List<String>? = null
    )
}

@Serializable
@UCloudApiStable
data class ProjectInvite(
    val createdAt: Long,
    val invitedBy: String,
    val invitedTo: String,
    val recipient: String,
    val projectTitle: String,
)

@Serializable
@UCloudApiStable
data class RenameProjectRequest(
    val id: String,
    val newTitle: String
)
typealias RenameProjectResponse = Unit

@Serializable
@UCloudApiStable
data class SetProjectVerificationStatusRequest(
    val projectId: String,
)
typealias SetProjectVerificationStatusResponse = Unit

@UCloudApiStable
object Projects : CallDescriptionContainer("projects.v2") {
    const val baseContext = "/api/projects/v2"
    const val inviteResource = "invites"
    const val inviteLinkResource = "link"
    const val groupResource = "groups"
    const val groupMemberResource = "groupMembers"

    init {
        description = """
The projects feature allow for collaboration between different users across the entire UCloud platform.

This project establishes the core abstractions for projects and establishes an event stream for receiving updates about
changes. Other services extend the projects feature and subscribe to these changes to create the full project feature.

## Definition

A project in UCloud is a collection of `members` which is uniquely identified by an `id`. All `members` are
[users](/docs/developer-guide/core/users/authentication/users.md) identified by their `username` and have exactly one 
`role`. A user always has exactly one `role`. Each project has exactly one principal investigator (`PI`). The `PI` is 
responsible for managing the project, including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project, and their privileges within project
management.

A project can be updated by adding/removing/changing any of its `members`.

A project is sub-divided into groups:

![](/backend/accounting-service/wiki/structure.png)

Each project may have 0 or more groups. The groups can have 0 or more members. A group belongs to exactly one project,
and the members of a group can only be from the project it belongs to.

## Special Groups

All projects have some special groups. The most common, and as of 05/01/23 the only, special group is the "All Users"
group. This group automatically contains all members of the project. These are synchronized every single time a user is
added or removed from a project. This special group is used by providers when registering resources with UCloud.

## Creating Projects and Sub-Projects

All projects create by end-users have exactly one parent project. Only UCloud administrators can create root-level
projects, that is a project without a parent. This allows users of UCloud to create a hierarchy of projects. The
project hierarchy plays a significant role in accounting.

Normal users can create a project through the [grant application](./grants/grants.md) feature.

A project can be uniquely identified by the path from the root project to the leaf-project. As a result, the `title` of
a project must be unique within a single project. `title`s are case-insensitive.

Permissions and memberships are _not_ hierarchical. This means that a user must be explicitly added to every project
they need permissions in. UCloud administrators can always create a sub-project in any given project. A setting exists
for every project which allows normal users to create sub-projects.

---

__Example:__ A project hierarchy

![](/backend/accounting-service/wiki/subprojects.png)

__Figure 1:__ A project hierarchy

Figure 1 shows a hierarchy of projects. Note that users deep in the hierarchy are not necessarily members of the
projects further up in the hierarchy. For example, being a member of "IMADA" does not imply membership of "NAT".
A member of "IMADA" can be a member of "NAT" but they must be _explicitly_ added to both projects.

None of the projects share _any_ resources. Each individual project will have their own home directory. The
administrators, or any other user, of "NAT" will not be able to read/write any files of "IMADA" unless they have
explicitly been added to the "IMADA" project.

## The Project Context (also known as workspace)

All requests in UCloud are executed in a particular context. The header of every request defines the context. For the
HTTP backend this is done in the `Project` header. The absence of a project implies that the request is executed in the
personal project context.

![](/backend/accounting-service/wiki/context-switcher.png)

__Figure 2:__ The UCloud user interface allows you to select context through a dropdown in the navigation header.

---

__Example:__ Accessing the project context from a microservice

```kotlin
implement(Descriptions.call) {
    val project: String? = ctx.project // null implies the personal project
    ok(service.doSomething(project))
}
```

--- 
        """.trimIndent()
    }

    // Project management
    val retrieve = call("retrieve", ProjectsRetrieveRequest.serializer(), Project.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val browse = call("browse", ProjectsBrowseRequest.serializer(), PageV2.serializer(Project.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, roles = Roles.END_USER + Roles.PROVIDER)
    }

    val create = call("create", BulkRequest.serializer(Project.Specification.serializer()), BulkResponse.serializer(FindByStringId.serializer()), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, roles = Roles.END_USER + Roles.SERVICE)
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

    @UCloudApiInternal(InternalLevel.STABLE)
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

    // Invitation links
    val createInviteLink = call("createInviteLink", Unit.serializer(), ProjectInviteLink.serializer(), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, inviteLinkResource)
    }

    val browseInviteLinks = call("browseInviteLinks", ProjectsBrowseInviteLinksRequest.serializer(), PageV2.serializer(ProjectInviteLink.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext, inviteLinkResource)
    }

    val retrieveInviteLinkInfo = call("retrieveInviteLinkProject", ProjectsRetrieveInviteLinkInfoRequest.serializer(), ProjectsRetrieveInviteLinkInfoResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, inviteLinkResource)
    }

    val deleteInviteLink = call("deleteInviteLink", ProjectsDeleteInviteLinkRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "deleteInviteLink")
    }

    val updateInviteLink = call("updateInviteLink", ProjectsUpdateInviteLinkRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "updateInviteLink")
    }

    val acceptInviteLink = call("acceptInviteLink", ProjectsAcceptInviteLinkRequest.serializer(), ProjectsAcceptInviteLinkResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "acceptInviteLink")
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
@UCloudApiStable
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
@UCloudApiStable
enum class ProjectsSortBy {
    favorite,
    title,
    parent,
}

@Serializable
@UCloudApiStable
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
@UCloudApiStable
enum class ProjectInviteType {
    INGOING,
    OUTGOING,
}

interface ProjectInviteFlags {
    val filterType: ProjectInviteType?
}

@Serializable
@UCloudApiStable
data class ProjectsBrowseInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterType: ProjectInviteType? = null,
) : ProjectInviteFlags, WithPaginationRequestV2

@Serializable
@UCloudApiStable
data class ProjectsCreateInviteRequestItem(val recipient: String)
typealias ProjectsCreateInviteRequest = BulkRequest<ProjectsCreateInviteRequestItem>

@Serializable
@UCloudApiStable
data class FindByProjectId(val project: String)
typealias ProjectsAcceptInviteRequest = BulkRequest<FindByProjectId>

@Serializable
@UCloudApiStable
data class ProjectsDeleteInviteRequestItem(
    val project: String,
    val username: String,
)
typealias ProjectsDeleteInviteRequest = BulkRequest<ProjectsDeleteInviteRequestItem>

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsBrowseInviteLinksRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null

) : WithPaginationRequestV2
@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectInviteLink(
    val token: String,
    val expires: Long,
    val groupAssignment: List<String> = emptyList(),
    val roleAssignment: ProjectRole = ProjectRole.USER
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsDeleteInviteLinkRequest(
    val token: String
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsRetrieveInviteLinkInfoRequest(
    val token: String
)
@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsRetrieveInviteLinkInfoResponse(
    val token: String,
    val project: Project,
    val isMember: Boolean
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsUpdateInviteLinkRequest(
    val token: String,
    val role: ProjectRole,
    val groups: List<String>
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsAcceptInviteLinkRequest(
    val token: String
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ProjectsAcceptInviteLinkResponse(
    val project: String
)


@Serializable
@UCloudApiStable
data class ProjectsDeleteMemberRequestItem(val username: String)
typealias ProjectsDeleteMemberRequest = BulkRequest<ProjectsDeleteMemberRequestItem>

@Serializable
@UCloudApiStable
data class ProjectsChangeRoleRequestItem(val username: String, val role: ProjectRole)
typealias ProjectsChangeRoleRequest = BulkRequest<ProjectsChangeRoleRequestItem>

interface ProjectGroupFlags {
    val includeMembers: Boolean?
}

@Serializable
@UCloudApiStable
data class ProjectsRetrieveGroupRequest(
    val id: String,
    override val includeMembers: Boolean? = null
) : ProjectGroupFlags

typealias ProjectsCreateGroupRequest = BulkRequest<Group.Specification>

@Serializable
@UCloudApiStable
data class ProjectsRenameGroupRequestItem(
    val group: String,
    val newTitle: String,
)

typealias ProjectsRenameGroupRequest = BulkRequest<ProjectsRenameGroupRequestItem>

@Serializable
@UCloudApiStable
data class GroupMember(
    val username: String,
    val group: String
)

typealias ProjectsCreateGroupMemberRequest = BulkRequest<GroupMember>
typealias ProjectsDeleteGroupMemberRequest = BulkRequest<GroupMember>

