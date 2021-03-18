package dk.sdu.cloud.indexing.services

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import kotlin.system.exitProcess

object FilesIndex {
    fun delete(elastic: RestHighLevelClient) {
        elastic.indices().delete(DeleteIndexRequest(FileSystemScanner.FILES_INDEX), RequestOptions.DEFAULT)
    }

    fun create(elastic: RestHighLevelClient, numberOfShards: Int, numberOfReplicas: Int) {
        val request = CreateIndexRequest(FileSystemScanner.FILES_INDEX)
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
