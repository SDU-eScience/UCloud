package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod

data class WorkspaceMount(val source: String, val destination: String, val readOnly: Boolean = true)

typealias WorkspaceDescriptions = Workspaces
object Workspaces : CallDescriptionContainer("files.workspace") {
    const val baseContext = "/api/files/workspaces"

    object Create {
        data class Request(
            val username: String,
            val mounts: List<WorkspaceMount>,
            val allowFailures: Boolean,
            val createSymbolicLinkAt: String
        )
        data class Response(val workspaceId: String, val failures: List<WorkspaceMount>)
    }

    val create = call<Create.Request, Create.Response, CommonErrorMessage>("create") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path { using(baseContext) }
            body { bindEntireRequestFromBody() }
        }
    }

    object Transfer {
        data class Request(
            val username: String,
            val workspaceId: String,
            val transferGlobs: List<String>,
            val destination: String,
            val deleteWorkspace: Boolean,
            val replaceExisting: Boolean = true
        )

        data class Response(val filesTransferred: List<String>)
    }

    val transfer = call<Transfer.Request, Transfer.Response, CommonErrorMessage>("transfer") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            body { bindEntireRequestFromBody() }
            path {
                using(baseContext)
                +"transfer"
            }
        }
    }

    object Delete {
        data class Request(val workspaceId: String)
        object Response
    }

    val delete = call<Delete.Request, Delete.Response, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete
            body { bindEntireRequestFromBody() }
            path { using(baseContext) }
        }
    }
}
