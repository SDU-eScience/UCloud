package dk.sdu.cloud.activity.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.ScrollResult
import dk.sdu.cloud.service.WithScrollRequest
import dk.sdu.cloud.service.WithScrollResult
import io.ktor.http.HttpMethod

typealias ActivityDescriptions = Activity

object Activity : CallDescriptionContainer("activity") {
    val baseContext = "/api/activity"

    val listByFileId = call<ListActivityByIdRequest, ListActivityByIdResponse, CommonErrorMessage>("listByFileId") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"by-file-id"
            }

            params {
                +boundTo(ListActivityByIdRequest::itemsPerPage)
                +boundTo(ListActivityByIdRequest::page)
                +boundTo(ListActivityByIdRequest::id)
            }
        }
    }

    val listByPath = call<ListActivityByPathRequest, ListActivityByPathResponse, CommonErrorMessage>("listByPath") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"by-path"
            }

            params {
                +boundTo(ListActivityByPathRequest::itemsPerPage)
                +boundTo(ListActivityByPathRequest::page)
                +boundTo(ListActivityByPathRequest::path)
            }
        }
    }

    val listByUser = call<ListActivityByUserRequest, ListActivityByUserResponse, CommonErrorMessage>("listByUser") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListActivityByUserRequest::itemsPerPage)
                +boundTo(ListActivityByUserRequest::page)
            }
        }
    }

    val browseByUser = call<BrowseByUser.Request, BrowseByUser.Response, CommonErrorMessage>("browseByUser") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"browse"
                +"user"
            }

            params {
                +boundTo(BrowseByUser.Request::user)
                +boundTo(BrowseByUser.Request::offset)
                +boundTo(BrowseByUser.Request::scrollSize)
            }
        }
    }

    object BrowseByUser {
        data class Request(
            val user: String,
            override val offset: Long?,
            override val scrollSize: Int?
        ) : WithScrollRequest<Long>

        data class Response(
            override val endOfScroll: Boolean,
            override val items: List<ActivityEvent>,
            override val nextOffset: Long
        ) : WithScrollResult<ActivityEvent, Long>
    }
}
