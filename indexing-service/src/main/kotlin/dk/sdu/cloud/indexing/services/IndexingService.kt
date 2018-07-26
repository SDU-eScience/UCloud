package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.indexing.util.search
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel
import dk.sdu.cloud.storage.api.StorageEvent
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.fulltext.match_phrase_prefix
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger
import java.io.File

data class IndexedFile(
    val id: String,
    val path: String,
    val fileName: String,
    val owner: String,
    val fileType: FileType,
    val lastModified: Long,

    // TODO Last two not currently supported. See #230
    val sensitivity: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
    val annotations: List<String> = emptyList()
) {
    companion object {
        // Refactoring safe without most of the performance penalty
        val ID_FIELD = IndexedFile::id.name
        val PATH_FIELD = IndexedFile::path.name
        val FILE_NAME_FIELD = IndexedFile::fileName.name
        val OWNER_FIELD = IndexedFile::owner.name
        val FILE_TYPE_FIELD = IndexedFile::fileType.name
        val LAST_MODIFIED_FILED = IndexedFile::lastModified.name
    }
}

interface Migratable {
    fun migrate() {}
}

data class InternalSearchResult(
    val id: String,
    val path: String,
    val fileType: FileType
) {
    fun toExternalResult(): SearchResult = SearchResult(path, fileType)
}

data class SearchResult(
    val path: String,
    val fileType: FileType
)

interface IndexingService : Migratable {
    fun handleEvent(event: StorageEvent)
    fun bulkHandleEvent(events: List<StorageEvent>) {
        events.forEach { handleEvent(it) }
    }
}

interface IndexQueryService {
    fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalSearchResult>

    fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        fileType: FileType?,
        lastModified: Long?,
        sensitivity: SensitivityLevel?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<InternalSearchResult>
}

class ElasticIndexingService(
    private val elasticClient: RestHighLevelClient
) : IndexingService {
    private val mapper = jacksonObjectMapper()

    private fun createIndexFromJsonResource(name: String) {
        elasticClient.indices().create(
            CreateIndexRequest(name).apply {
                source(
                    javaClass
                        .classLoader
                        .getResourceAsStream("elasticsearch/${name}_mapping.json")
                        .bufferedReader()
                        .readText(),

                    XContentType.JSON
                )
            }
        ).takeIf { it.isAcknowledged } ?: throw RuntimeException("Unable to create $name index")
    }

    override fun migrate() {
        try {
            elasticClient.indices().delete(Requests.deleteIndexRequest(FILES_INDEX))
        } catch (ex: ElasticsearchStatusException) {
            if (ex.status().status != 404) throw ex
        }

        createIndexFromJsonResource(FILES_INDEX)
    }

    override fun handleEvent(event: StorageEvent) {
        log.debug("Indexing based on storage event: $event")

        when (event) {
            is StorageEvent.CreatedOrModified -> elasticClient.index(handleCreatedOrModified(event))
            is StorageEvent.Deleted -> elasticClient.delete(handleDeleted(event))
            is StorageEvent.Moved -> elasticClient.update(handleMoved(event))
        }
    }

    override fun bulkHandleEvent(events: List<StorageEvent>) {
        val request = BulkRequest()
        events.forEach { event ->
            when (event) {
                is StorageEvent.CreatedOrModified -> request.add(handleCreatedOrModified(event))
                is StorageEvent.Deleted -> request.add(handleDeleted(event))
                is StorageEvent.Moved -> request.add(handleMoved(event))
            }
        }
    }

    private fun handleCreatedOrModified(event: StorageEvent.CreatedOrModified): IndexRequest {
        val indexedFile = IndexedFile(
            event.id,
            event.path,
            event.path.fileName(),
            event.owner,
            event.fileType,
            event.timestamp
        )

        return IndexRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
            source(mapper.writeValueAsBytes(indexedFile), XContentType.JSON)
        }
    }

    private fun handleDeleted(event: StorageEvent.Deleted): DeleteRequest {
        return DeleteRequest(FILES_INDEX, DOC_TYPE, event.id)
    }

    private fun handleMoved(event: StorageEvent.Moved): UpdateRequest {
        return UpdateRequest(FILES_INDEX, DOC_TYPE, event.id).apply {
            doc(
                mapOf(
                    IndexedFile.PATH_FIELD to event.path,
                    IndexedFile.FILE_NAME_FIELD to event.path.fileName()
                )
            )
        }
    }

    private fun String.fileName(): String = File(this).name

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val FILES_INDEX = "files"
        internal const val DOC_TYPE = "doc"
    }
}

class ElasticQueryService(
    private val elasticClient: RestHighLevelClient
) : IndexQueryService {
    private val mapper = jacksonObjectMapper()

    override fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalSearchResult> = elasticClient
        .search<IndexedFile>(mapper, paging, FILES_INDEX) {
            bool {
                must {
                    match_phrase_prefix {
                        IndexedFile.FILE_NAME_FIELD to {
                            this.query = query
                            max_expansions = 10
                        }
                    }
                }

                filter {
                    terms {
                        IndexedFile.PATH_FIELD to roots
                    }
                }
            }
        }
        .mapItems { with(it) { InternalSearchResult(id, path, fileType) } }

    override fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        fileType: FileType?,
        lastModified: Long?,
        sensitivity: SensitivityLevel?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<InternalSearchResult> {
        TODO("not implemented")
    }

    companion object : Loggable {
        override val log = logger()

        private val FILES_INDEX = ElasticIndexingService.FILES_INDEX
        private val DOC_TYPE = ElasticIndexingService.DOC_TYPE
    }
}

