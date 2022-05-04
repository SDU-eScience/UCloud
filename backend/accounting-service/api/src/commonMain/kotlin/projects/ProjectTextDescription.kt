package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants.isEnabled
import dk.sdu.cloud.grant.api.Grants.setEnabledStatus
import kotlinx.serialization.Serializable


@Serializable
data class UploadDescriptionRequest(
    val projectId: String,
    val description: String
) {
    init {
        if (description.length > 240) {
            throw RPCException("Description must not exceed 240 characters", HttpStatusCode.BadRequest)
        }
    }
}
typealias UploadDescriptionResponse = Unit

@Serializable
data class RetrieveDescriptionRequest(
    val projectId: String
)
@Serializable
data class RetrieveDescriptionResponse(
    val description: String
)
object ProjectTextDescription : CallDescriptionContainer("project_text_description") {
    val baseContext = "/api/projects/description"

    init {
        title = "Project Text Description"
        description = """
            A project can upload a description that is shown to the users when they are looking for a grant giver.
            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    /**
     * Uploads a description of a project which is enabled
     *
     * Only project administrators of the project can upload a description
     *
     * @see setEnabledStatus
     * @see isEnabled
     */
    val uploadDescription =
        call<BulkRequest<UploadDescriptionRequest>, UploadDescriptionResponse, CommonErrorMessage>("uploadDescription") {
            httpUpdate(
                baseContext,
                "upload"
            )

            documentation {
                summary = "Uploads descriptions of projects"
            }
        }

    val retrieveDescription =
        call<RetrieveDescriptionRequest, RetrieveDescriptionResponse, CommonErrorMessage>("retrieveDescription") {
            httpRetrieve(
                baseContext,
                roles = Roles.PUBLIC
            )

            documentation {
                summary = "Fetches a description of a project"
            }
        }
}

