package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.storage.api.WithPagination
import io.ktor.http.HttpMethod

data class SimpleQueryRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPagination

object MetadataQueryDescriptions : RESTDescriptions("metadata") {
    private const val baseContext = "/api/metadata"

    val simpleQuery = callDescription<SimpleQueryRequest, Page<ProjectMetadata>, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "metadata-simple-query"

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