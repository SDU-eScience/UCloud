package dk.sdu.cloud.project.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.IngoingCall
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

data class CreateProjectRequest(
    val title: String,
    val parent: String?,
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
        val regex = Regex("[a-zA-Z0-9 _-]+")
        const val TITLE_MAX_LENGTH = 128
    }
}

typealias CreateProjectResponse = FindByStringId

data class ViewMemberInProjectRequest(val projectId: String, val username: String)
data class ViewMemberInProjectResponse(val member: ProjectMember)

data class InviteRequest(val projectId: String, val usernames: Set<String>) {
    init {
        if (usernames.size == 0) throw RPCException("No usernames supplied", HttpStatusCode.BadRequest)
        if (usernames.size > 200) throw RPCException("Too many invites in a single request", HttpStatusCode.BadRequest)
    }
}
typealias InviteResponse = Unit

data class DeleteMemberRequest(val projectId: String, val member: String)
typealias DeleteMemberResponse = Unit

data class ChangeUserRoleRequest(val projectId: String, val member: String, val newRole: ProjectRole)
typealias ChangeUserRoleResponse = Unit

data class OutgoingInvite(
    val username: String,
    val invitedBy: String,
    val timestamp: Long
)

data class IngoingInvite(
    val project: String,
    val title: String,
    val invitedBy: String,
    val timestamp: Long
)

data class TransferPiRoleRequest(val newPrincipalInvestigator: String)
typealias TransferPiRoleResponse = Unit

data class ArchiveRequest(val archiveStatus: Boolean)
typealias ArchiveResponse = Unit

/**
 * A project summary from a user's perspective
 */
data class UserProjectSummary(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val needsVerification: Boolean,
    val isFavorite: Boolean,
    val archived: Boolean,
    val parent: String?
)

data class UserGroupSummary(
    val project: String,
    val group: String,
    val username: String
)

data class ListProjectsRequest(
    val user: String? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
    val archived: Boolean? = null,
    val noFavorites: Boolean? = null
) : WithPaginationRequest

typealias ListProjectsResponse = Page<UserProjectSummary>

data class ListFavoriteProjectsRequest(
    val user: String?,
    override val itemsPerPage: Int,
    override val page: Int,
    val archived: Boolean
) : WithPaginationRequest

typealias ListFavoriteProjectsResponse = ListProjectsResponse

data class ViewProjectRequest(val id: String)
typealias ViewProjectResponse = UserProjectSummary

data class ListIngoingInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListIngoingInvitesResponse = Page<IngoingInvite>

data class ListOutgoingInvitesRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListOutgoingInvitesResponse = Page<OutgoingInvite>

data class AcceptInviteRequest(val projectId: String)
typealias AcceptInviteResponse = Unit

data class RejectInviteRequest(val username: String?, val projectId: String)
typealias RejectInviteResponse = Unit

typealias LeaveProjectRequest = Unit
typealias LeaveProjectResponse = Unit

data class ExistsRequest(val projectId: String)
data class ExistsResponse(val exists: Boolean)

data class ListSubProjectsRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest

typealias ListSubProjectsResponse = Page<Project>

typealias CountSubProjectsRequest = Unit
typealias CountSubProjectsResponse = Long

typealias ViewAncestorsRequest = Unit
typealias ViewAncestorsResponse = List<Project>

data class LookupByTitleRequest(
    val title: String
)

data class LookupByIdRequest(
    val id: String
)

data class LookupByIdBulkRequest(val ids: List<String>) {
    init {
        if (ids.isEmpty()) throw RPCException("ids is empty", HttpStatusCode.BadRequest)
        if (ids.size > 150) throw RPCException("too many ids", HttpStatusCode.BadRequest)
    }
}

typealias LookupPrincipalInvestigatorRequest = Unit
data class LookupPrincipalInvestigatorResponse(val principalInvestigator: String)

data class RenameProjectRequest(
    val id: String,
    val newTitle: String
)

typealias RenameProjectResponse = Unit

object Projects : CallDescriptionContainer("project") {
    val baseContext = "/api/projects"

    /**
     * Creates a project in UCloud.
     *
     * Only UCloud administrators can create root-level (i.e. no parent) projects. Project administrators can create
     * a sub-project by supplying [CreateProjectRequest.parent] with their [Project.id].
     *
     * End-users can create new projects by applying to an existing project through the grant-service.
     */
    val create = call<CreateProjectRequest, CreateProjectResponse, CommonErrorMessage>("create") {
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
    val viewMemberInProject =
        call<ViewMemberInProjectRequest, ViewMemberInProjectResponse, CommonErrorMessage>("viewMemberInProject") {
            auth {
                roles = Roles.PRIVILEGED
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
    val invite = call<InviteRequest, InviteResponse, CommonErrorMessage>("invite") {
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
    val acceptInvite = call<AcceptInviteRequest, AcceptInviteResponse, CommonErrorMessage>("acceptInvite") {
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
    val rejectInvite = call<RejectInviteRequest, RejectInviteResponse, CommonErrorMessage>("rejectInvite") {
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
    val listIngoingInvites = call<ListIngoingInvitesRequest, ListIngoingInvitesResponse, CommonErrorMessage>(
        "listIngoingInvites"
    ) {
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
    val listOutgoingInvites = call<ListOutgoingInvitesRequest, ListOutgoingInvitesResponse, CommonErrorMessage>(
        "listOutgoingInvites"
    ) {
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
    val transferPiRole = call<TransferPiRoleRequest, TransferPiRoleResponse, CommonErrorMessage>("transferPiRole") {
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
    val leaveProject = call<LeaveProjectRequest, LeaveProjectResponse, CommonErrorMessage>("leaveProject") {
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
    val deleteMember = call<DeleteMemberRequest, DeleteMemberResponse, CommonErrorMessage>("deleteMember") {
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
    val changeUserRole = call<ChangeUserRoleRequest, ChangeUserRoleResponse, CommonErrorMessage>("changeUserRole") {
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
    val listFavoriteProjects = call<ListFavoriteProjectsRequest, ListFavoriteProjectsResponse, CommonErrorMessage>("listFavoriteProjects") {
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
            }
        }
    }

    /**
     * Fetches a list of projects the calling user is a member of.
     */
    val listProjects = call<ListProjectsRequest, ListProjectsResponse, CommonErrorMessage>("listProjects") {
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
            }
        }
    }

    /**
     * View information about an existing project.
     *
     * Only members of the project have permissions to view a project.
     */
    val viewProject = call<ViewProjectRequest, ViewProjectResponse, CommonErrorMessage>("viewProject") {
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
    val verifyMembership =
        call<Unit, Unit, CommonErrorMessage>("verifyMembership") {
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
    val archive = call<ArchiveRequest, ArchiveResponse, CommonErrorMessage>("archive") {
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

    /**
     * Checks if a project exists.
     *
     * Only [Roles.PRIVILEGED] users can call this endpoint. It is intended that services call this to verify input
     * parameters that relate to existing projects.
     */
    val exists = call<ExistsRequest, ExistsResponse, CommonErrorMessage>("exists") {
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
     * Lists sub-projects of an existing project
     */
    val listSubProjects = call<ListSubProjectsRequest, ListSubProjectsResponse, CommonErrorMessage>("listSubProjects") {
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
                +boundTo(ListSubProjectsRequest::page)
            }
        }
    }

    /**
     * Returns the number of sub-projects of an existing project
     */
    @Deprecated("Should be replaced with listSubProjects.itemsInTotal")
    val countSubProjects = call<CountSubProjectsRequest, CountSubProjectsResponse, CommonErrorMessage>("countSubProjects") {
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
    val viewAncestors = call<ViewAncestorsRequest, ViewAncestorsResponse, CommonErrorMessage>("viewAncestors") {
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

    val lookupByTitle = call<LookupByTitleRequest, Project, CommonErrorMessage>("lookupByTitle") {
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

    val lookupById = call<LookupByIdRequest, Project, CommonErrorMessage>("lookupById") {
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

    val lookupByIdBulk = call<LookupByIdBulkRequest, List<Project>, CommonErrorMessage>("lookupByIdBulk") {
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
    val lookupPrincipalInvestigator =
        call<LookupPrincipalInvestigatorRequest, LookupPrincipalInvestigatorResponse, CommonErrorMessage>("lookupPrincipalInvestigator") {
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
    val rename = call<RenameProjectRequest, RenameProjectResponse, CommonErrorMessage>("rename") {
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
}
