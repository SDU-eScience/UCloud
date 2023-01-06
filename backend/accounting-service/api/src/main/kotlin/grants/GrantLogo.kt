package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class UploadLogoRequest(
    val projectId: String,
)

typealias UploadLogoResponse = Unit

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class RetrieveLogoRequest(
    val projectId: String
)

typealias RetrieveLogoResponse = Unit

object ProjectLogo : CallDescriptionContainer("grant.logo") {
    val baseContext = "/api/grant/logo"

    init {
        title = "Project grant logos"
        description = """
Logos help the end users navigate the list of potential grant givers by giving them a visual cue.

${ApiConventions.nonConformingApiWarning}
""".trimIndent()
    }

    val uploadLogo = call(
        "upload",
        UploadLogoRequest.serializer(),
        UploadLogoResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"upload"
            }

            headers {
                +boundTo("Upload-Name", UploadLogoRequest::projectId)
            }
        }

        documentation {
            summary = "Uploads a logo for a project, which is enabled"
            description = "Only project administrators of the project can upload a logo"
        }
    }

    val retrieveLogo = call(
        "retrieveLogo",
        RetrieveLogoRequest.serializer(),
        RetrieveLogoResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(
            baseContext,
            roles = Roles.PUBLIC
        )

        documentation {
            summary = "Fetches a logo for a project"
        }
    }
}
