package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable


@Serializable
data class UploadLogoRequest(
    val projectId: String,
)

typealias UploadLogoResponse = Unit

@Serializable
data class RetrieveLogoRequest(
    val projectId: String
)

typealias RetrieveLogoResponse = Unit

object ProjectLogo : CallDescriptionContainer("projects_logo") {
    val baseContext = "/api/projects/logo"

    init {
        title = "Project Logos"
        description = """
            Project logos are intended as a help for the end users to navigate the list of potential grant givers by 
            giving them a visual cue.

            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val uploadLogo = call<UploadLogoRequest, UploadLogoResponse, CommonErrorMessage>("uploadLogo") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(Grants.baseContext)
                +"upload"
            }

            headers {
                +boundTo("Upload-Name", UploadLogoRequest::projectId)
            }

            /*
        body {
            bindToSubProperty(UploadLogoRequest::data)
        }
         */
        }

        documentation {
            summary = "Uploads a logo for a project, which is enabled"
            description = "Only project administrators of the project can upload a logo"
        }
    }

    val retrieveLogo = call<RetrieveLogoRequest, RetrieveLogoResponse, CommonErrorMessage>("retrieveLogo") {
        httpRetrieve(
            baseContext,
            roles = Roles.PUBLIC
        )

        documentation {
            summary = "Fetches a logo for a project"
        }
    }
}
