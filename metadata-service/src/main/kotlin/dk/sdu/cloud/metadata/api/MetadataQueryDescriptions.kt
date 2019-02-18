package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class SimpleQueryRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

object MetadataQueryDescriptions : CallDescriptionContainer("metadata") {
    private const val baseContext = "/api/metadata"

    val simpleQuery = call<SimpleQueryRequest, Page<ProjectMetadata>, CommonErrorMessage>("simpleQuery") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"search"
            }

            params {
                +boundTo(SimpleQueryRequest::query)
                +boundTo(SimpleQueryRequest::itemsPerPage)
                +boundTo(SimpleQueryRequest::page)
            }
        }
    }
}
