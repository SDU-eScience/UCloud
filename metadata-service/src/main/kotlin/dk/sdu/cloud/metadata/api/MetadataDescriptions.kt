package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.storage.api.FindByPath
import io.netty.handler.codec.http.HttpMethod

data class ProjectMetadataWithRightsInfo(
    val metadata: ProjectMetadata,
    val canEdit: Boolean
)

object MetadataDescriptions : RESTDescriptions(MetadataServiceDescription) {
    private const val baseContext = "/api/metadata"

    val updateProjectMetadata = callDescription<ProjectMetadataEditRequest, Unit, CommonErrorMessage> {
        method = HttpMethod.POST
        prettyName = "metadata-update"

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val findById = callDescription<FindByProjectId, ProjectMetadataWithRightsInfo, CommonErrorMessage> {
        method = HttpMethod.GET
        prettyName = "metadata-find"

        path {
            using(baseContext)
            +boundTo(FindByProjectId::id)
        }
    }

    val findByPath = callDescription<FindByPath, ProjectMetadataWithRightsInfo, CommonErrorMessage> {
        method = HttpMethod.GET
        prettyName = "metadata-find-by-path"

        path {
            using(baseContext)
            +"by-path"
        }

        params {
            +boundTo(FindByPath::path)
        }
    }
}