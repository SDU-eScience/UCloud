package dk.sdu.cloud.app.fs.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

abstract class FileSystemCalls(val name: String) : CallDescriptionContainer("app.fs.$name") {
    val baseContext = "/api/app/fs/$name"

    val create = call<Create.Request, Create.Response, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
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
        class Request(val internalId: String, val ownerUid: Int)
        class Response()
    }

    val delete = call<Delete.Request, Delete.Response, CommonErrorMessage>("delete") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
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

    val view = call<View.Request, View.Response, CommonErrorMessage>("view") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(View.Request::id)
            }
        }
    }

    object View {
        data class Request(val id: String)
        data class Response(val size: Long)
    }
}
