package dk.sdu.cloud.file.ucloud.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch.indices.*
import dk.sdu.cloud.file.ucloud.services.FileScanner.Companion.FILES_INDEX
import dk.sdu.cloud.service.Loggable
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.util.concurrent.ExecutionException

object FilesIndex : Loggable {
    override val log = logger()

    fun delete(elastic: ElasticsearchClient) {
        elastic.indices().delete(DeleteIndexRequest.Builder().index(FILES_INDEX).build())
    }

    fun create(elastic: ElasticsearchClient, numberOfShards: Int, numberOfReplicas: Int) {
        try {
            if (elastic.indices().exists(ExistsRequest.Builder().index(FILES_INDEX).build()).value()) {
                return
            }
            val request = CreateIndexRequest.Builder()
                .index(FILES_INDEX)
                .settings(
                    IndexSettings.Builder()
                        .withJson(
                            ByteArrayInputStream(
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
                            """.toByteArray()
                            )
                        )
                        .build()

                )
                .mappings(
                    TypeMapping.Builder()
                        .withJson(
                            ByteArrayInputStream(
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
                                    }""".toByteArray()
                            )
                        )
                        .build()
                ).build()

            elastic.indices().create(request)
            elastic.indices().flush(FlushRequest.Builder().index("files").waitIfOngoing(true).build())
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
