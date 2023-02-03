package dk.sdu.cloud.file.ucloud.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.*
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.elasticsearch.core.ScrollRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilesProviderSearchRequest
import dk.sdu.cloud.file.orchestrator.api.PartialUFile
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFile
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFileConstants
import dk.sdu.cloud.file.ucloud.api.toPartialUFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import java.net.SocketTimeoutException

class ElasticQueryService(
    private val elasticClient: ElasticsearchClient,
    private val nativeFS: NativeFS,
    private val pathConverter: PathConverter
) {
    private val mapper = defaultMapper

    private suspend fun verifyAndUpdateIndex(hits: List<PartialUFile>): List<PartialUFile> {
        //making sure that the results does not contain duplicates
        val filteredHits = hits.toSet()

        val illegalIndices = mutableListOf<PartialUFile>()

        val existingHits = filteredHits.mapNotNull { partialUFile ->
            try {
                nativeFS.stat(pathConverter.ucloudToInternal(UCloudFile.createFromPreNormalizedString(partialUFile.id)))
                partialUFile
            } catch (ex: Exception) {
                if (ex is FSException.NotFound) {
                    log.warn("Path not found")
                    illegalIndices.add(partialUFile)
                    null
                } else {
                    throw ex
                }
            }
        }
        // Deletes indices that do not exist anymore
        illegalIndices.forEach {
            log.info("Deleting: ${it.id} from ES (does not exist anymore)")
            val queryDeleteRequest = DeleteByQueryRequest.Builder()
                .index(FileScanner.FILES_INDEX)
                .conflicts(Conflicts.Proceed)
                .query(
                    MatchQuery.Builder()
                        .field(ElasticIndexedFileConstants.PATH_FIELD)
                        .query(it.id)
                        .build()._toQuery()
                )
                .build()
            try {
                var moreToDelete = true
                while (moreToDelete) {
                    try {
                        val response = elasticClient.deleteByQuery(queryDeleteRequest)
                        if (response.deleted() == 0L) moreToDelete = false
                    } catch (ex: SocketTimeoutException) {
                        FileScanner.log.warn(ex.message)
                        FileScanner.log.warn("Socket Timeout: Delay and try again")
                        delay(2000)
                    }
                }
            } catch (ex: ElasticsearchException) {
                FileScanner.log.warn(ex.message)
                FileScanner.log.warn("Deletion failed")
            }
        }

        return existingHits
    }

    suspend fun query(
        searchRequest: FilesProviderSearchRequest
    ): PageV2<PartialUFile> {
        val normalizedRequest = searchRequest.normalize()
        if (searchRequest.query.isBlank()) {
            return PageV2(normalizedRequest.itemsPerPage, emptyList(), null)
        }
        if (searchRequest.next != null) {
            val searchScrollRequest = ScrollRequest.Builder()
                .scrollId(searchRequest.next)
                .scroll(Time.Builder().time("1m").build())
                .build()

            val response = elasticClient.scroll(searchScrollRequest, ElasticIndexedFile::class.java)
            var scrollId = response.scrollId()
            val hits = response.hits().hits().mapNotNull {
                it.source()?.toPartialUFile()
            }

            if (hits.isEmpty()) {
                scrollId = null
            }

            val existingHits = verifyAndUpdateIndex(hits)

            return PageV2(normalizedRequest.itemsPerPage, existingHits, scrollId)
        }

        val request = SearchRequest.Builder()
            .index(FILES_INDEX)
            .query(
                searchBasedOnQuery(searchRequest)
            )
            .sort(
                SortOptions.Builder()
                    .field(
                        FieldSort.Builder()
                            .field(ElasticIndexedFileConstants.FILE_NAME_KEYWORD)
                            .order(SortOrder.Asc)
                            .build()
                    )
                    .build()
            )
            .size(normalizedRequest.itemsPerPage)
            .scroll(Time.Builder().time("1m").build())
            .build()

        val response = try {
            elasticClient.search(request, ElasticIndexedFile::class.java)
        } catch (ex: Exception) {
            throw ex
        }
        val scrollId = response.scrollId()
        val hits = response.hits().hits().mapNotNull {
           it.source()?.toPartialUFile()
        }

        val existingHits = verifyAndUpdateIndex(hits)

        return PageV2(
            normalizedRequest.itemsPerPage,
            existingHits,
            if ((response.hits()?.total()?.value() ?: 0) > normalizedRequest.itemsPerPage) scrollId else null
        )
    }

    private fun searchBasedOnQuery(searchRequest: FilesProviderSearchRequest): Query {
        return with(searchRequest) {
            BoolQuery.Builder()
                .should(
                    WildcardQuery.Builder()
                        .field(ElasticIndexedFileConstants.FILE_NAME_FIELD)
                        .value("*${query.lowercase()}*")
                        .build()._toQuery(),
                    TermQuery.Builder()
                        .field(ElasticIndexedFileConstants.FILE_NAME_EXTENSION)
                        .value(query.lowercase().substringAfterLast("."))
                        .build()._toQuery()
                )
                .filter(
                    if (searchRequest.owner.project == null) {
                        TermsQuery.Builder()
                            .field(ElasticIndexedFileConstants.OWNER_FIELD)
                            .terms(
                                TermsQueryField.Builder().value(
                                    listOf(
                                        FieldValue.Builder().stringValue(searchRequest.owner.createdBy).build()
                                    )
                                ).build()
                            )
                            .build()._toQuery()
                    } else {
                        TermsQuery.Builder()
                            .field(ElasticIndexedFileConstants.PROJECT_ID)
                            .terms(
                                TermsQueryField.Builder().value(
                                    listOf(
                                        FieldValue.Builder().stringValue(searchRequest.owner.project).build()
                                    )
                                ).build()
                            )
                            .build()._toQuery()
                    },
                    if (flags.filterByFileExtension != null) {
                        TermsQuery.Builder()
                            .field(ElasticIndexedFileConstants.FILE_NAME_EXTENSION)
                            .terms(
                                TermsQueryField.Builder().value(
                                    listOf(
                                        FieldValue.Builder().stringValue(flags.filterByFileExtension).build()
                                    )
                                ).build()
                            )
                            .build()._toQuery()
                    } else {
                        //TODOD() MIGHT NOT WORK SHOULD JUST BE EMPTY
                        TermsQuery.Builder()
                            .field(ElasticIndexedFileConstants.FILE_NAME_EXTENSION)
                            .build()._toQuery()
                    }
                )
                //TODO ORIGINAL IF SHOULD() NOT EMPTY DO MINIMUMMATCH
                .minimumShouldMatch("1")

                .build()._toQuery()
        }
    }

    companion object : Loggable {
        override val log = logger()

        private const val FILES_INDEX = FileScanner.FILES_INDEX

        private const val FILE_NAME_QUERY_MAX_EXPANSIONS = 50
    }
}
