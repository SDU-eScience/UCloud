package dk.sdu.cloud.file.ucloud.services

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import services.FileScanner.Companion.FILES_INDEX

object FilesIndex {
    fun delete(elastic: RestHighLevelClient) {
        elastic.indices().delete(DeleteIndexRequest(FILES_INDEX), RequestOptions.DEFAULT)
    }

    fun create(elastic: RestHighLevelClient, numberOfShards: Int, numberOfReplicas: Int) {
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
                        "rctime": {
                            "type": "text"
                        },
                        "permission": {
                            "type": "keyword"
                        },
                        "createdAt" : {
                            "type": "long"
                        },
                        "collectionId" : {
                            "type": "text"
                        },
                        "owner" : {
                            "type": "text"
                        }
                    }
                }
            """.trimIndent(),
            XContentType.JSON
        )
        elastic.indices().create(request, RequestOptions.DEFAULT)
        elastic.indices().flush(FlushRequest("files").waitIfOngoing(true), RequestOptions.DEFAULT)
    }
}
