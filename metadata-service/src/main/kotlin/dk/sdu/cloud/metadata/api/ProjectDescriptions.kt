package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.file.api.FindByPath
import io.ktor.http.HttpMethod

data class CreateProjectRequest(val fsRoot: String)
data class CreateProjectResponse(val id: Long)

typealias FindByProjectId = FindByLongId

object ProjectDescriptions : RESTDescriptions("projects") {
    const val baseContext = "/api/projects"

    val create = callDescription<CreateProjectRequest, CreateProjectResponse, CommonErrorMessage> {
        name = "projectsCreate"
        method = HttpMethod.Put

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val findProjectByPath = callDescription<FindByPath, Project, CommonErrorMessage> {
        name = "projectsFindByPath"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(FindByPath::path)
        }
    }
}

