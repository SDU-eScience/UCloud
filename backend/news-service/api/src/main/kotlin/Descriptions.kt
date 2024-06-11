package dk.sdu.cloud.news.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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
    private const val baseContext = "/api/news"
    init {
        description = """
            News communicates to users about new features, bug fixes and upcoming maintenance.
            
            Only administrators of UCloud can create news posts. All posts are publicly readable unless they are
            explicitly marked as `hidden`.
            
            Administrators can view hidden posts using `withHidden = true`. This flag is not usable by normal users.
        """.trimIndent()
    }

    override fun documentation() {
        useCase("create-read-update-delete", "News CRUD") {
            val user = actor("admin", "UCloud Admin")
            success(
                newPost,
                NewPostRequest(
                    "This is a news post",
                    "Short summary of the post",
                    "Et ipsam ex explicabo quis aut sit voluptates.",
                    0L,
                    "News"
                ),
                NewPostResponse,
                user
            )

            success(
                listPosts,
                ListPostsRequest(withHidden = false, page = 0, itemsPerPage = 50),
                Page(
                    1, 50, 0, listOf(
                        NewsPost(
                            4512,
                            "This is a news post",
                            "Short summary of the post",
                            "Et ipsam ex explicabo quis aut sit voluptates.",
                            "UCloud Admin",
                            0L,
                            hidden = false,
                            category = "News"
                        )
                    )
                ),
                user
            )

            success(
                updatePost,
                UpdatePostRequest(
                    4512,
                    "Updated title",
                    "Short summary of the post",
                    "Et ipsam ex explicabo quis aut sit voluptates.",
                    0L,
                    category = "News"
                ),
                UpdatePostResponse,
                user
            )

            success(
                deletePost,
                DeleteNewsPostRequest(4512),
                DeleteNewsPostResponse,
                user
            )
        }

        useCase("invisible-news", "Marking a news post as hidden") {
            val user = actor("admin", "UCloud Admin")
            success(
                newPost,
                NewPostRequest(
                    "This is a news post",
                    "Short summary of the post",
                    "Et ipsam ex explicabo quis aut sit voluptates.",
                    0L,
                    "News"
                ),
                NewPostResponse,
                user
            )

            success(
                listPosts,
                ListPostsRequest(withHidden = false, page = 0, itemsPerPage = 50),
                Page(
                    1, 50, 0, listOf(
                        NewsPost(
                            4512,
                            "This is a news post",
                            "Short summary of the post",
                            "Et ipsam ex explicabo quis aut sit voluptates.",
                            "UCloud Admin",
                            0L,
                            hidden = false,
                            category = "News"
                        )
                    )
                ),
                user
            )

            success(
                togglePostHidden,
                TogglePostHiddenRequest(4512),
                TogglePostHiddenResponse,
                user
            )

            success(
                listPosts,
                ListPostsRequest(withHidden = false, page = 0, itemsPerPage = 50),
                Page(0, 50, 0, emptyList()),
                user
            )
        }
    }

    val newPost = call("newPost", NewPostRequest.serializer(), NewPostResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Creates a new post"
        }
    }

    val updatePost = call("updatePost", UpdatePostRequest.serializer(), UpdatePostResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Updates an existing post"
        }
    }

    val deletePost = call("deletePost", DeleteNewsPostRequest.serializer(), DeleteNewsPostResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Deletes an existing post"
        }
    }

    val togglePostHidden = call("togglePostHidden", TogglePostHiddenRequest.serializer(), TogglePostHiddenResponse.serializer(), CommonErrorMessage.serializer()) {
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

            documentation {
                summary = "Swaps the visibility state of an existing post"
            }
        }

    val listCategories = call("listCategories", ListCategoriesRequest.serializer(), ListSerializer(String.serializer()), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Lists all news categories in UCloud"
        }
    }

    val listPosts = call("listPosts", ListPostsRequest.serializer(), Page.serializer(NewsPost.serializer()), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Retrieves a page of news"
        }
    }

    val listDowntimes = call("listDowntimes", ListDownTimesRequest.serializer(), Page.serializer(NewsPost.serializer()), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Retrieves a page of news related to upcoming downtime"
        }
    }

    val getPostById = call("getPostBy", GetPostByIdRequest.serializer(), GetPostByIdResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Retrieves a concrete post by ID"
        }
    }
}