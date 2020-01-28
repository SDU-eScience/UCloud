package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod
import dk.sdu.cloud.calls.CallDescriptionContainer


data class ListTagsRequest(
    val serverId: String
)

data class AddTagRequest(
    val serverId: String,
    val name: String
)

data class DeleteTagRequest(
    val serverId: String,
    val name: String
)

data class ListTagsResponse(
    val tags: List<String>
)

object TagDescriptions : CallDescriptionContainer("app.license.tag") {
    val baseContext = "/api/app/license/tag"

    val add = call<AddTagRequest, Unit, CommonErrorMessage>("add") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(AppLicenseDescriptions.baseContext)
                +"add"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val delete = call<DeleteTagRequest, Unit, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(AppLicenseDescriptions.baseContext)
                +"delete"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val list = call<ListTagsRequest, ListTagsResponse, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(AppLicenseDescriptions.baseContext)
                +"list"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
