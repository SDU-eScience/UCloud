@file:Suppress("TooManyFunctions")

package dk.sdu.cloud.indexing.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.stackTraceToString
import mbuhot.eskotlin.query.QueryData
import mbuhot.eskotlin.query.initQuery
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.ClearScrollRequest
import org.elasticsearch.action.search.ClearScrollResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.TermQueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

inline fun <reified T : Any> SearchResponse.paginated(
    mapper: ObjectMapper,
    paging: NormalizedPaginationRequest
): Page<T> {
    val items = hits.hits
        .filter { it.hasSource() }
        .mapNotNull {
            @Suppress("TooGenericExceptionCaught")
            try {
                mapper.readValue<T>(it.sourceAsString)
            } catch (ex: Exception) {
                ElasticQueryService.log.info(
                    "Unable to deserialize ElasticIndexedFile from source: ${it.sourceAsString}"
                )
                null
            }
        }

    return Page(
        hits.totalHits.value.toInt(),
        paging.itemsPerPage,
        paging.page,
        items
    )
}

inline fun <reified T : Any> SearchResponse.mapped(mapper: ObjectMapper): List<T> {
    return hits.hits.filter { it.hasSource() }.mapNotNull {
        @Suppress("TooGenericExceptionCaught")
        try {
            mapper.readValue<T>(it.sourceAsString)
        } catch (ex: Exception) {
            ElasticQueryService.log.info("Unable to deserialize ElasticIndexedFile from source: ${it.sourceAsString}")
            ElasticQueryService.log.info(ex.stackTraceToString())
            null
        }
    }
}

inline fun <reified T : Any> RestHighLevelClient.search(
    mapper: ObjectMapper,
    paging: NormalizedPaginationRequest,
    vararg indices: String,
    noinline builder: SearchSourceBuilder.() -> QueryBuilder
): Page<T> {
    @Suppress("SpreadOperator")
    return search(*indices) { source(paging, builder) }.paginated(mapper, paging)
}

fun RestHighLevelClient.search(vararg indices: String, builder: SearchRequest.() -> Unit): SearchResponse {
    return search(SearchRequest(indices, SearchSourceBuilder()).also(builder), RequestOptions.DEFAULT)
}

private const val SEARCH_REQUEST_SIZE = 1000

fun RestHighLevelClient.scrollThroughSearch(
    indices: List<String>,
    builder: SearchRequest.() -> Unit,
    handler: (SearchResponse) -> Unit
) {
    val request = SearchRequest(indices.toTypedArray(), SearchSourceBuilder())
        .also {
            it.source().sort("_doc").size(SEARCH_REQUEST_SIZE)
            it.scroll(TimeValue.timeValueMinutes(1))
        }
        .also(builder)

    var resp: SearchResponse = search(request, RequestOptions.DEFAULT)
    while (resp.hits.hits.isNotEmpty()) {
        handler(resp)
        resp = searchScroll(SearchScrollRequest(resp.scrollId).apply {
            scroll(TimeValue.timeValueMinutes(1))
        }, RequestOptions.DEFAULT)
    }

    clearScroll(ClearScrollRequest().apply { scrollIds = listOf(resp.scrollId) }, RequestOptions.DEFAULT)
}

inline fun <reified T : Any> RestHighLevelClient.scrollThroughSearch(
    mapper: ObjectMapper,
    indices: List<String>,
    noinline builder: SearchRequest.() -> Unit,
    noinline handler: (T) -> Unit
) {
    scrollThroughSearch(indices, builder) {
        it.mapped<T>(mapper).forEach(handler)
    }
}

private fun <T> actionListener(continuation: Continuation<T>): ActionListener<T> = object : ActionListener<T> {
    override fun onResponse(response: T) {
        continuation.resume(response)
    }

    override fun onFailure(e: Exception) {
        continuation.resumeWithException(e)
    }
}

fun SearchResponse.scroll(client: RestHighLevelClient): SearchResponse =
    client.searchScroll(SearchScrollRequest(scrollId), RequestOptions.DEFAULT)

fun SearchResponse.clearScroll(client: RestHighLevelClient): ClearScrollResponse =
    client.clearScroll(ClearScrollRequest().apply { scrollIds = listOf(scrollId) }, RequestOptions.DEFAULT)

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

/**
 * Fixes an issue in the term DSL that didn't allow non-string values
 */
class FixedTermBlock {
    /**
     * Data container for elasticsearch
     */
    class TermData(
        var name: String? = null,
        var value: Any? = null
    ) : QueryData()

    infix fun String.to(value: Any): TermData {
        return TermData(name = this, value = value)
    }

    infix fun String.to(init: TermData.() -> Unit): TermData {
        return TermData(name = this).apply(init)
    }
}

fun term(init: FixedTermBlock.() -> FixedTermBlock.TermData): TermQueryBuilder {
    val params = FixedTermBlock().init()
    return TermQueryBuilder(params.name, params.value).apply {
        initQuery(params)
    }
}
