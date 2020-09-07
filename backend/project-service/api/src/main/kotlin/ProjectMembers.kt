package dk.sdu.cloud.project.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class UserStatusRequest(
    val username: String?
)

data class UserStatusInProject(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val parent: String?
)

data class UserStatusResponse(
    val membership: List<UserStatusInProject>,
    val groups: List<UserGroupSummary>
)

data class SearchRequest(
    val query: String,
    val notInGroup: String?,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias SearchResponse = Page<ProjectMember>

typealias CountRequest = Unit
typealias CountResponse = Long

data class LookupAdminsRequest(val projectId: String)
data class LookupAdminsResponse(
    val admins: List<ProjectMember>
)

data class LookupAdminsBulkRequest(val projectId: List<String>)
data class LookupAdminsBulkResponse(
    val admins: List<Pair<String, List<ProjectMember>>>
)

object ProjectMembers : CallDescriptionContainer("project.members") {
    val baseContext = "/api/projects/membership"

    /**
     * An endpoint for retrieving the complete project status of a specific user.
     *
     * UCloud users in [Roles.PRIVILEGED] can set [UserStatusRequest.username] otherwise the username of the caller
     * will be used.
     *
     * The returned information will contain a complete status of all groups and project memberships. This endpoint
     * is mostly intended for services to perform permission checks.
     */
    val userStatus = call<UserStatusRequest, UserStatusResponse, CommonErrorMessage>("userStatus") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path { using(baseContext) }
            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Searches in members of a project.
     *
     * If [SearchRequest.notInGroup] is specified then only members which are not in the group specified will be
     * returned. Otherwise all members of the project will be used as the search space.
     *
     * The [SearchRequest.query] will be used to search in the usernames of project members.
     */
    val search = call<SearchRequest, SearchResponse, CommonErrorMessage>("search") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"search"
            }

            params {
                +boundTo(SearchRequest::query)
                +boundTo(SearchRequest::itemsPerPage)
                +boundTo(SearchRequest::page)
                +boundTo(SearchRequest::notInGroup)
            }
        }
    }

    /**
     * Returns the number of members in a project.
     *
     * Only project administrators can use this endpoint.
     */
    val count = call<CountRequest, CountResponse, CommonErrorMessage>("count") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"count"
            }
        }
    }

    /**
     * Returns a complete list of all project administrators in a project.
     *
     * This endpoint can only be used by [Roles.PRIVILEGED]. It is intended for services to consume when they need to
     * communicate with administrators of a project.
     */
    val lookupAdmins = call<LookupAdminsRequest, LookupAdminsResponse, CommonErrorMessage>("lookupAdmins") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookup-admins"
            }

            params {
                +boundTo(LookupAdminsRequest::projectId)
            }
        }
    }

    val lookupAdminsBulk = call<LookupAdminsBulkRequest, LookupAdminsBulkResponse, CommonErrorMessage>("lookupAdminsBulk") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"lookup-admins"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
