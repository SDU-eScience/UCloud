package dk.sdu.cloud.contact.book.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.MultiSearchRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.*
import kotlin.collections.HashMap

class ContactBookElasticDao(private val elasticClient: RestHighLevelClient) {

    fun createIndex() {
        if(elasticClient.indices().exists(GetIndexRequest(CONTACT_BOOK_INDEX), RequestOptions.DEFAULT)) {
            return
        }
        val request = CreateIndexRequest(CONTACT_BOOK_INDEX)
        request.settings(
            """
                {
                    "number_of_shards": 2,
                    "number_of_replicas": 2,
                    "analysis": {
                        "analyzer": "whitespace",
                        "tokenizer": "whitespace"
                    }
                }
            """,
            XContentType.JSON
        )

        request.mapping(
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
            """,
            XContentType.JSON
        )
        elasticClient.indices().create(request, RequestOptions.DEFAULT)
        elasticClient.indices().flush(FlushRequest(CONTACT_BOOK_INDEX).waitIfOngoing(true), RequestOptions.DEFAULT)
    }

    private fun createInsertContactRequest(fromUser: String, toUser: String, serviceOrigin: String): IndexRequest {
        val request = IndexRequest(CONTACT_BOOK_INDEX)
        val source = HashMap<String, Any>()
        source["fromUser"] = fromUser
        source["toUser"] = toUser
        source["createdAt"] = Time.now()
        source["serviceOrigin"] = serviceOrigin
        request.source(source)

        return request
    }

    fun insertContact(fromUser: String, toUser: String, serviceOrigin: String) {
        if (!elasticClient.indices().exists(GetIndexRequest(CONTACT_BOOK_INDEX), RequestOptions.DEFAULT)) {
            createIndex()
        }
        val exists = findSingleContactOrNull(fromUser, toUser, serviceOrigin)
        if (exists == null) {
            val request = createInsertContactRequest(fromUser, toUser, serviceOrigin)
            elasticClient.index(request, RequestOptions.DEFAULT)
        }
    }

    fun insertContactsBulk(fromUser: String, toUser: List<String>, serviceOrigin: String) {
        val request = BulkRequest(CONTACT_BOOK_INDEX)
        val multiSearchRequest = MultiSearchRequest()
        toUser.forEach { shareReceiver ->
            val searchRequest = createSingleContactSearch(fromUser, shareReceiver, serviceOrigin)
            multiSearchRequest.add(searchRequest)
        }
        val mResponse = elasticClient.msearch(multiSearchRequest, RequestOptions.DEFAULT)
        toUser.forEachIndexed { index, shareReceiver ->
            val exists = mResponse.responses[index].response?.hits?.totalHits?.value
            if (exists != null && exists.toInt() == 0) {
                val indexRequest = createInsertContactRequest(fromUser, shareReceiver, serviceOrigin)
                request.add(indexRequest)
            }
        }
        //Empty bulk requests not allowed
        if (request.numberOfActions() != 0) {
            elasticClient.bulk(request, RequestOptions.DEFAULT)
        }
    }

    private fun createSingleContactSearch(fromUser: String, toUser: String, serviceOrigin: String): SearchRequest {
        val searchRequest = SearchRequest(CONTACT_BOOK_INDEX)
        val searchSource = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.termQuery(
                        "fromUser", fromUser
                    )
                )
                .must(
                    QueryBuilders.termQuery(
                        "toUser", toUser
                    )
                )
                .must(
                    QueryBuilders.termQuery(
                        "serviceOrigin", serviceOrigin
                    )
                )
        )
        searchRequest.source(searchSource)
        return searchRequest
    }

    private fun findSingleContactOrNull(fromUser: String, toUser: String, serviceOrigin: String): SearchHit? {
        val searchRequest = createSingleContactSearch(fromUser, toUser, serviceOrigin)
        val response = elasticClient.search(searchRequest, RequestOptions.DEFAULT)
        return when {
            response.hits.totalHits!!.value.toInt() == 0 -> null
            response.hits.totalHits!!.value.toInt() == 1 -> response.hits.hits.first()
            response.hits.totalHits!!.value.toInt() > 1 -> throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            else -> throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    fun deleteContact(fromUser: String, toUser: String, serviceOrigin: String) {
        val doc = findSingleContactOrNull(fromUser, toUser, serviceOrigin)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val deleteRequest = DeleteRequest(CONTACT_BOOK_INDEX, doc.id)
        elasticClient.delete(deleteRequest, RequestOptions.DEFAULT)
    }

    fun getAllContactsForUser(fromUser: String, serviceOrigin: String): SearchHits {
        val searchRequest = SearchRequest(CONTACT_BOOK_INDEX)
        val searchSource = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.termQuery(
                        "fromUser", fromUser
                    )
                )
                .must(
                    QueryBuilders.termQuery(
                        "serviceOrigin", serviceOrigin
                    )
                )
        )
        searchRequest.source(searchSource)
        val response = elasticClient.search(searchRequest, RequestOptions.DEFAULT)
        return response.hits
    }

    fun queryContacts(fromUser: String, query: String, serviceOrigin: String): SearchHits {
        val searchRequest = SearchRequest(CONTACT_BOOK_INDEX)
        val searchSource = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.termQuery(
                        "fromUser", fromUser
                    )
                )
                .must(
                    QueryBuilders.wildcardQuery(
                        "toUser", "$query*"
                    )
                )
                .must(
                    QueryBuilders.termQuery(
                        "serviceOrigin", serviceOrigin
                    )
                )
        )
        searchRequest.source(searchSource)

        val response = elasticClient.search(searchRequest, RequestOptions.DEFAULT)
        return response.hits
    }

    companion object: Loggable {
        override val log = logger()
        const val CONTACT_BOOK_INDEX = "contactbook"
    }
}
