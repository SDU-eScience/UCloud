package dk.sdu.cloud.news.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class NewsPost(
    val id: Long,
    val title: String,
    val subtitle: String,
    val body: String,
    val postedBy: String,
    val showFrom: Long,
    val hideFrom: Long?,
    val hidden: Boolean,
    val category: String
)

data class NewPostRequest(
    val title: String,
    val subtitle: String,
    val body: String,
    val showFrom: Long,
    val category: String,
    val hideFrom: Long?
)
typealias NewPostResponse = Unit

typealias ListCategoriesRequest = Unit
typealias ListCategoriesResponse = List<String>

data class ListPostsRequest(
    val filter: String?,
    val withHidden: Boolean,
    override val page: Int,
    override val itemsPerPage: Int
) : WithPaginationRequest
typealias ListPostsResponse = Page<NewsPost>

typealias ListDownTimesRequest = Unit
typealias ListDownTimesResponse = Page<NewsPost>

data class TogglePostHiddenRequest(val id: Long)
typealias TogglePostHiddenResponse = Unit

data class GetPostByIdRequest(val id: Long)
typealias GetPostByIdResponse = NewsPost

object News : CallDescriptionContainer("news") {
    val baseContext = "/api/news"

    val newPost = call<NewPostRequest, NewPostResponse, CommonErrorMessage>("newPost") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
                +"post"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val togglePostHidden = call<TogglePostHiddenRequest, TogglePostHiddenResponse, CommonErrorMessage>("togglePostHidden") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"toggleHidden"

                body { bindEntireRequestFromBody() }
            }
        }
    }

    val listCategories = call<ListCategoriesRequest, ListCategoriesResponse, CommonErrorMessage>("listCategories") {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listCategories"
            }
        }
    }

    val listPosts = call<ListPostsRequest, ListPostsResponse, CommonErrorMessage>("listPosts") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list"
            }

            params {
                +boundTo(ListPostsRequest::filter)
                +boundTo(ListPostsRequest::withHidden)
                +boundTo(ListPostsRequest::itemsPerPage)
                +boundTo(ListPostsRequest::page)
            }
        }
    }

    val listDowntimes = call<ListDownTimesRequest, ListDownTimesResponse, CommonErrorMessage>("listDowntimes") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listDowntimes"
            }
        }
    }

    val getPostById = call<GetPostByIdRequest, GetPostByIdResponse, CommonErrorMessage>("getPostBy") {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"byId"
            }

            params {
                +boundTo(GetPostByIdRequest::id)
            }
        }
    }
}