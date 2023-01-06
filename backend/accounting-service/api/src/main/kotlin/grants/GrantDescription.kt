package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
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
@UCloudApiInternal(InternalLevel.STABLE)
data class RetrieveDescriptionRequest(
    val projectId: String
)

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class RetrieveDescriptionResponse(
    val description: String
)
object GrantDescription : CallDescriptionContainer("grant.description") {
    val baseContext = "/api/grant/description"

    init {
        title = "Grant description"
        description = """
A project can upload a description that is shown to the users when they are looking for a grant giver.
${ApiConventions.nonConformingApiWarning}
""".trimIndent()
    }

    val uploadDescription =
        call(
            "uploadDescription",
            BulkRequest.serializer(UploadDescriptionRequest.serializer()),
            UploadDescriptionResponse.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpUpdate(
                baseContext,
                "upload"
            )

            documentation {
                summary = "Uploads descriptions of projects"
            }
        }

    val retrieveDescription =
        call(
            "retrieveDescription",
            RetrieveDescriptionRequest.serializer(),
            RetrieveDescriptionResponse.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpRetrieve(
                baseContext,
                roles = Roles.PUBLIC
            )

            documentation {
                summary = "Fetches a description of a project"
            }
        }
}

