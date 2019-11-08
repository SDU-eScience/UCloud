package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val server_id: String)
data class LicenseServerResponse(val address: String)
data class UpdatePermissionRequest(val entityId: String, val entityType: EntityType, val permission: AccessRight)
data class UpdatePermissionResponse(val echo: String)

object AppLicenseDescriptions : CallDescriptionContainer("app.license") {
    val baseContext = "/api/app/license"

    val permission = call<LicenseServerRequest, LicenseServerResponse, CommonErrorMessage>("get") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val update = call<UpdatePermissionRequest, UpdatePermissionResponse, CommonErrorMessage>("updatePermission") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                + "update"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
