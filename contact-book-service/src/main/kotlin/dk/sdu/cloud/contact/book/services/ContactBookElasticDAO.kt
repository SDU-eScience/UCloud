package dk.sdu.cloud.contact.book.services

import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.ElasticsearchClient
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType
import java.util.*
import kotlin.collections.HashMap

class ContactBookElasticDAO(private val elasticClient: RestHighLevelClient): ContactBookDAO {

    fun createIndex() {
        val request = CreateIndexRequest(CONTACT_BOOK_INDEX)
        request.settings("""
            {
            "number_of_shards": 2,
            "number_of_replicas": 2,
            "analysis": {
              "analyzer": "whitespace",
              "tokenizer": "whitespace"
            }
            }
            """.trimIndent(), XContentType.JSON)

        request.mapping("""
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
                }""".trimIndent(), XContentType.JSON)
        elasticClient.indices().create(request, RequestOptions.DEFAULT)
        elasticClient.indices().flush(FlushRequest(CONTACT_BOOK_INDEX).waitIfOngoing(true), RequestOptions.DEFAULT)
    }

    private fun createInsertContactRequest(fromUser: String, toUser: String, serviceOrigin: String): IndexRequest {
        val request = IndexRequest(CONTACT_BOOK_INDEX)
        val source = HashMap<String, Any>()
        source["fromUser"] = fromUser
        source["toUser"] = toUser
        source["createdAt"] = Date()
        source["serviceOrigin"] = serviceOrigin
        request.source(source)

        return request
    }

    override fun insert(fromUser: String, toUser: String, serviceOrigin: String) {
        val request = createInsertContactRequest(fromUser, toUser, serviceOrigin)
        elasticClient.index(request, RequestOptions.DEFAULT)
    }

    override fun insertBulk(fromUser: String, toUser: List<String>, serviceOrigin: String) {
        val request = BulkRequest()
        toUser.forEach { shareReceiver ->
            val indexRequest = createInsertContactRequest(fromUser, shareReceiver, serviceOrigin)
            request.add(indexRequest)
        }
        elasticClient.bulk(request, RequestOptions.DEFAULT)
    }

    override fun delete(fromUser: String, toUser: String, serviceOrigin: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAllContactsForUser(fromUser: String, serviceOrigin: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun queryContacts(fromUser: String, toUser: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        const val CONTACT_BOOK_INDEX = "contactbook"
    }
}
