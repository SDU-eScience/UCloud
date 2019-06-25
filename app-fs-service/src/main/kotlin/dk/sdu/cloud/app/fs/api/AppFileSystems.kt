package dk.sdu.cloud.app.fs.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

object AppFileSystems : CallDescriptionContainer("app.fs") {
    val baseContext = "/api/app/fs"

    val create = call<Create.Request, Create.Response, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    object Create {
        data class Request(val backend: String?, val title: String)
        data class Response(val id: String)
    }

    val delete = call<Delete.Request, Delete.Response, CommonErrorMessage>("delete") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +boundTo(Delete.Request::id)
            }
        }
    }

    object Delete {
        data class Request(val id: String)
        class Response()
    }

    val list = call<List.Request, Page<SharedFileSystem>, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(List.Request::itemsPerPage)
                +boundTo(List.Request::page)
            }
        }
    }

    object List {
        data class Request(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
    }

    val view = call<View.Request, View.Response, CommonErrorMessage>("view") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(View.Request::id)
            }

            params {
                +boundTo(View.Request::calculateSize)
            }
        }
    }

    object View {
        data class Request(val id: String, val calculateSize: Boolean?)
        data class Response(val fileSystem: SharedFileSystem, val size: Long)
    }
}
