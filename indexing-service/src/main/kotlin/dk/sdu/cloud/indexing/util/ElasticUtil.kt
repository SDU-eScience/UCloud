package dk.sdu.cloud.indexing.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import mbuhot.eskotlin.query.QueryData
import mbuhot.eskotlin.query.initQuery
import mbuhot.eskotlin.query.term.TermBlock
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.*
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.TermQueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

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

inline fun <reified T : Any> SearchResponse.mapped(mapper: ObjectMapper): List<T> {
    return hits.hits.filter { it.hasSource() }.mapNotNull {
        try {
            mapper.readValue<T>(it.sourceAsString)
        } catch (ex: Exception) {
            ElasticQueryService.log.info("Unable to deserialize IndexedFile from source: ${it.sourceAsString}")
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
    return search(*indices) { source(paging, builder) }.paginated(mapper, paging)
}

fun RestHighLevelClient.search(vararg indices: String, builder: SearchRequest.() -> Unit): SearchResponse {
    return search(SearchRequest(*indices).also(builder))
}

suspend fun RestHighLevelClient.aSearch(searchRequest: SearchRequest): SearchResponse = suspendCoroutine { cont ->
    searchAsync(searchRequest, actionListener(cont))
}

suspend fun RestHighLevelClient.aScrollThroughSearch(
    indices: List<String>,
    builder: SearchRequest.() -> Unit,
    handler: (SearchResponse) -> Unit
) {
    val request = SearchRequest(*indices.toTypedArray()).also(builder)
    var resp: SearchResponse = aSearch(request)
    while (resp.hits.hits.isNotEmpty()) {
        handler(resp)
        resp = resp.aScroll(this)
    }

    resp.aClearScroll(this)
}

suspend inline fun <reified T : Any> RestHighLevelClient.aScrollThroughSearch(
    mapper: ObjectMapper,
    indices: List<String>,
    noinline builder: SearchRequest.() -> Unit,
    noinline handler: (T) -> Unit
) {
    aScrollThroughSearch(indices, builder) {
        it.mapped<T>(mapper).forEach(handler)
    }
}

fun RestHighLevelClient.scrollThroughSearch(
    indices: List<String>,
    builder: SearchRequest.() -> Unit,
    handler: (SearchResponse) -> Unit
) {
    val request = SearchRequest(*indices.toTypedArray()).also(builder)
    var resp: SearchResponse = search(request)
    while (resp.hits.hits.isNotEmpty()) {
        handler(resp)
        resp = resp.scroll(this)
    }

    resp.clearScroll(this)
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

suspend fun SearchResponse.aScroll(client: RestHighLevelClient): SearchResponse = suspendCoroutine { continuation ->
    client.searchScrollAsync(SearchScrollRequest(scrollId), actionListener(continuation))
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
    client.searchScroll(SearchScrollRequest(scrollId))

suspend fun SearchResponse.aClearScroll(client: RestHighLevelClient): ClearScrollResponse = suspendCoroutine { cont ->
    client.clearScrollAsync(ClearScrollRequest().apply { scrollIds = listOf(scrollId) }, actionListener(cont))
}

fun SearchResponse.clearScroll(client: RestHighLevelClient): ClearScrollResponse =
    client.clearScroll(ClearScrollRequest().apply { scrollIds = listOf(scrollId) })

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

// Fixes an issue in the term DSL that didn't allow non-string values
class FixedTermBlock {
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
