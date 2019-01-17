package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.file.api.FindByPath
import io.ktor.http.HttpMethod

data class ProjectMetadataWithRightsInfo(
    val metadata: ProjectMetadata,
    val canEdit: Boolean
)

object MetadataDescriptions : RESTDescriptions("metadata") {
    const val baseContext = "/api/metadata"

    val updateProjectMetadata = callDescription<ProjectMetadataEditRequest, Unit, CommonErrorMessage> {
        name = "metadataUpdate"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val findById = callDescription<FindByProjectId, ProjectMetadataWithRightsInfo, CommonErrorMessage> {
        name = "metadataFind"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +boundTo(FindByProjectId::id)
        }
    }
}
