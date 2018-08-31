package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.filesearch.api.TimestampQuery
import dk.sdu.cloud.indexing.util.isNullOrEmpty
import dk.sdu.cloud.indexing.util.search
import dk.sdu.cloud.indexing.util.term
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.storage.api.EventMaterializedStorageFile
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.fulltext.match_phrase_prefix
import mbuhot.eskotlin.query.term.range
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import java.util.*

class ElasticQueryService(
    private val elasticClient: RestHighLevelClient
) : IndexQueryService {
    private val mapper = jacksonObjectMapper()

    override fun findFileByIdOrNull(id: String): EventMaterializedStorageFile? {
        return elasticClient[GetRequest(FILES_INDEX, DOC_TYPE, id)]
            ?.takeIf { it.isExists }
            ?.let { mapper.readValue<ElasticIndexedFile>(it.sourceAsString) }
            ?.toMaterializedFile()
    }

    override fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> = elasticClient
        .search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            sort(ElasticIndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = listOf(
                    match_phrase_prefix {
                        ElasticIndexedFile.FILE_NAME_FIELD to {
                            this.query = query
                            max_expansions = 10
                        }
                    },

                    term {
                        boost = 0.5f
                        ElasticIndexedFile.OWNER_FIELD to query
                    }
                )

                filter {
                    terms {
                        ElasticIndexedFile.PATH_FIELD to roots
                    }
                }

                // minimum_should_match = 1
                // TODO Can't use this. eskotlin is compiled against an old version which isn't binary compatible
                // Also seems like eskotlin development is mostly dead, we should fork it.
            }.also {
                it.minimumShouldMatch(1)
            }
        }
        .mapItems { it.toMaterializedFile() }

    override fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        extensions: List<String>?,
        fileTypes: List<FileType>?,
        createdAt: TimestampQuery?,
        modifiedAt: TimestampQuery?,
        sensitivity: List<SensitivityLevel>?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> {
        if (name == null && owner == null && fileTypes.isNullOrEmpty() && createdAt == null &&
            modifiedAt == null && sensitivity.isNullOrEmpty() && annotations.isNullOrEmpty() &&
            extensions.isNullOrEmpty()
        ) {
            return Page(0, paging.itemsPerPage, paging.page, emptyList())
        }

        return elasticClient.search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            sort(ElasticIndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = ArrayList<QueryBuilder>().apply {
                    if (name != null) {
                        add(match_phrase_prefix {
                            ElasticIndexedFile.FILE_NAME_FIELD to {
                                query = name
                                max_expansions = 10
                            }
                        })
                    }


                }

                filter = ArrayList<QueryBuilder>().apply {
                    add(terms { ElasticIndexedFile.PATH_FIELD to roots })

                    if (createdAt != null) {
                        add(range {
                            ElasticIndexedFile.TIMESTAMP_CREATED_FIELD to {
                                if (createdAt.after != null) gte = createdAt.after
                                if (createdAt.before != null) lte = createdAt.before
                            }
                        })
                    }

                    if (modifiedAt != null) {
                        add(range {
                            ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD to {
                                if (modifiedAt.after != null) gte = modifiedAt.after
                                if (modifiedAt.before != null) lte = modifiedAt.before
                            }
                        })
                    }

                    if (owner != null) {
                        add(term {
                            ElasticIndexedFile.OWNER_FIELD to owner
                        })
                    }

                    if (!sensitivity.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.SENSITIVITY_FIELD to sensitivity!!.map { it.name }
                        })
                    }

                    if (!annotations.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.ANNOTATIONS_FIELD to annotations!!
                        })
                    }

                    if (!extensions.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.FILE_NAME_EXTENSION to extensions!!
                        })
                    }

                    if (!fileTypes.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.FILE_TYPE_FIELD to fileTypes!!.map { it.name }
                        })
                    }
                }
            }
        }.mapItems { it.toMaterializedFile() }
    }

    companion object : Loggable {
        override val log = logger()

        private const val FILES_INDEX = ElasticIndexingService.FILES_INDEX
        private const val DOC_TYPE = ElasticIndexingService.DOC_TYPE
    }
}