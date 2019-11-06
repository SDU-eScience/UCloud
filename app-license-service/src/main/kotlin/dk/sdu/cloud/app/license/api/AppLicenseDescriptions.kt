package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class PermissionRequest(val server_name: String, val server_version: String)
data class PermissionResponse(val echo: String)



object AppLicenseDescriptions : CallDescriptionContainer("app.license") {
    val baseContext = "/api/app/license"

    val permission = call<PermissionRequest, PermissionResponse, CommonErrorMessage>("permission") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"permission"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val list = call<ListServersRequest, ListServersResponse, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"list"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
