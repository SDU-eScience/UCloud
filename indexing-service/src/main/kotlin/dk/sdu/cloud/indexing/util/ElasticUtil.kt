package dk.sdu.cloud.indexing.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder

inline fun <reified T : Any> SearchResponse.paginated(
    mapper: ObjectMapper,
    paging: NormalizedPaginationRequest
): Page<T> {
    val items = hits.hits
        .filter { it.hasSource() }
        .mapNotNull {
            try {
                mapper.readValue<T>(it.sourceAsString)
            } catch (ex: Exception) {
                ElasticQueryService.log.info("Unable to deserialize IndexedFile from source: ${it.sourceAsString}")
                null
            }
        }

    return Page(
        hits.totalHits.toInt(),
        paging.itemsPerPage,
        paging.page,
        items
    )
}

inline fun <reified T : Any> RestHighLevelClient.search(
    mapper: ObjectMapper,
    paging: NormalizedPaginationRequest,
    vararg indices: String,
    noinline builder: SearchSourceBuilder.() -> QueryBuilder
): Page<T> {
    return search(*indices) { source(paging, builder) }.paginated(mapper, paging)
}

fun RestHighLevelClient.search(vararg indices: String, builder: SearchRequest.() -> Unit): SearchResponse {
    return search(SearchRequest(*indices).also(builder))
}

fun SearchRequest.source(
    paging: NormalizedPaginationRequest? = null,
    builder: SearchSourceBuilder.() -> QueryBuilder
) {
    val sourceBuilder = SearchSourceBuilder()
    val query = sourceBuilder.builder()
    if (paging != null) sourceBuilder.paginated(paging)
    sourceBuilder.query(query)
    source(sourceBuilder)
}

fun SearchSourceBuilder.paginated(paging: NormalizedPaginationRequest) {
    from(paging.itemsPerPage * paging.page)
    size(paging.itemsPerPage)
}