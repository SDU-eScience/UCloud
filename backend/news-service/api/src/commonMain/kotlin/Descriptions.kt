package dk.sdu.cloud.news.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class NewsPost(
    val id: Long,
    val title: String,
    val subtitle: String,
    val body: String,
    val postedBy: String,
    val showFrom: Long,
    val hideFrom: Long? = null,
    val hidden: Boolean,
    val category: String
)

@Serializable
data class NewPostRequest(
    val title: String,
    val subtitle: String,
    val body: String,
    val showFrom: Long,
    val category: String,
    val hideFrom: Long? = null,
)
typealias NewPostResponse = Unit

typealias ListCategoriesRequest = Unit
typealias ListCategoriesResponse = List<String>

@Serializable
data class ListPostsRequest(
    val filter: String? = null,
    val withHidden: Boolean,
    override val page: Int,
    override val itemsPerPage: Int
) : WithPaginationRequest
typealias ListPostsResponse = Page<NewsPost>

typealias ListDownTimesRequest = Unit
typealias ListDownTimesResponse = Page<NewsPost>

@Serializable
data class TogglePostHiddenRequest(val id: Long)
typealias TogglePostHiddenResponse = Unit

@Serializable
data class GetPostByIdRequest(val id: Long)
typealias GetPostByIdResponse = NewsPost

@Serializable
data class UpdatePostRequest(
    val id: Long,
    val title: String,
    val subtitle: String,
    val body: String,
    val showFrom: Long,
    val hideFrom: Long? = null,
    val category: String
)
typealias UpdatePostResponse = Unit

@Serializable
data class DeleteNewsPostRequest(val id: Long)
typealias DeleteNewsPostResponse = Unit;

@TSTopLevel
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

    val updatePost = call<UpdatePostRequest, UpdatePostResponse, CommonErrorMessage>("updatePost") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val deletePost = call<DeleteNewsPostRequest, DeleteNewsPostResponse, CommonErrorMessage>("deletePost") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"delete"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val togglePostHidden =
        call<TogglePostHiddenRequest, TogglePostHiddenResponse, CommonErrorMessage>("togglePostHidden") {
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