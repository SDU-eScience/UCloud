package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger

private const val SEARCH_RESPONSE_SIZE = 200

class ElasticDAO(
    val elasticClient: RestHighLevelClient
) {

    private fun findApplicationInElasticOrNull(appName: String, appVersion: String): SearchResponse? {
        val indexExists = elasticClient.indices().exists(GetIndexRequest(APPLICATION_INDEX), RequestOptions.DEFAULT)
        if (!indexExists) {
            //If index does not exist then create with special whitspace mapping (makes terms on whitespace only,
            // not on  special chars)
            val request = CreateIndexRequest(APPLICATION_INDEX)
            request.settings(Settings.builder()
                .put("index.number_of_shards",1)
                .put("index.number_of_replicas", 2)
                .put("analysis.analyzer", "whitespace" )
            )

            request.mapping("""
                {
                    "properties" : {
                        "version" : {
                            "type" : "text",
                            "analyzer": "whitespace",
                            "fields" : {
                                "keyword" : { 
                                    "type" : "text",
                                    "analyzer" : "whitespace"
                                }
                            }
                        }
                    }
                }""".trimIndent(),XContentType.JSON)
            elasticClient.indices().create(request, RequestOptions.DEFAULT)
            flushElastic()
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
            )

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

    fun createApplicationInElastic(
        name: String, version: String,
        description: String,
        title: String,
        tags: List<String> = emptyList()
    ) {

        val r = findApplicationInElasticOrNull(name, version)
        if (r != null) {
            log.info("Application already exists in Elastic cluster")
            return
        }

        val normalizedTags = tags.map { it.toLowerCase() }

        val indexedApplication = ElasticIndexedApplication(
            name = name.toLowerCase(),
            version = version.toLowerCase(),
            description = description.toLowerCase(),
            title = title.toLowerCase(),
            tags = normalizedTags
        )

        val request = IndexRequest(APPLICATION_INDEX)
        val source = defaultMapper.writeValueAsBytes(indexedApplication)
        request.source(source, XContentType.JSON)

        elasticClient.index(request, RequestOptions.DEFAULT)
        flushElastic()
    }

    fun deleteApplicationInElastic(
        name: String, version: String
    ) {
        val results = findApplicationInElasticOrNull(name, version) ?: throw RPCException.fromStatusCode(
            HttpStatusCode.NotFound
        )
        val id = results.hits.hits.first().id
        val deleteRequest = DeleteRequest(APPLICATION_INDEX, id)
        elasticClient.delete(deleteRequest, RequestOptions.DEFAULT)
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
        flushElastic()
    }


    private fun findDocsByAppName(appName: String): SearchResponse {
        val request = SearchRequest(APPLICATION_INDEX)
        val source = SearchSourceBuilder()
            .query(
                QueryBuilders.boolQuery()
                    .must(
                        QueryBuilders.termQuery("name", appName.toLowerCase())
                    )
            )

        request.source(source)

        return elasticClient.search(request, RequestOptions.DEFAULT)
    }

    fun addTagToElastic(appName: String, tag: List<String>) {
        val results = findDocsByAppName(appName)
        val normalizedTags = tag.map { it.toLowerCase() }

        results.hits.hits.forEach { elasticDoc ->
            val doc = defaultMapper.readValue<ElasticIndexedApplication>(elasticDoc.sourceAsString)
            val newListOfTags = mutableListOf<String>()

            normalizedTags.forEach {
                if (doc.tags.contains(it)) {
                    return@forEach
                } else {
                    newListOfTags.add(it)
                }
            }
            newListOfTags.addAll(doc.tags)

            val updateRequest = UpdateRequest(APPLICATION_INDEX, elasticDoc.id).doc("tags", newListOfTags)
            elasticClient.update(updateRequest, RequestOptions.DEFAULT)
        }
        flushElastic()
    }

    fun removeTagFromElastic(appName: String, tag: List<String>) {
        val results = findDocsByAppName(appName)

        val normalizedTagsToDelete = tag.map { it.toLowerCase() }
        results.hits.hits.forEach { elasticDoc ->
            val doc = defaultMapper.readValue<ElasticIndexedApplication>(elasticDoc.sourceAsString)

            val newListOfTags = mutableListOf<String>()
            doc.tags.forEach { oldTag ->
                if (normalizedTagsToDelete.contains(oldTag)) {
                    return@forEach
                } else {
                    newListOfTags.add(oldTag)
                }
            }

            val updateRequest = UpdateRequest(APPLICATION_INDEX, elasticDoc.id).doc("tags", newListOfTags)
            elasticClient.update(updateRequest, RequestOptions.DEFAULT)
        }
        flushElastic()
    }

    fun search(queryTerms: List<String>, tagFilter: List<String>): SearchResponse {
        val request = SearchRequest(APPLICATION_INDEX)

        val qb = QueryBuilders.boolQuery()

        val queryFilterTerms = QueryBuilders.boolQuery()

        if (tagFilter.isEmpty()) {
            queryFilterTerms.should(QueryBuilders.matchAllQuery())
        } else {
            tagFilter.forEach { tag ->
                queryFilterTerms.should(
                    QueryBuilders.termQuery(
                        "tags",
                        tag
                    )
                )
            }
        }

        if (queryTerms.isEmpty()) {
            qb.should(QueryBuilders.matchAllQuery()).filter(queryFilterTerms)
        } else {
            for (i in 0 until queryTerms.size) {
                val term = ".*${queryTerms[i]}.*"
                qb.should(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.regexpQuery(
                                "description",//FIELD
                                term//REGEXP
                            ).boost(0.5f)
                        )
                        .should(
                            QueryBuilders.regexpQuery(
                                "title",
                                term
                            ).boost(2.0f)
                        )
                        .should(
                            QueryBuilders.regexpQuery(
                                "tags",
                                term
                            )
                        )
                        .should(
                            QueryBuilders.regexpQuery(
                                "version",
                                term
                            ).boost(5.0f)
                        ).minimumShouldMatch(1)
                ).minimumShouldMatch(1).filter(queryFilterTerms)
            }
        }
        val source = SearchSourceBuilder().query(qb).size(SEARCH_RESPONSE_SIZE)

        request.source(source)
        log.debug(source.toString())

        return elasticClient.search(request, RequestOptions.DEFAULT)
    }

    private fun flushElastic() {
        elasticClient.indices().flush(FlushRequest(APPLICATION_INDEX).waitIfOngoing(true), RequestOptions.DEFAULT)
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val APPLICATION_INDEX = "applications"
    }
}
