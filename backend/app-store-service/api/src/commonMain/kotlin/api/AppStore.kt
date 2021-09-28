package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val title: String
)
typealias ProjectGroup = Project

@Serializable
data class AccessEntity(
    val user: String? = null,
    val project: String? = null,
    val group: String? = null,
) {
    init {
        require(!user.isNullOrBlank() || (!project.isNullOrBlank() && !group.isNullOrBlank())) { "No access entity defined" }
    }
}

@Serializable
data class DetailedAccessEntity(
    val user: String? = null,
    val project: Project? = null,
    val group: ProjectGroup? = null,
) {
    init {
        require(!user.isNullOrBlank() || (project != null && group != null)) { "No access entity defined" }
    }
}

@Serializable
data class EntityWithPermission(
    val entity: AccessEntity,
    val permission: ApplicationAccessRight
)

@Serializable
data class DetailedEntityWithPermission(
    val entity: DetailedAccessEntity,
    val permission: ApplicationAccessRight
)

@Serializable
data class FindApplicationAndOptionalDependencies(
    val appName: String,
    val appVersion: String
)

@Serializable
data class HasPermissionRequest(
    val appName: String,
    val appVersion: String,
    val permission: Set<ApplicationAccessRight>
)

@Serializable
data class UpdateAclRequest(
    val applicationName: String,
    val changes: List<ACLEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

@Serializable
data class IsPublicRequest(
    val applications: List<NameAndVersion>
)

@Serializable
data class IsPublicResponse(
    val public: Map<NameAndVersion, Boolean>
)


@Serializable
data class ListAclRequest(
    val appName: String
)

@Serializable
data class FavoriteRequest(
    val appName: String,
    val appVersion: String
)

@Serializable
data class ACLEntryRequest(
    val entity: AccessEntity,
    val rights: ApplicationAccessRight,
    val revoke: Boolean = false
)

@Serializable
data class SetPublicRequest(
    val appName: String,
    val appVersion: String,
    val public: Boolean
)

@Serializable
data class TagSearchRequest(
    val query: String,
    val excludeTools: String? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

val TagSearchRequest.tags: List<String> get() = query.split(",")

@Serializable
data class AppSearchRequest(
    val query: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

@Serializable
data class CreateTagsRequest(
    val tags: List<String>,
    val applicationName: String
)

typealias DeleteTagsRequest = CreateTagsRequest

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class UploadApplicationLogoRequest(
    val name: String,
)

@Serializable
data class AdvancedSearchRequest(
    val query: String? = null,
    val tags: List<String>? = null,
    val showAllVersions: Boolean,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest


@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class ClearLogoRequest(val name: String)
typealias ClearLogoResponse = Unit

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class FetchLogoRequest(val name: String)
typealias FetchLogoResponse = Unit

typealias UploadApplicationLogoResponse = Unit

@Serializable
data class FindLatestByToolRequest(
    val tool: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
typealias FindLatestByToolResponse = Page<Application>

@Serializable
data class DeleteAppRequest(val appName: String, val appVersion: String)
typealias DeleteAppResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
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
            }

            body { bindEntireRequestFromBody() }
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
                +boundTo(TagSearchRequest::excludeTools)
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
                    +"byName"
                }

                params {
                    +boundTo(FindByNameAndPagination::appName)
                    +boundTo(FindByNameAndPagination::itemsPerPage)
                    +boundTo(FindByNameAndPagination::page)
                }
            }
        }

    val isPublic =
        call<IsPublicRequest, IsPublicResponse, CommonErrorMessage>("isPublic") {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"isPublic"
                }

                body {
                    bindEntireRequestFromBody()

                }
            }
        }

    val setPublic = call<SetPublicRequest, Unit, CommonErrorMessage>("setPublic")  {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"setPublic"
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    val advancedSearch = call<AdvancedSearchRequest, Page<ApplicationSummaryWithFavorite>,CommonErrorMessage>("advancedSearch") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"advancedSearch"
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    val findByNameAndVersion = call<
            FindApplicationAndOptionalDependencies,
            ApplicationWithFavoriteAndTags,
            CommonErrorMessage>("findByNameAndVersion") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byNameAndVersion"
            }

            params {
                +boundTo(FindApplicationAndOptionalDependencies::appName)
                +boundTo(FindApplicationAndOptionalDependencies::appVersion)
            }
        }
    }

    val hasPermission = call<
            HasPermissionRequest,
            Boolean,
            CommonErrorMessage>("hasPermission") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"permission"
            }

            params {
                +boundTo(HasPermissionRequest::appName)
                +boundTo(HasPermissionRequest::appVersion)
                +boundTo(HasPermissionRequest::permission)
            }
        }
    }

    val listAcl = call<
            ListAclRequest,
            List<DetailedEntityWithPermission>,
            CommonErrorMessage>("listAcl") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"list-acl"
            }

            params {
                +boundTo(ListAclRequest::appName)
            }
        }
    }

    val updateAcl = call<UpdateAclRequest, Unit, CommonErrorMessage>("updateAcl") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"updateAcl"
            }

            body { bindEntireRequestFromBody() }
        }
    }



    val findBySupportedFileExtension =
        call<FindBySupportedFileExtension, PageV2<ApplicationWithExtension>, CommonErrorMessage>("findBySupportedFileExtension") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    + "bySupportedFileExtension"
                }

                body {
                    bindEntireRequestFromBody()
                }
            }
        }


    val findLatestByTool = call<FindLatestByToolRequest, FindLatestByToolResponse, CommonErrorMessage>(
        "findLatestByTool"
    ) {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"byTool"
            }

            params {
                +boundTo(FindLatestByToolRequest::tool)
                +boundTo(FindLatestByToolRequest::itemsPerPage)
                +boundTo(FindLatestByToolRequest::page)
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
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Put
            path { using(baseContext) }
            body { bindEntireRequestFromBody() }
        }
    }

    val delete = call<DeleteAppRequest, DeleteAppResponse, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.ADMIN
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val createTag = call<CreateTagsRequest, Unit, CommonErrorMessage>("createTag") {
        auth {
            roles = Roles.PRIVILEGED
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

    val removeTag = call<DeleteTagsRequest, Unit, CommonErrorMessage>("removeTag") {
        auth {
            roles = Roles.PRIVILEGED
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

    val uploadLogo =
        call<UploadApplicationLogoRequest, UploadApplicationLogoResponse, CommonErrorMessage>("uploadLogo") {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"uploadLogo"
                }

                headers {
                    +boundTo("Upload-Name", UploadApplicationLogoRequest::name)
                }

                /*
                body {
                    bindToSubProperty(UploadApplicationLogoRequest::data)
                }
                 */
            }
        }

    val clearLogo =
        call<ClearLogoRequest, ClearLogoResponse, CommonErrorMessage>("clearLogo") {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Delete

                path {
                    using(baseContext)
                    +"clearLogo"
                }

                body { bindEntireRequestFromBody() }
            }
        }


    val fetchLogo = call<FetchLogoRequest, FetchLogoResponse, CommonErrorMessage>("fetchLogo") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"logo"
            }

            params {
                +boundTo(FetchLogoRequest::name)
            }
        }
    }
}
