package dk.sdu.cloud.file.ucloud.services

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import dk.sdu.cloud.file.ucloud.services.FileScanner.Companion.FILES_INDEX
import dk.sdu.cloud.service.Loggable
import java.net.ConnectException
import java.util.concurrent.ExecutionException

object FilesIndex : Loggable {
    override val log = logger()

    fun delete(elastic: RestHighLevelClient) {
        elastic.indices().delete(DeleteIndexRequest(FILES_INDEX), RequestOptions.DEFAULT)
    }

    fun create(elastic: RestHighLevelClient, numberOfShards: Int, numberOfReplicas: Int) {
        try {
            if (elastic.indices().exists(GetIndexRequest(FILES_INDEX), RequestOptions.DEFAULT)) {
                return
            }
            val request = CreateIndexRequest(FILES_INDEX)
            request.settings(
                """
                {
                    "number_of_shards": $numberOfShards,
                    "number_of_replicas": $numberOfReplicas,
                    "analysis": {
                        "analyzer": {
                            "path-analyzer": {
                                "type": "custom",
                                "tokenizer": "path-tokenizer"
                            },
                            "extension-analyzer": {
                                "type": "custom",
                                "tokenizer": "extension-tokenizer",
                                "filter": [
                                    "lowercase"
                                ]
                            }
                        },
                        "tokenizer": {
                            "path-tokenizer": {
                                "type": "path_hierarchy",
                                "delimiter": "/"
                            },
                            "extension-tokenizer": {
                                "type": "path_hierarchy",
                                "delimiter": ".",
                                "reverse": "true"
                            }
                        }
                    }
                }
            """.trimIndent(),
                XContentType.JSON
            )

            request.mapping(
                """
                {
                    "properties": {
                        "path": {
                            "type": "text",
                            "analyzer": "path-analyzer",
                            "search_analyzer": "keyword",
                            "fields": {
                                "keyword": {
                                    "type": "keyword"
                                }
                            }
                        },
                        "fileName": {
                            "type": "text",
                            "fields": {
                                "keyword": {
                                    "type": "keyword"
                                },
                                "extension": {
                                    "type": "text",
                                    "analyzer": "extension-analyzer",
                                    "search_analyzer": "keyword"
                                }
                            }
                        },
                        "fileDepth": {
                            "type": "long"
                        },
                        "fileType": {
                            "type": "keyword"
                        },
                        "size": {
                            "type": "long"
                        },
                        "createdAt" : {
                            "type": "long"
                        },
                        "collectionId" : {
                            "type": "text"
                        },
                        "owner" : {
                            "type": "keyword"
                        },
                        "projectId" : {
                            "type": "keyword"
                        },
                        "scanTime" : {
                            "type": "long"
                        }
                    }
                }
            """.trimIndent(),
                XContentType.JSON
            )
            elastic.indices().create(request, RequestOptions.DEFAULT)
            elastic.indices().flush(FlushRequest("files").waitIfOngoing(true), RequestOptions.DEFAULT)
        } catch (ex: ExecutionException) {
            log.info("It looks like ElasticSearch isn't running. We cannot index any files in that case.")
        } catch (ex: ConnectException) {
            log.info("It looks like ElasticSearch isn't running. We cannot index any files in that case.")
        } catch (ex: Throwable) {
            if (ex.cause is ExecutionException) {
                log.info("It looks like ElasticSearch isn't running. We cannot index any files in that case.")
            } else {
                log.warn(ex.stackTraceToString())
            }
        }
    }
}
