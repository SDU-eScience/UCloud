package dk.sdu.cloud.indexing

import dk.sdu.cloud.indexing.http.QueryController
import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import kotlin.system.exitProcess

/**
 * The primary server class for indexing-service
 */
class Server(
    override val micro: Micro
) : CommonServer {
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()

    override fun start() {
        elastic = micro.elasticHighLevelClient

        val indexingService = ElasticIndexingService(elastic)
        val queryService = ElasticQueryService(elastic)

        val indexArgIdx = micro.commandLineArguments.indexOf("--create-index")
        if (indexArgIdx != -1) {
            val numberOfShards = micro.commandLineArguments.getOrNull(indexArgIdx + 1) ?: 5
            val numberOfReplicas = micro.commandLineArguments.getOrNull(indexArgIdx + 1) ?: 2
            try {
                val request = CreateIndexRequest("files")
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
                              "id": {
                                "type": "keyword"
                              },
                              "fileType": {
                                "type": "keyword"
                              },
                              "owner": {
                                "type": "keyword"
                              },
                              "size": {
                                "type": "long"
                              },
                              "fileTimestamps": {
                                "type": "object"
                              },
                              "sensitivity": {
                                "type": "keyword"
                              }
                            }
                          }
                    """.trimIndent(),
                    XContentType.JSON
                )
                elastic.indices().create(request, RequestOptions.DEFAULT)
                elastic.indices().flush(FlushRequest("files").waitIfOngoing(true), RequestOptions.DEFAULT)
                exitProcess(0)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                QueryController(queryService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        elastic.close()
    }
}
