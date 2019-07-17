package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class FindApplicationAndOptionalDependencies(
    val name: String,
    val version: String
)

data class FavoriteRequest(
    val name: String,
    val version: String
)

data class TagSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

val TagSearchRequest.tags: List<String> get() = query.split(",")

data class AppSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

data class CreateTagRequest(
    val tags: List<String>,
    val applicationName: String,
    val applicationVersion: String
)

typealias DeleteTagRequest = CreateTagRequest

object AppStore : CallDescriptionContainer("hpc.apps") {
    const val baseContext = "/api/hpc/apps/"

    val toggleFavorite = call<FavoriteRequest, Unit, CommonErrorMessage>("toggleFavorite") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"favorites"
                +boundTo(FavoriteRequest::name)
                +boundTo(FavoriteRequest::version)
            }
        }
    }

    val retrieveFavorites =
        call<PaginationRequest, Page<ApplicationSummaryWithFavorite>, CommonErrorMessage>("retrieveFavorites") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"favorites"
                }

                params {
                    +boundTo(PaginationRequest::itemsPerPage)
                    +boundTo(PaginationRequest::page)
                }
            }
        }
    val searchTags = call<TagSearchRequest, Page<ApplicationSummaryWithFavorite>, CommonErrorMessage>("searchTags") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"searchTags"
            }

            params {
                +boundTo(TagSearchRequest::query)
                +boundTo(TagSearchRequest::itemsPerPage)
                +boundTo(TagSearchRequest::page)
            }
        }
    }

    val searchApps = call<AppSearchRequest, Page<ApplicationSummaryWithFavorite>, CommonErrorMessage>("searchApps") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"search"
            }

            params {
                +boundTo(AppSearchRequest::query)
                +boundTo(AppSearchRequest::itemsPerPage)
                +boundTo(AppSearchRequest::page)
            }
        }
    }

    val findByName =
        call<FindByNameAndPagination, Page<ApplicationSummaryWithFavorite>, CommonErrorMessage>("findByName") {
            auth {
                roles = Roles.AUTHENTICATED
                access = AccessRight.READ
            }

            http {
                path {
                    using(baseContext)
                    +boundTo(FindByNameAndPagination::name)
                }

                params {
                    +boundTo(FindByNameAndPagination::itemsPerPage)
                    +boundTo(FindByNameAndPagination::page)
                }
            }
        }

    val findByNameAndVersion = call<
            FindApplicationAndOptionalDependencies,
            ApplicationWithFavorite,
            CommonErrorMessage>("findByNameAndVersion") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(FindApplicationAndOptionalDependencies::name)
                +boundTo(FindApplicationAndOptionalDependencies::version)
            }
        }
    }

    val listAll = call<PaginationRequest, Page<ApplicationSummaryWithFavorite>, CommonErrorMessage>("listAll") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }

            params {
                +boundTo(PaginationRequest::itemsPerPage)
                +boundTo(PaginationRequest::page)
            }
        }
    }

    val create = call<Unit, Unit, CommonErrorMessage>("create") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Put
            path { using(baseContext) }
            // body { //YAML Body TODO Implement support }
        }
    }

    val createTag = call<CreateTagRequest, Unit, CommonErrorMessage>("createTag") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"createTag"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeTag = call<DeleteTagRequest, Unit, CommonErrorMessage>("removeTag") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"deleteTag"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
