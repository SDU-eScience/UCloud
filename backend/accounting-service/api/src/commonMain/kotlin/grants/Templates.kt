package dk.sdu.cloud.accounting.api.grants

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable

@Serializable
data class UploadTemplatesRequest(
    val form: GrantApplication.Form
)

typealias UploadTemplatesResponse = Unit

@Serializable
data class RetrieveTemplatesRequest(val projectId: String)
typealias RetrieveTemplatesResponse = UploadTemplatesRequest


object GrantTemplates : CallDescriptionContainer("grant_template") {
    val baseContext = "/api/grant/templates"

    init {
        title = "Grant Template"
        description = """
            Grant Templates are templates that are used on each grant. 
            This usually contains questions from the grant giver to the grant receiver, that are needed in the 
            process of approving the grant and are common for each application. 
            E.g "Give a small description of the project you are applying for", "If you need GPU please write why" etc.

            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val uploadTemplates = call<UploadTemplatesRequest, UploadTemplatesResponse, CommonErrorMessage>("uploadTemplates") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"upload-templates"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Uploads templates used for new grant Applications"
            description = "Only project administrators of the project can upload new templates. The project needs to be " +
                "enabled."
        }
    }

    val retrieveTemplates = call<RetrieveTemplatesRequest, RetrieveTemplatesResponse, CommonErrorMessage>("retrieveTemplates") {
        httpRetrieve(
            baseContext
        )

        documentation {
            summary = "Reads the templates for a new grant [Application]"
            description = """
                User interfaces should display the relevant template, based on who will be the 
                [Application.grantRecipient].
            """.trimIndent()
        }
    }
}
