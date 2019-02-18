package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class ProjectMetadataWithRightsInfo(
    val metadata: ProjectMetadata,
    val canEdit: Boolean
)

object MetadataDescriptions : CallDescriptionContainer("metadata") {
    const val baseContext = "/api/metadata"

    val updateProjectMetadata = call<ProjectMetadataEditRequest, Unit, CommonErrorMessage>("updateProjectMetadata") {
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

    val findById = call<FindByProjectId, ProjectMetadataWithRightsInfo, CommonErrorMessage>("findById") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(FindByProjectId::id)
            }
        }
    }
}
