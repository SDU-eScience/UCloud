package dk.sdu.cloud.contact.book.services

import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.ElasticsearchClient
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType

class ContactBookElasticDAO(val elasticClient: RestHighLevelClient) {

    fun createIndex() {
        val request = CreateIndexRequest(CONTACT_BOOK_INDEX)
        request.settings(
            Settings.builder()
                .put("index.number_of_shards",2)
                .put("index.number_of_replicas", 2)
                .put("analysis.analyzer", "whitespace" )
        )

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
                        },
                    }
                }""".trimIndent(), XContentType.JSON)
        elasticClient.indices().create(request, RequestOptions.DEFAULT)
        elasticClient.indices().flush(FlushRequest(CONTACT_BOOK_INDEX).waitIfOngoing(true), RequestOptions.DEFAULT)
    }

    companion object {
        const val CONTACT_BOOK_INDEX = "contactbook"
    }
}
