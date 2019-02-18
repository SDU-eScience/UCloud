package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.bindEntireRequestFromBody
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

object ProjectDescriptions : CallDescriptionContainer("projects") {
    const val baseContext = "/api/projects/form"

    val create = call<CreateProjectFromFormRequest, CreateProjectFromFormResponse, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}

