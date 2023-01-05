package dk.sdu.cloud.project.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class CreateProjectRequest(
    val title: String,
    val parent: String? = null,
    val principalInvestigator: String? = null
) {
    init {
        if (title.isEmpty()) throw RPCException("Project title must not be empty", HttpStatusCode.BadRequest)
        if (!title.matches(regex)) {
            throw RPCException("Title must not contain special characters", HttpStatusCode.BadRequest)
        }
        if (title.length > TITLE_MAX_LENGTH) throw RPCException("Title is too long", HttpStatusCode.BadRequest)
    }

    companion object {
        val regex = Regex("[a-zA-Z0-9 æøåÆØÅ_-]+")
        const val TITLE_MAX_LENGTH = 128
    }
}

typealias CreateProjectResponse = FindByStringId

@Serializable
data class ViewMemberInProjectRequest(val projectId: String, val username: String)
@Serializable
data class ViewMemberInProjectResponse(val member: ProjectMember)

@Serializable
data class InviteRequest(val projectId: String, val usernames: Set<String>) {
    init {
        if (usernames.isEmpty()) throw RPCException("No usernames supplied", HttpStatusCode.BadRequest)
        if (usernames.size > 200) throw RPCException("Too many invites in a single request", HttpStatusCode.BadRequest)
    }
}
typealias InviteResponse = Unit

@Serializable
data class DeleteMemberRequest(val projectId: String, val member: String)
typealias DeleteMemberResponse = Unit

@Serializable
data class ChangeUserRoleRequest(val projectId: String, val member: String, val newRole: ProjectRole)
typealias ChangeUserRoleResponse = Unit

@Serializable
data class OutgoingInvite(
    val username: String,
    val invitedBy: String,
    val timestamp: Long
)

@Serializable
data class IngoingInvite(
    val project: String,
    val title: String,
    val invitedBy: String,
    val timestamp: Long
)

@Serializable
data class TransferPiRoleRequest(val newPrincipalInvestigator: String)
typealias TransferPiRoleResponse = Unit

@Serializable
data class ArchiveRequest(val archiveStatus: Boolean)
typealias ArchiveResponse = Unit

@Serializable
data class ArchiveBulkRequest(val projects: List<UserProjectSummary>)
typealias ArchiveBulkResponse = Unit

/**
 * A project summary from a user's perspective
 */
@Serializable
data class UserProjectSummary(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val needsVerification: Boolean,
    val isFavorite: Boolean,
    val archived: Boolean,
    val parent: String? = null,
    val ancestorPath: String? = null
)

@Serializable
data class UserGroupSummary(
    val project: String,
    val group: String,
    val username: String
)

@Serializable
data class ListProjectsRequest(
    val user: String? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
    val archived: Boolean? = null,
    val noFavorites: Boolean? = null,
    val showAncestorPath: Boolean? = null
) : WithPaginationRequest

typealias ListProjectsResponse = Page<UserProjectSummary>

@Serializable
data class ListFavoriteProjectsRequest(
    val user: String? = null,
    override val itemsPerPage: Int,
    override val page: Int,
    val archived: Boolean,
    val showAncestorPath: Boolean? = null
) : WithPaginationRequest

typealias ListFavoriteProjectsResponse = ListProjectsResponse

@Serializable
data class ViewProjectRequest(val id: String)
typealias ViewProjectResponse = UserProjectSummary

@Serializable
data class ListIngoingInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListIngoingInvitesResponse = Page<IngoingInvite>

@Serializable
data class ListOutgoingInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListOutgoingInvitesResponse = Page<OutgoingInvite>

@Serializable
data class AcceptInviteRequest(val projectId: String)
typealias AcceptInviteResponse = Unit

@Serializable
data class RejectInviteRequest(val username: String? = null, val projectId: String)
typealias RejectInviteResponse = Unit

typealias LeaveProjectRequest = Unit
typealias LeaveProjectResponse = Unit

@Serializable
data class ExistsRequest(val projectId: String)
@Serializable
data class ExistsResponse(val exists: Boolean)

@Serializable
data class ListSubProjectsRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias ListSubProjectsResponse = PageV2<MemberInProject>

typealias CountSubProjectsRequest = Unit
typealias CountSubProjectsResponse = Long

typealias ViewAncestorsRequest = Unit
typealias ViewAncestorsResponse = List<Project>

@Serializable
data class LookupByTitleRequest(
    val title: String
)

@Serializable
data class LookupByIdRequest(
    val id: String
)

@Serializable
data class LookupByIdBulkRequest(val ids: List<String>) {
    init {
        if (ids.isEmpty()) throw RPCException("ids is empty", HttpStatusCode.BadRequest)
        if (ids.size > 150) throw RPCException("too many ids", HttpStatusCode.BadRequest)
    }
}

typealias LookupPrincipalInvestigatorRequest = Unit
@Serializable
data class LookupPrincipalInvestigatorResponse(val principalInvestigator: String)

@Serializable
data class RenameProjectRequest(
    val id: String,
    val newTitle: String
)

typealias RenameProjectResponse = Unit

@Serializable
data class ToggleRenamingRequest(
    val projectId: String
)

typealias ToggleRenamingResponse = Unit

@Serializable
data class AllowsRenamingRequest(
    val projectId: String
)

@Serializable
data class AllowsRenamingResponse(
    val allowed: Boolean
)

@Serializable
data class UpdateDataManagementPlanRequest(val id: String, val dmp: String? = null)
typealias UpdateDataManagementPlanResponse = Unit

typealias FetchDataManagementPlanRequest = Unit
@Serializable
data class FetchDataManagementPlanResponse(val dmp: String? = null)

interface ProjectIncludeFlags {
    val includeFullPath: Boolean?
}

@Serializable
data class ProjectSearchByPathRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val next: String?,
    override val consistency: PaginationRequestV2Consistency?,
    override val itemsToSkip: Long?,

    override val includeFullPath: Boolean?

): WithPaginationRequestV2, ProjectIncludeFlags

typealias ProjectSearchByPathResponse = PageV2<Project>


@TSTopLevel
object Projects : CallDescriptionContainer("project") {
    const val baseContext = "/api/projects"

    init {
        description = """
The projects feature allow for collaboration between different users across the entire UCloud platform.

This project establishes the core abstractions for projects and establishes an event stream for receiving updates about
changes. Other services extend the projects feature and subscribe to these changes to create the full project feature.

${ApiConventions.nonConformingApiWarning}

## Definition

A project in UCloud is a collection of `members` which is uniquely identified by an `id`. All `members` are
[users](../../core/users/creation.md) identified by their `username` and have exactly one `role`. A user always has exactly one
`role`. Each project has exactly one principal investigator (`PI`). The `PI` is responsible for managing the project,
including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project, and their privileges within project
management.

A project can be updated by adding/removing/changing any of its `members`. Such an update will trigger a new message
on the event stream.

A project is sub-divided into groups:

![](/backend/accounting-service/wiki/structure.png)

Each project may have 0 or more groups. The groups can have 0 or more members. A group belongs to exactly one project,
and the members of a group can only be from the project it belongs to.

## Creating Projects and Sub-Projects

All projects created by end-users have exactly one parent project. Only UCloud administrators can create root-level
projects, that is a project without a parent. This allows users of UCloud to create a hierarchy of projects. The
project hierarchy plays a significant role in accounting.

Normal users can create a project through the [grant application](../grants/grants.md) feature.

A project can be uniquely identified by the path from the root project to the leaf-project. As a result, the `title` of
a project must be unique within a single project. `title`s are case-insensitive.

Permissions and memberships are _not_ hierarchical. This means that a user must be explicitly added to every project
they need permissions in. UCloud administrators can always create a sub-project in any given project. A setting exists
for every project which allows normal users to create sub-projects.

---

__Example:__ A project hierarchy

![](/backend/accounting-service/wiki/subprojects.png)

__Figure 1:__ A storage hierarchy

Figure 1 shows a hierarchy of projects. Note that users deep in the hierarchy are not necessarily members of the
projects further up in the hierarchy. For example, being a member of "IMADA" does not imply membership of "NAT".
A member of "IMADA" can be a member of "NAT" but they must be _explicitly_ added to both projects.

None of the projects share _any_ resources. Each individual project will have their own home directory. The
administrators, or any other user, of "NAT" will not be able to read/write any files of "IMADA" unless they have
explicitly been added to the "IMADA" project.

## The Project Context

All requests in UCloud are executed in a particular context. The header of every request defines the context. For the
HTTP backend this is done in the `Project` header. The absence of a project implies that the request is executed in the
personal project context, also called *My Workspace* on UCloud.

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

    /**
     * Creates a project in UCloud.
     *
     * Only UCloud administrators can create root-level (i.e. no parent) projects. Project administrators can create
     * a sub-project by supplying [CreateProjectRequest.parent] with their [Project.id].
     *
     * End-users can create new projects by applying to an existing project through the grant-service.
     */
    val create = call("create", CreateProjectRequest.serializer(), CreateProjectResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * View the status of a member in a project.
     *
     * This endpoint is only available for [Roles.PRIVILEGED]. It is commonly used as part of a permission check related
     * to project resources.
     */
    val viewMemberInProject = call("viewMemberInProject", ViewMemberInProjectRequest.serializer(), ViewMemberInProjectResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                roles = Roles.PRIVILEGED + Role.PROVIDER
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"members"
                }

                params {
                    +boundTo(ViewMemberInProjectRequest::projectId)
                    +boundTo(ViewMemberInProjectRequest::username)
                }
            }
        }

    /**
     * Invites a set of users to a project.
     *
     * Only a project administrator of [InviteRequest.projectId] can [invite] members.
     */
    val invite = call("invite", InviteRequest.serializer(), InviteResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"invites"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Accepts the [invite] to a project.
     *
     * Only the recipient of the invite can call this endpoint. This call will fail if an invite has already been sent
     * to the user.
     */
    val acceptInvite = call("acceptInvite", AcceptInviteRequest.serializer(), AcceptInviteResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"invites"
                +"accept"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Rejects the invite to a project.
     *
     * The recipient of the invite _and_ a project administrator of the project can call this endpoint.
     * Calling this will invalidate the invite and a new invite must be sent to the user if they wish to join.
     */
    val rejectInvite = call("rejectInvite", RejectInviteRequest.serializer(), RejectInviteResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"invites"
                +"reject"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Fetches a list of invites received by the requesting user.
     */
    val listIngoingInvites = call("listIngoingInvites", ListIngoingInvitesRequest.serializer(), Page.serializer(IngoingInvite.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"invites"
                +"ingoing"
            }

            params {
                +boundTo(ListIngoingInvitesRequest::itemsPerPage)
                +boundTo(ListIngoingInvitesRequest::page)
            }
        }
    }

    /**
     * Fetches a list of invites sent by the requesting project.
     */
    val listOutgoingInvites = call("listOutgoingInvites", ListOutgoingInvitesRequest.serializer(), Page.serializer(OutgoingInvite.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"invites"
                +"outgoing"
            }

            params {
                +boundTo(ListOutgoingInvitesRequest::itemsPerPage)
                +boundTo(ListOutgoingInvitesRequest::page)
            }
        }
    }

    /**
     * Transfers the [ProjectRole.PI] role of the calling user to a different member of the project.
     *
     * Only the [ProjectRole.PI] of the [IngoingCall.project] can call this.
     */
    val transferPiRole = call("transferPiRole", TransferPiRoleRequest.serializer(), TransferPiRoleResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"transfer-pi"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * The calling user leaves the project.
     *
     * Note: The PI cannot leave a project. They must first transfer the role to another user, see [transferPiRole].
     * If there are no other members then the PI can [archive] the project.
     */
    val leaveProject = call("leaveProject", LeaveProjectRequest.serializer(), LeaveProjectResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"leave"
            }
        }
    }

    /**
     * Removes a member from a project.
     *
     * Only project administrators of [DeleteMemberRequest.projectId] can remove members from the project.
     */
    val deleteMember = call("deleteMember", DeleteMemberRequest.serializer(), DeleteMemberResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"members"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Changes the project role of an existing member.
     *
     * Only the project administrators can change the role of a member. The new role cannot be [ProjectRole.PI]. In
     * order to promote a user to PI use the [transferPiRole] endpoint.
     */
    val changeUserRole = call("changeUserRole", ChangeUserRoleRequest.serializer(), ChangeUserRoleResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"members"
                +"change-role"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Fetches a list of favorite projects for the calling user.
     */
    val listFavoriteProjects = call("listFavoriteProjects", ListFavoriteProjectsRequest.serializer(), Page.serializer(UserProjectSummary.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listFavorites"
            }

            params {
                +boundTo(ListFavoriteProjectsRequest::itemsPerPage)
                +boundTo(ListFavoriteProjectsRequest::page)
                +boundTo(ListFavoriteProjectsRequest::user)
                +boundTo(ListFavoriteProjectsRequest::archived)
                +boundTo(ListFavoriteProjectsRequest::showAncestorPath)
            }
        }
    }

    /**
     * Fetches a list of projects the calling user is a member of.
     */
    val listProjects = call("listProjects", ListProjectsRequest.serializer(), Page.serializer(UserProjectSummary.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list"
            }

            params {
                +boundTo(ListProjectsRequest::itemsPerPage)
                +boundTo(ListProjectsRequest::page)
                +boundTo(ListProjectsRequest::user)
                +boundTo(ListProjectsRequest::archived)
                +boundTo(ListProjectsRequest::noFavorites)
                +boundTo(ListProjectsRequest::showAncestorPath)
            }
        }
    }

    /**
     * View information about an existing project.
     *
     * Only members of the project have permissions to view a project.
     */
    val viewProject = call("viewProject", ViewProjectRequest.serializer(), ViewProjectResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"view"
            }

            params {
                +boundTo(ViewProjectRequest::id)
            }
        }
    }

    /**
     * Verify that the users of a project are still correct.
     *
     * We occasionally ask project administrators to verify that the members of a project is still correct. Only project
     * administrators of a project can verify membership.
     */
    val verifyMembership = call("verifyMembership", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.AUTHENTICATED
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"verify-membership"
                }
            }
        }

    /**
     * Archives/unarchives a project.
     *
     * Archiving a project has no other effect than hiding the project from various calls. No resources should be
     * deleted as a result of this action.
     *
     * Archiving can be reversed by calling this endpoint with [ArchiveRequest.archiveStatus] `= false`.
     */
    val archive = call("archive", ArchiveRequest.serializer(), ArchiveResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"archive"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val archiveBulk = call("archiveBulk", ArchiveBulkRequest.serializer(), ArchiveBulkResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"archiveBulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Checks if a project exists.
     *
     * Only [Roles.PRIVILEGED] users can call this endpoint. It is intended that services call this to verify input
     * parameters that relate to existing projects.
     */
    val exists = call("exists", ExistsRequest.serializer(), ExistsResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"exists"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Lists sub-projects of an existing project in the PageV2 format
     */

    val listSubProjects = call("listSubProjects", ListSubProjectsRequest.serializer(), PageV2.serializer(MemberInProject.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"sub-projects"
            }

            params {
                +boundTo(ListSubProjectsRequest::itemsPerPage)
                +boundTo(ListSubProjectsRequest::consistency)
                +boundTo(ListSubProjectsRequest::itemsToSkip)
                +boundTo(ListSubProjectsRequest::next)
            }
        }
    }

    /**
     * Returns the number of sub-projects of an existing project
     */
    @Deprecated("Should be replaced with listSubProjects.itemsInTotal")
    val countSubProjects = call("countSubProjects", CountSubProjectsRequest.serializer(), CountSubProjectsResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"sub-projects-count"
            }
        }
    }

    /**
     * Returns a complete list of ancestors of an existing project
     */
    val viewAncestors = call("viewAncestors", ViewAncestorsRequest.serializer(), ListSerializer(Project.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"ancestors"
            }
        }
    }

    val lookupByPath = call("lookupByPath", LookupByTitleRequest.serializer(), Project.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookupByTitle"
            }

            params {
                +boundTo(LookupByTitleRequest::title)
            }
        }
    }

    val lookupById = call("lookupById", LookupByIdRequest.serializer(), Project.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookupById"
            }

            params {
                +boundTo(LookupByIdRequest::id)
            }
        }
    }

    val lookupByIdBulk = call("lookupByIdBulk", LookupByIdBulkRequest.serializer(), ListSerializer(Project.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"lookupByIdBulk"
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    /**
     * Lookup the principal investigator ([ProjectRole.PI]) of a project
     */
    val lookupPrincipalInvestigator = call("lookupPrincipalInvestigator", LookupPrincipalInvestigatorRequest.serializer(), LookupPrincipalInvestigatorResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ
                roles = Roles.PRIVILEGED
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"lookup-pi"
                }
            }
        }

    /**
     * Rename a project
     */
    val rename = call("rename", RenameProjectRequest.serializer(), RenameProjectResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"rename"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val toggleRenaming = call("toggleRenaming", ToggleRenamingRequest.serializer(), ToggleRenamingResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"toggleRenaming"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val allowedRenaming = call("allowsRenaming", AllowsRenamingRequest.serializer(), AllowsRenamingResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"renameable"
            }

            params {
                +boundTo(AllowsRenamingRequest::projectId)
            }
        }
    }

    val allowsSubProjectRenaming = call("allowsSubProjectRenaming", AllowsRenamingRequest.serializer(), AllowsRenamingResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"renameable-sub"
            }

            params {
                +boundTo(AllowsRenamingRequest::projectId)
            }
        }
    }

    val updateDataManagementPlan = call("updateDataManagementPlan", UpdateDataManagementPlanRequest.serializer(), UpdateDataManagementPlanResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"update-dmp"
                }

                body { bindEntireRequestFromBody() }
            }
        }

    val fetchDataManagementPlan = call("fetchDataManagementPlan", FetchDataManagementPlanRequest.serializer(), FetchDataManagementPlanResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"dmp"
                }
            }
        }

    val search = call("search", ProjectSearchByPathRequest.serializer(), PageV2.serializer(Project.serializer()), CommonErrorMessage.serializer()) {
        httpSearch(
            baseContext = baseContext,
            roles = Roles.PRIVILEGED
        )
    }
}
