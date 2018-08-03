package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import java.util.ArrayList

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
        fileType: FileType?,
        lastModified: Long?,
        sensitivity: List<SensitivityLevel>?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> {
        if (name == null && owner == null && fileType == null && lastModified == null &&
            sensitivity == null && annotations == null) {
            return Page(0, paging.itemsPerPage, paging.page, emptyList())
        }

        elasticClient.search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
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

                    if (owner != null) {
                        add(term {
                            ElasticIndexedFile.OWNER_FIELD to owner
                        })
                    }

                    if (fileType != null) {
                        add(term {
                            ElasticIndexedFile.FILE_TYPE_FIELD to fileType.name
                        })
                    }

                    if (sensitivity != null) {
                        add(terms {
                            ElasticIndexedFile.SENSITIVITY_FIELD to sensitivity.map { it.name }
                        })
                    }

                    if (annotations != null) {
                        add(terms {
                            ElasticIndexedFile.ANNOTATIONS_FIELD to annotations
                        })
                    }
                }

                filter {
                    terms { ElasticIndexedFile.PATH_FIELD to roots }
                }
            }.also {
                it.minimumShouldMatch(1)
            }
        }

        TODO()
    }

    companion object : Loggable {
        override val log = logger()

        private const val FILES_INDEX = ElasticIndexingService.FILES_INDEX
        private const val DOC_TYPE = ElasticIndexingService.DOC_TYPE
    }
}