package dk.sdu.cloud.project.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.project.favorite.api.ListFavoritesRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

data class CreateProjectRequest(val title: String) {
    init {
        if (!title.matches(regex)) {
            throw RPCException("Title must not contain special characters", HttpStatusCode.BadRequest)
        }
        if (title.length > TITLE_MAX_LENGTH) throw RPCException("Title is too long", HttpStatusCode.BadRequest)
    }

    companion object {
        val regex = Regex("[a-zA-Z0-9 -_]+")
        const val TITLE_MAX_LENGTH = 128
    }
}

typealias CreateProjectResponse = Unit

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
    val archived: Boolean
)

data class UserGroupSummary(
    val projectId: String,
    val group: String,
    val username: String
)

data class ListProjectsRequest(
    val user: String?,
    override val itemsPerPage: Int?,
    override val page: Int?,
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

data class ListIngoingInvitesRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListIngoingInvitesResponse = Page<IngoingInvite>

data class ListOutgoingInvitesRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListOutgoingInvitesResponse = Page<OutgoingInvite>

data class AcceptInviteRequest(val projectId: String)
typealias AcceptInviteResponse = Unit

data class RejectInviteRequest(val username: String?, val projectId: String)
typealias RejectInviteResponse = Unit

typealias LeaveProjectRequest = Unit
typealias LeaveProjectResponse = Unit

data class ExistsRequest(val projectId: String)
data class ExistsResponse(val exists: Boolean)

object Projects : CallDescriptionContainer("project") {
    val baseContext = "/api/projects"

    val create = call<CreateProjectRequest, CreateProjectResponse, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val viewMemberInProject =
        call<ViewMemberInProjectRequest, ViewMemberInProjectResponse, CommonErrorMessage>("viewMemberInProject") {
            auth {
                roles = Roles.PRIVILEDGED
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

    val invite = call<InviteRequest, InviteResponse, CommonErrorMessage>("invite") {
        auth {
            access = AccessRight.READ_WRITE
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

    val acceptInvite = call<AcceptInviteRequest, AcceptInviteResponse, CommonErrorMessage>("acceptInvite") {
        auth {
            access = AccessRight.READ_WRITE
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

    val rejectInvite = call<RejectInviteRequest, RejectInviteResponse, CommonErrorMessage>("rejectInvite") {
        auth {
            access = AccessRight.READ_WRITE
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

    val listIngoingInvites = call<ListIngoingInvitesRequest, ListIngoingInvitesResponse, CommonErrorMessage>("listIngoingInvites") {
        auth {
            access = AccessRight.READ_WRITE
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

    val listOutgoingInvites = call<ListOutgoingInvitesRequest, ListOutgoingInvitesResponse, CommonErrorMessage>("listOutgoingInvites") {
        auth {
            access = AccessRight.READ_WRITE
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

    val transferPiRole = call<TransferPiRoleRequest, TransferPiRoleResponse, CommonErrorMessage>("transferPiRole") {
        auth {
            access = AccessRight.READ_WRITE
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

    val leaveProject = call<LeaveProjectRequest, LeaveProjectResponse, CommonErrorMessage>("leaveProject") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"leave"
            }
        }
    }

    val deleteMember = call<DeleteMemberRequest, DeleteMemberResponse, CommonErrorMessage>("deleteMember") {
        auth {
            access = AccessRight.READ_WRITE
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

    val changeUserRole = call<ChangeUserRoleRequest, ChangeUserRoleResponse, CommonErrorMessage>("changeUserRole") {
        auth {
            access = AccessRight.READ_WRITE
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

    val listFavoriteProjects = call<ListFavoriteProjectsRequest, ListFavoriteProjectsResponse, CommonErrorMessage>("listFavoriteProjects") {
        auth {
            access = AccessRight.READ
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

    val listProjects = call<ListProjectsRequest, ListProjectsResponse, CommonErrorMessage>("listProjects") {
        auth {
            access = AccessRight.READ
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

    val viewProject = call<ViewProjectRequest, ViewProjectResponse, CommonErrorMessage>("viewProject") {
        auth {
            access = AccessRight.READ_WRITE
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

    val verifyMembership =
        call<Unit, Unit, CommonErrorMessage>("verifyMembership") {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"verify-membership"
                }
            }
        }

    val archive = call<ArchiveRequest, ArchiveResponse, CommonErrorMessage>("archive") {
        auth {
            access = AccessRight.READ_WRITE
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

    val exists = call<ExistsRequest, ExistsResponse, CommonErrorMessage>("exists") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
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
}
