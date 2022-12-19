package dk.sdu.cloud.contact.book.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.analysis.WhitespaceAnalyzer
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery
import co.elastic.clients.elasticsearch.core.*
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.CreateOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.elasticsearch.core.msearch.MultisearchBody
import co.elastic.clients.elasticsearch.core.msearch.MultisearchHeader
import co.elastic.clients.elasticsearch.core.msearch.RequestItem
import co.elastic.clients.elasticsearch.core.search.Hit
import co.elastic.clients.elasticsearch.indices.*
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import java.io.ByteArrayInputStream
import java.net.ConnectException
import kotlin.collections.HashMap

class ContactBookElasticDao(private val elasticClient: ElasticsearchClient) {

    fun createIndex() {
        try {
            if (elasticClient.indices().exists(ExistsRequest.Builder().index(CONTACT_BOOK_INDEX).build()).value()) {
                return
            }

            val request = CreateIndexRequest.Builder()
                .index(CONTACT_BOOK_INDEX)
                .settings(
                    IndexSettings.Builder()
                        .numberOfShards("2")
                        .numberOfReplicas("2")
                        .analysis(
                            IndexSettingsAnalysis.Builder()
                                .analyzer("whitespace", WhitespaceAnalyzer.Builder().build()._toAnalyzer())
                                .build()
                        ).build()
                )
                .mappings(
                    TypeMapping.Builder()
                        .withJson(
                            ByteArrayInputStream(
                                """
                                    {
                                        "properties" : {
                                            "fromUser" : {
                                                "type" : "text",
                                                "analyzer": "whitespace",
                                                "fields" : {
                                                    "keyword" : { 
                                                        "type" : "text",
                                                        "analyzer" : "whitespace"
                                                    }
                                                }
                                            },
                                            "toUser" : {
                                                "type" : "text",
                                                "analyzer": "whitespace",
                                                "fields" : {
                                                    "keyword" : { 
                                                        "type" : "text",
                                                        "analyzer" : "whitespace"
                                                    }
                                                }
                                            },
                                            "createdAt" : { 
                                                "type" : "date"
                                            },
                                            "serviceOrigin" : {
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
                            )
                        )
                        .build()
                ).build()

            elasticClient.indices().create(request)
            elasticClient.indices().flush(FlushRequest.Builder().index(CONTACT_BOOK_INDEX).waitIfOngoing(true).build())
        } catch (ex: ConnectException) {
            log.info("Failed to create index because of missing elasticsearch.")
        }
    }

    private fun createInsertContactRequest(fromUser: String, toUser: String, serviceOrigin: String): IndexRequest<ElasticIndexedContact> {
        return IndexRequest.Builder<ElasticIndexedContact>()
            .index(CONTACT_BOOK_INDEX)
            .document(ElasticIndexedContact(
                fromUser = fromUser,
                toUser = toUser,
                createdAt = Time.now(),
                serviceOrigin = serviceOrigin
            ))
            .build()
    }

    fun insertContact(fromUser: String, toUser: String, serviceOrigin: String) {
        if (!elasticClient.indices().exists(ExistsRequest.Builder().index(CONTACT_BOOK_INDEX).build()).value()) {
            createIndex()
        }
        val exists = findSingleContactOrNull(fromUser, toUser, serviceOrigin)
        if (exists == null) {
            val request = createInsertContactRequest(fromUser, toUser, serviceOrigin)
            elasticClient.index(request)
        }
    }

    fun insertContactsBulk(fromUser: String, toUser: List<String>, serviceOrigin: String) {
        val multiSearchRequest = MsearchRequest.Builder()
            .index(listOf(CONTACT_BOOK_INDEX))
            .searches(
                toUser.map { shareReciever ->
                    RequestItem.Builder()
                        .header(
                            MultisearchHeader.Builder()
                                .index(CONTACT_BOOK_INDEX)
                                .build()
                        )
                        .body(
                            MultisearchBody.Builder()
                                .query(
                                    createSingleContactSearch(fromUser, shareReciever, serviceOrigin)
                                )
                                .build()
                        ).build()
                }
            )
            .build()

        val mResponse = elasticClient.msearch(multiSearchRequest, ElasticIndexedContact::class.java)


        val request = BulkRequest.Builder()
            .index(CONTACT_BOOK_INDEX)

        val operations = mutableListOf<BulkOperation>()
        toUser.forEachIndexed { index, shareReceiver ->
            val exists = mResponse.responses()[index].result().hits().total()?.value()
            if (exists != null && exists.toInt() == 0) {
                operations.add(
                    BulkOperation.Builder()
                        .index(
                            IndexOperation.Builder<ElasticIndexedContact>()
                                .document(
                                    ElasticIndexedContact(
                                        fromUser,
                                        shareReceiver,
                                        Time.now(),
                                        serviceOrigin
                                    )
                                ).build()

                        ).build()
                )
            }
        }

        request.operations(operations)
        val finishedRequest = request.build()
        //Empty bulk requests not allowed
        if (finishedRequest.operations().size != 0) {
            elasticClient.bulk(finishedRequest)
        }
    }

    private fun createSingleContactSearch(fromUser: String, toUser: String, serviceOrigin: String): Query {
        return Query(
            BoolQuery.Builder()
                .must(
                    TermQuery.Builder()
                        .field("fromUser")
                        .value(fromUser)
                        .build()._toQuery()
                )
                .must(
                    TermQuery.Builder()
                        .field("toUser")
                        .value(toUser)
                        .build()._toQuery()
                )
                .must(
                    TermQuery.Builder()
                        .field("serviceOrigin")
                        .value(serviceOrigin)
                        .build()._toQuery()
                )
                .build()
        )
    }

    private fun findSingleContactOrNull(fromUser: String, toUser: String, serviceOrigin: String): Hit<ElasticIndexedContact>? {
        val searchRequest = SearchRequest.Builder()
            .index(CONTACT_BOOK_INDEX)
            .query(createSingleContactSearch(fromUser, toUser, serviceOrigin))
            .build()

        val response = elasticClient.search(searchRequest, ElasticIndexedContact::class.java)
        val totalHits = response.hits().total()?.value()?.toInt() ?: -1
        return when {
            totalHits == 0 -> null
            totalHits == 1 -> response.hits().hits().first()
            totalHits > 1 -> throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            else -> throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    fun deleteContact(fromUser: String, toUser: String, serviceOrigin: String) {
        val doc = findSingleContactOrNull(fromUser, toUser, serviceOrigin)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val deleteRequest = DeleteRequest.Builder().index(CONTACT_BOOK_INDEX).id(doc.id()).build()
        elasticClient.delete(deleteRequest)
    }

    fun getAllContactsForUser(fromUser: String, serviceOrigin: String): List<Hit<ElasticIndexedContact>> {
        val searchRequest = SearchRequest.Builder()
            .index(CONTACT_BOOK_INDEX)
            .query(
                BoolQuery.Builder()
                    .must(
                        TermQuery.Builder()
                            .field("fromUser")
                            .value(fromUser)
                            .build()._toQuery()
                    )
                    .must(
                        TermQuery.Builder()
                            .field("serviceOrigin")
                            .value(serviceOrigin)
                            .build()._toQuery()
                    )
                    .build()._toQuery()
            ).build()

        val response = elasticClient.search(searchRequest, ElasticIndexedContact::class.java)
        return response.hits().hits()
    }

    fun queryContacts(fromUser: String, query: String, serviceOrigin: String): List<Hit<ElasticIndexedContact>> {
        val searchRequest = SearchRequest.Builder()
            .index(CONTACT_BOOK_INDEX)
            .query(
                BoolQuery.Builder()
                    .must(
                        TermQuery.Builder()
                            .field("fromUser")
                            .value(fromUser)
                            .build()._toQuery()
                    )
                    .must(
                        WildcardQuery.Builder()
                            .field("toUser")
                            .value("$query*")
                            .build()._toQuery()
                    )
                    .must(
                        TermQuery.Builder()
                            .field("serviceOrigin")
                            .value(serviceOrigin)
                            .build()._toQuery()
                    )
                    .build()._toQuery()
            )
            .build()

        val response = elasticClient.search(searchRequest, ElasticIndexedContact::class.java)
        return response.hits().hits()
    }

    companion object: Loggable {
        override val log = logger()
        const val CONTACT_BOOK_INDEX = "contactbook"
    }
}
