package dk.sdu.cloud.share.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Page
import dk.sdu.cloud.WithPaginationRequest
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageFile
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

private typealias AuthAccessRight = dk.sdu.cloud.AccessRight

@TSTopLevel
object Shares : CallDescriptionContainer("shares") {
    const val baseContext = "/api/shares"

    val list = call<List.Request, Page<SharesByPath>, CommonErrorMessage>("list") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(List.Request::sharedByMe)
                +boundTo(List.Request::itemsPerPage)
                +boundTo(List.Request::page)
            }
        }
    }

    object List {
        @Serializable
        data class Request(
            val sharedByMe: Boolean,
            override val itemsPerPage: Int? = null,
            override val page: Int? = null
        ) : WithPaginationRequest
    }

    val findByPath = call<FindByPath.Request, SharesByPath, CommonErrorMessage>("findByPath") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"byPath"
            }

            params {
                +boundTo(FindByPath.Request::path)
            }
        }
    }

    object FindByPath {
        @Serializable
        data class Request(
            val path: String
        )
    }

    val create = call<Create.Request, Unit, CommonErrorMessage>("create") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    object Create {
        @Serializable
        data class Request(
            val sharedWith: String,
            val path: String,
            val rights: Set<AccessRight>
        ) {
            init {
                if (path.startsWith("/projects/")) {
                    throw RPCException("Shares between projects and users are not allowed", HttpStatusCode.BadRequest)
                }
            }
        }
    }

    val update = call<Update.Request, Unit, CommonErrorMessage>("update") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    object Update {
        @Serializable
        data class Request(
            val path: String,
            val sharedWith: String,
            val rights: Set<AccessRight>
        )
    }

    val revoke = call<Revoke.Request, Unit, CommonErrorMessage>("revoke") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"revoke"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    object Revoke {
        @Serializable
        data class Request(
            val path: String,
            val sharedWith: String
        )
    }

    val accept = call<Accept.Request, Unit, CommonErrorMessage>("accept") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"accept"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    object Accept {
        @Serializable
        data class Request(val path: String)
    }

    val listFiles = call<ListFiles.Request, Page<StorageFile>, CommonErrorMessage>("listFiles") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list-files"
            }

            params {
                +boundTo(ListFiles.Request::itemsPerPage)
                +boundTo(ListFiles.Request::page)
            }
        }
    }

    object ListFiles {
        @Serializable
        data class Request(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
    }
}
