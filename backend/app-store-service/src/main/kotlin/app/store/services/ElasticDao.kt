package dk.sdu.cloud.app.store.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.analysis.WhitespaceAnalyzer
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RegexpQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch.core.*
import co.elastic.clients.elasticsearch.core.search.Hit
import co.elastic.clients.elasticsearch.indices.*
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayInputStream

private const val SEARCH_RESPONSE_SIZE = 200

class ElasticDao(
    val elasticClient: ElasticsearchClient
) {

    private fun findApplicationInElasticOrNull(appName: String, appVersion: String): List<Hit<ElasticIndexedApplication>>? {
        val indexExists = elasticClient.indices().exists(
            ExistsRequest.Builder()
                .index(APPLICATION_INDEX)
                .build()
        ).value()

        if (!indexExists) {
            //If index does not exist then create with special whitspace mapping (makes terms on whitespace only,
            // not on  special chars)
            val request = CreateIndexRequest.Builder()
                .index(APPLICATION_INDEX)
                .settings(
                    IndexSettings.Builder()
                        .numberOfShards("1")
                        .numberOfReplicas("2")
                        .analysis(
                            IndexSettingsAnalysis.Builder()
                                .analyzer("whitespace", WhitespaceAnalyzer.Builder().build()._toAnalyzer())
                                .build()
                        )
                        .build()
                )
                .mappings(
                    TypeMapping.Builder()
                        .withJson(ByteArrayInputStream(
                            """
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
                                }
                            """.toByteArray()
                        )).build()
                ).build()

            elasticClient.indices().create(request)
            flushElastic()
            return null
        }

        val request = SearchRequest.Builder()
            .index(APPLICATION_INDEX)
            .query(
                BoolQuery.Builder()
                    .must(
                        TermQuery.Builder()
                            .field("name")
                            .value(appName.lowercase())
                            .build()._toQuery()
                    )
                    .must(
                        TermQuery.Builder()
                            .field("version")
                            .value(appVersion.lowercase())
                            .build()._toQuery()
                    )
                    .build()._toQuery()
            )
            .build()

        val results = elasticClient.search(request, ElasticIndexedApplication::class.java).hits().hits()


        return when {
            results.isEmpty() -> null
            results.size > 1 -> throw RPCException.fromStatusCode(
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

        val normalizedTags = tags.map { it.lowercase() }

        val indexedApplication = ElasticIndexedApplication(
            name = name.lowercase(),
            version = version.lowercase(),
            description = description.lowercase(),
            title = title.lowercase(),
            tags = normalizedTags
        )

        val request = IndexRequest.Builder<ElasticIndexedApplication>()
            .index(APPLICATION_INDEX)
            .document(
                indexedApplication
            )
            .build()

        elasticClient.index(request)
        flushElastic()
    }

    fun deleteApplicationInElastic(
        name: String, version: String
    ) {
        val results = findApplicationInElasticOrNull(name, version) ?: throw RPCException.fromStatusCode(
            HttpStatusCode.NotFound
        )
        val id = results.first().id()
        val deleteRequest = DeleteRequest.Builder().index(APPLICATION_INDEX).id(id).build()
        elasticClient.delete(deleteRequest)
    }

    fun updateApplicationDescriptionInElastic(appName: String, appVersion: String, newDescription: String) {
        val results = findApplicationInElasticOrNull(appName, appVersion) ?: throw RPCException.fromStatusCode(
            HttpStatusCode.NotFound
        )

        val hit = results.first()
        val updateRequest = UpdateRequest.Builder<ElasticIndexedApplication, ElasticIndexedApplication>()
            .index(APPLICATION_INDEX)
            .id(hit.id())
            .doc(
                hit.source()?.copy(description = newDescription)
            )
            .build()

        elasticClient.update(updateRequest, ElasticIndexedApplication::class.java)
        flushElastic()
    }


    private fun findDocsByAppName(appName: String): List<Hit<ElasticIndexedApplication>> {
        val request = SearchRequest.Builder()
            .index(APPLICATION_INDEX)
            .query(
                BoolQuery.Builder()
                    .must(
                        TermQuery.Builder()
                            .field("name")
                            .value(appName.lowercase())
                            .build()._toQuery()
                    )
                    .build()._toQuery()
            )
            .build()

        return elasticClient.search(request, ElasticIndexedApplication::class.java).hits().hits()
    }

    fun addTagToElastic(appName: String, tag: List<String>) {
        val results = findDocsByAppName(appName)
        val normalizedTags = tag.map { it.lowercase() }

        results.forEach { elasticDoc ->
            val doc = elasticDoc.source()
            val newListOfTags = mutableListOf<String>()

            normalizedTags.forEach {
                if (doc?.tags?.contains(it) == true) {
                    return@forEach
                } else {
                    newListOfTags.add(it)
                }
            }
            if (doc?.tags != null && doc.tags.isNotEmpty()) {
                newListOfTags.addAll( doc.tags )
            }

            val updateRequest = UpdateRequest.Builder<ElasticIndexedApplication,ElasticIndexedApplication>()
                .index(APPLICATION_INDEX)
                .id(elasticDoc.id())
                .doc(
                    doc?.copy(tags = newListOfTags)
                )
                .build()
            elasticClient.update(updateRequest, ElasticIndexedApplication::class.java)
        }
        flushElastic()
    }

    fun removeTagFromElastic(appName: String, tag: List<String>) {
        val results = findDocsByAppName(appName)

        val normalizedTagsToDelete = tag.map { it.lowercase() }
        results.forEach { elasticDoc ->
            val doc = elasticDoc.source()

            val newListOfTags = mutableListOf<String>()
            doc?.tags?.forEach { oldTag ->
                if (normalizedTagsToDelete.contains(oldTag)) {
                    return@forEach
                } else {
                    newListOfTags.add(oldTag)
                }
            }

            val updateRequest = UpdateRequest.Builder<ElasticIndexedApplication, ElasticIndexedApplication>()
                .index(APPLICATION_INDEX)
                .id(elasticDoc.id())
                .doc(
                    doc?.copy(tags = newListOfTags)
                )
                .build()
            elasticClient.update(updateRequest, ElasticIndexedApplication::class.java)
        }
        flushElastic()
    }

    fun search(queryTerms: List<String>, tagFilter: List<String>): List<Hit<ElasticIndexedApplication>> {
        val request = SearchRequest.Builder()
            .index(APPLICATION_INDEX)

            .build()

        val qb = BoolQuery.Builder()

        val queryFilterTerms = BoolQuery.Builder()

        if (tagFilter.isEmpty()) {
            queryFilterTerms.should(MatchAllQuery.Builder().build()._toQuery())
        } else {
            tagFilter.forEach { tag ->
                queryFilterTerms.should(
                    TermQuery.Builder()
                        .field("tags")
                        .value(tag)
                        .build()._toQuery()
                )
            }
        }

        if (queryTerms.isEmpty()) {
            qb.should(MatchAllQuery.Builder().build()._toQuery()).filter(queryFilterTerms.build()._toQuery())
        } else {
            for (i in 0 until queryTerms.size) {
                val term = ".*${queryTerms[i]}.*"
                qb.should(
                    BoolQuery.Builder()
                        .should(
                            RegexpQuery.Builder()
                                .field("description")
                                .value(term)
                                .boost(0.5f)
                                .build()._toQuery()
                        )
                        .should(
                            RegexpQuery.Builder()
                                .field("title")
                                .value(term)
                                .boost(2.0f)
                                .build()._toQuery()
                        )
                        .should(
                            RegexpQuery.Builder()
                                .field("tags")
                                .value(term)
                                .build()._toQuery()
                        )
                        .should(
                            RegexpQuery.Builder()
                                .field("version")
                                .value(term)
                                .boost(5.0f)
                                .build()._toQuery()
                        ).minimumShouldMatch("1")
                        .build()._toQuery()
                ).minimumShouldMatch("1")
                    .filter(queryFilterTerms.build()._toQuery())
            }
        }

        return elasticClient.search(request, ElasticIndexedApplication::class.java).hits().hits()
    }

    private fun flushElastic() {
        elasticClient.indices().flush(FlushRequest.Builder().index(APPLICATION_INDEX).waitIfOngoing(true).build())
    }

    companion object : Loggable {
        override val log = logger()

        internal const val APPLICATION_INDEX = "applications"
    }
}
