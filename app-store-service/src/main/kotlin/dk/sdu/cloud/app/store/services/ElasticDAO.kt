package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger

private const val SEARCH_RESPONSE_SIZE = 200

class ElasticDAO(
    val elasticClient: RestHighLevelClient
) {

    private fun findApplicationInElasticOrNull(appName: String, appVersion: String): SearchResponse? {
        val indexExists = elasticClient.indices().exists(GetIndexRequest(APPLICATION_INDEX), RequestOptions.DEFAULT)
        if (!indexExists) {
            return null
        }

        val request = SearchRequest(APPLICATION_INDEX)
        val source = SearchSourceBuilder()
            .query(
                QueryBuilders.boolQuery()
                    .must(
                        QueryBuilders.termQuery("name", appName.toLowerCase())
                    )
                    .must(
                        QueryBuilders.termQuery("version", appVersion.toLowerCase())
                    )
            ).size(SEARCH_RESPONSE_SIZE)

        request.source(source)

        val results = elasticClient.search(request, RequestOptions.DEFAULT)


        return when {
            results.hits.hits.isEmpty() -> null
            results.hits.hits.size > 1 -> throw RPCException.fromStatusCode(
                HttpStatusCode.Conflict,
                "Multiple entries matching: name = $appName and version = $appVersion found. Contact support for help."
            )
            else -> results
        }
    }

    fun createApplicationInElastic(app: Application) {
        createApplicationInElastic(
            app.metadata.name,
            app.metadata.version,
            app.metadata.description,
            app.metadata.title
        )
    }

    fun createApplicationInElastic(name: String, version: String, description: String, title: String) {

        val r = findApplicationInElasticOrNull(name, version)
        if (r != null) {
            log.info("Application already exists in Elastic cluster")
            return
        }

        val indexedApplication = ElasticIndexedApplication(
            name = name.toLowerCase(),
            version = version.toLowerCase(),
            description = description.toLowerCase(),
            title = title.toLowerCase()
        )

        val request = IndexRequest(APPLICATION_INDEX)
        val source = defaultMapper.writeValueAsBytes(indexedApplication)
        request.source(source, XContentType.JSON)

        elasticClient.index(request, RequestOptions.DEFAULT)
    }

    fun updateApplicationDescriptionInElastic(appName: String, appVersion: String, newDescription: String) {
        val results = findApplicationInElasticOrNull(appName, appVersion) ?: throw RPCException.fromStatusCode(
            HttpStatusCode.NotFound
        )

        val hitID = results.hits.hits.first().id
        val updateRequest = UpdateRequest(APPLICATION_INDEX, hitID).apply {
            doc("description", newDescription.toLowerCase())
        }

        elasticClient.update(updateRequest, RequestOptions.DEFAULT)

    }

    fun search(appTitleQuery: List<String>, appVersion: String?, descriptionQuery: List<String>): SearchResponse {
        var titleRegex = ""
        if (appTitleQuery.isEmpty()) {
            titleRegex = ".*"
        } else {
            titleRegex += "("
            appTitleQuery.forEachIndexed { index, s ->
                val lowerCasedString = s.toLowerCase()
                if (index == appTitleQuery.lastIndex) {
                    titleRegex += ".*$lowerCasedString.*"
                    return@forEachIndexed
                }
                titleRegex += ".*$lowerCasedString.*|"
            }
            titleRegex += ")"
        }

        // descriptions have a buffer of one char on each side. Reduces search window.
        var descriptionRegex = "("
        descriptionQuery.forEachIndexed { index, s ->
            val lowerCasedString = s.toLowerCase()
            if (index == descriptionQuery.lastIndex ) {
                descriptionRegex += ".?$lowerCasedString.?"
                return@forEachIndexed
            }
            descriptionRegex += ".?$lowerCasedString.?|"
        }
        descriptionRegex += ")"

        val version = appVersion ?: ""
        val request = SearchRequest(APPLICATION_INDEX)

        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.regexpQuery(
                        "description",//FIELD
                        descriptionRegex//REGEXP
                    )
                )
                .must(
                    QueryBuilders.regexpQuery(
                        "title",
                        titleRegex
                    )
                )
                .must(
                    QueryBuilders.wildcardQuery(
                        "version",
                        "$version*"
                    )
            )
        ).size(SEARCH_RESPONSE_SIZE)

        request.source(source)
        log.debug(source.toString())

        return elasticClient.search(request, RequestOptions.DEFAULT)
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val APPLICATION_INDEX = "applications"
    }
}
