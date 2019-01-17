package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod


data class CreateProjectFromFormRequest(
    override val title: String? = null,
    override val description: String? = null,
    override val license: String? = null,
    override val keywords: List<String>? = null,
    override val contributors: List<Creator>? = null,
    override val references: List<String>? = null,
    override val grants: List<Grant>? = null,
    override val subjects: List<Subject>? = null,
    override val notes: String? = null
) : UserEditableProjectMetadata

data class CreateProjectFromFormResponse(val id: String)

typealias FindByProjectId = FindByStringId

object ProjectDescriptions : RESTDescriptions("projects") {
    const val baseContext = "/api/projects/form"

    val create = callDescription<CreateProjectFromFormRequest, CreateProjectFromFormResponse, CommonErrorMessage> {
        name = "projectsCreate"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }
}

