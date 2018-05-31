package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.storage.api.FindByPath
import io.ktor.http.HttpMethod

data class CreateProjectRequest(val fsRoot: String)
data class CreateProjectResponse(val id: String)

typealias FindByProjectId = FindByStringId

object ProjectDescriptions : RESTDescriptions(MetadataServiceDescription) {
    private const val baseContext = "/api/projects"

    val create = callDescription<CreateProjectRequest, CreateProjectResponse, CommonErrorMessage> {
        method = HttpMethod.Put
        prettyName = "projects-create"

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val findProjectByPath = callDescription<FindByPath, Project, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "projects-find-by-path"

        path {
            using(baseContext)
            +boundTo(FindByPath::path)
        }
    }
}

