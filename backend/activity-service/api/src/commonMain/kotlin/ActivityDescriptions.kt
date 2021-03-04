package dk.sdu.cloud.activity.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.WithScrollRequest
import dk.sdu.cloud.service.WithScrollResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

typealias ActivityDescriptions = Activity

@TSTopLevel
object Activity : CallDescriptionContainer("activity") {
    val baseContext = "/api/activity"

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

    val activityFeed = call<BrowseByUser.Request, BrowseByUser.Response, CommonErrorMessage>("activityFeed") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"browse"
            }

            params {
                +boundTo(BrowseByUser.Request::user)
                +boundTo(BrowseByUser.Request::offset)
                +boundTo(BrowseByUser.Request::scrollSize)
                +boundTo(BrowseByUser.Request::type)
                +boundTo(BrowseByUser.Request::minTimestamp)
                +boundTo(BrowseByUser.Request::maxTimestamp)
            }
        }
    }

    object BrowseByUser {
        @Serializable
        data class Request(
            override val user: String? = null,
            override val type: ActivityEventType? = null,
            override val minTimestamp: Long? = null,
            override val maxTimestamp: Long? = null,
            override val offset: Int? = null,
            override val scrollSize: Int? = null,
        ) : WithScrollRequest<Int>, ActivityFilter

        @Serializable
        data class Response(
            override val endOfScroll: Boolean,
            override val items: List<ActivityForFrontend>,
            override val nextOffset: Int
        ) : WithScrollResult<ActivityForFrontend, Int>
    }
}

interface ActivityFilter {
    val user: String?
    val type: ActivityEventType?
    val minTimestamp: Long?
    val maxTimestamp: Long?
}


