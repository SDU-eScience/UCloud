package dk.sdu.cloud.project.repository.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

typealias FileRights = dk.sdu.cloud.file.api.AccessRight

private fun verifyRepositoryName(name: String) {
    if (name.isEmpty() || name.isBlank()) throw RPCException("Name cannot be empty", HttpStatusCode.BadRequest)
    if (name.length !in 1..128) throw RPCException("Name too long", HttpStatusCode.BadRequest)
    if (name.contains("\n")) throw RPCException("Name cannot contain line-breaks", HttpStatusCode.BadRequest)
}

data class RepositoryCreateRequest(val name: String) {
    init {
        verifyRepositoryName(name)
    }
}

typealias RepositoryCreateResponse = Unit

data class RepositoryUpdateRequest(val oldName: String, val newName: String) {
    init {
        verifyRepositoryName(oldName)
        verifyRepositoryName(newName)
    }
}

typealias RepositoryUpdateResponse = Unit

data class RepositoryDeleteRequest(val name: String) {
    init {
        verifyRepositoryName(name)
    }
}

typealias RepositoryDeleteResponse = Unit

data class Repository(val name: String)

data class RepositoryListRequest(
    val user: String?,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias RepositoryListResponse = Page<Repository>

typealias ListFilesRequest = RepositoryListRequest
typealias ListFilesResponse = Page<StorageFile>

data class ProjectAclEntry(val group: String, val rights: Set<FileRights>)

data class UpdatePermissionsRequest(
    val repository: String,
    val newAcl: List<ProjectAclEntry>
) {
    init {
        verifyRepositoryName(repository)
    }
}

typealias UpdatePermissionsResponse = Unit

object ProjectRepository : CallDescriptionContainer("project.repositories") {
    private const val baseContext = "/api/projects/repositories"

    val create = call<RepositoryCreateRequest, RepositoryCreateResponse, CommonErrorMessage>("create") {
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

    val update = call<RepositoryUpdateRequest, RepositoryUpdateResponse, CommonErrorMessage>("update") {
        auth {
            access = AccessRight.READ_WRITE
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

    val delete = call<RepositoryDeleteRequest, RepositoryDeleteResponse, CommonErrorMessage>("delete") {
        auth {
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

    val list = call<RepositoryListRequest, RepositoryListResponse, CommonErrorMessage>("list") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(RepositoryListRequest::itemsPerPage)
                +boundTo(RepositoryListRequest::page)
                +boundTo(RepositoryListRequest::user)
            }
        }
    }

    val listFiles = call<ListFilesRequest, ListFilesResponse, CommonErrorMessage>("listFiles") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list-files"
            }

            params {
                +boundTo(ListFilesRequest::itemsPerPage)
                +boundTo(ListFilesRequest::page)
                +boundTo(ListFilesRequest::user)
            }
        }
    }

    val updatePermissions =
        call<UpdatePermissionsRequest, UpdatePermissionsResponse, CommonErrorMessage>("updatePermissions") {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"update-permissions"
                }

                body { bindEntireRequestFromBody() }
            }
        }
}
