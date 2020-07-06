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

/**
 * A service only API for querying about a user's project membership
 */
object ProjectMembers : CallDescriptionContainer("project.members") {
    val baseContext = "/api/projects/membership"

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
}
