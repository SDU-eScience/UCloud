package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.util.scrollThroughSearch
import dk.sdu.cloud.indexing.util.search
import dk.sdu.cloud.indexing.util.source
import dk.sdu.cloud.indexing.util.term
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.storage.api.*
import kotlinx.coroutines.experimental.runBlocking
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
import java.util.*

data class IndexedFile(
    val id: String,
    val path: String,
    val fileName: String,
    val owner: String,

    /**
     * Depth in the file hierarchy
     *
     * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4
     */
    val fileDepth: Int,

    val fileType: FileType,

    val size: Long,
    val fileTimestamps: Timestamps,
    val checksum: FileChecksum,

    val isLink: Boolean,
    val linkTarget: String?,
    val linkTargetId: String?,

    val sensitivity: SensitivityLevel,

    val annotations: Set<String>
) {
    @Suppress("unused")
    companion object {
        // Refactoring safe without most of the performance penalty
        val ID_FIELD = IndexedFile::id.name
        val PATH_FIELD = IndexedFile::path.name
        val FILE_NAME_FIELD = IndexedFile::fileName.name
        val OWNER_FIELD = IndexedFile::owner.name
        val FILE_DEPTH_FIELD = IndexedFile::fileDepth.name
        val FILE_TYPE_FIELD = IndexedFile::fileType.name

        val SIZE_FIELD = IndexedFile::size.name
        val FILE_TIMESTAMPS_FIELD = IndexedFile::fileTimestamps.name
        val CHECKSUM_FIELD = IndexedFile::checksum.name

        val IS_LINK_FIELD = IndexedFile::isLink.name
        val LINK_TARGET_FIELD = IndexedFile::linkTarget.name
        val LINK_TARGET_ID_FIELD = IndexedFile::linkTargetId.name

        val SENSITIVITY_FIELD = IndexedFile::sensitivity.name

        val ANNOTATIONS_FIELD = IndexedFile::annotations.name
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

        @Suppress("UNUSED_VARIABLE")
        val ignored: Any? = when (event) {
            is StorageEvent.CreatedOrRefreshed -> elasticClient.update(handleCreatedOrModified(event))
            is StorageEvent.Deleted -> elasticClient.delete(handleDeleted(event))
            is StorageEvent.Moved -> elasticClient.update(handleMoved(event))
            is StorageEvent.SensitivityUpdated -> elasticClient.update(handleSensitivityUpdated(event))
            is StorageEvent.AnnotationsUpdated -> elasticClient.update(handleAnnotationsUpdated(event))
            is StorageEvent.Invalidated -> elasticClient.bulk(handleInvalidated(event))
        }
    }

    override fun bulkHandleEvent(events: List<StorageEvent>) {
        val request = BulkRequest()
        events.forEach { event ->
            @Suppress("UNUSED_VARIABLE")
            val ignored: Any? = when (event) {
                is StorageEvent.CreatedOrRefreshed -> request.add(handleCreatedOrModified(event))
                is StorageEvent.Deleted -> request.add(handleDeleted(event))
                is StorageEvent.Moved -> request.add(handleMoved(event))
                is StorageEvent.SensitivityUpdated -> request.add(handleSensitivityUpdated(event))
                is StorageEvent.AnnotationsUpdated -> request.add(handleAnnotationsUpdated(event))
                is StorageEvent.Invalidated -> request.add(handleInvalidated(event).requests())
            }
        }
    }

    private fun handleCreatedOrModified(event: StorageEvent.CreatedOrRefreshed): UpdateRequest {
        val indexedFile = IndexedFile(
            id = event.id,
            owner = event.owner,

            path = event.path,
            fileName = event.path.fileName(),
            fileDepth = event.path.depth(),

            fileType = event.fileType,

            size = event.size,
            fileTimestamps = event.fileTimestamps,
            checksum = event.checksum,

            isLink = event.isLink,
            linkTarget = event.linkTarget,
            linkTargetId = event.linkTargetId,

            sensitivity = event.sensitivityLevel,

            annotations = event.annotations
        )

        return UpdateRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
            upsert(mapper.writeValueAsBytes(indexedFile), XContentType.JSON)
        }
    }

    private fun handleDeleted(event: StorageEvent.Deleted): DeleteRequest {
        return DeleteRequest(FILES_INDEX, DOC_TYPE, event.id)
    }

    private fun handleMoved(event: StorageEvent.Moved): UpdateRequest = updateDoc(
        event.id, mapOf(
            IndexedFile.PATH_FIELD to event.path,
            IndexedFile.FILE_NAME_FIELD to event.path.fileName(),
            IndexedFile.FILE_DEPTH_FIELD to event.path.depth()
        )
    )

    private fun handleSensitivityUpdated(event: StorageEvent.SensitivityUpdated): UpdateRequest = updateDoc(
        event.id, mapOf(
            IndexedFile.SENSITIVITY_FIELD to event.sensitivityLevel
        )
    )

    private fun handleAnnotationsUpdated(event: StorageEvent.AnnotationsUpdated): UpdateRequest = updateDoc(
        event.id, mapOf(IndexedFile.ANNOTATIONS_FIELD to event.annotations)
    )

    private fun handleInvalidated(event: StorageEvent.Invalidated): BulkRequest {
        // Note: It appears that the high level rest client for elastic doesn't support delete by query yet.
        // For this reason we just search through and delete in bulk

        val request = BulkRequest()
        elasticClient.scrollThroughSearch<IndexedFile>(
            mapper,
            listOf(FILES_INDEX),

            builder = {
                source {
                    term { IndexedFile.PATH_FIELD to event.path }
                }
            },

            handler = { request.add(DeleteRequest(FILES_INDEX, DOC_TYPE, it.id)) }
        )

        return request
    }

    private fun updateDoc(id: String, updatedFields: Map<String, Any>): UpdateRequest =
        UpdateRequest(
            FILES_INDEX,
            DOC_TYPE,
            id
        ).apply {
            doc(updatedFields)
        }


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

/**
 * Scans through the file indexes and together with the storage-service fixes inconsistencies
 *
 * The storage-service is responsible for performing the diffing and emitting events to fix inconsistencies.
 *
 * This service is responsible for delivering a reliable materialized view based on the [StorageEvent] stream.
 */
class FileIndexScanner(
    private val cloud: AuthenticatedCloud,
    private val elasticClient: RestHighLevelClient
) {
    private val mapper = jacksonObjectMapper()

    fun scan() {
        // TODO If the materialized view has two files at same path, but with different id
        // Then I do not think the verifier will be able to correct the record. It will invalidate the entire
        // path, and not tell which is correct.
        // I guess this will still be eventually consistent. In the next update there will (hopefully) not be the
        // same conflict and it will add the missing one. We should probably detect this duplicate case?

        // We will end up traversing the entries it has just corrected for us.
        //
        // A timestamp for newest event is not enough to filter out new entries. We cannot rely on timestamps for
        // created (since we also get for refreshes).
        //
        // This will cause quite a lot of wasted work when we are doing large rebuilds.
        //
        // We could maybe track the first time we see a file (by id) and do queries based on that

        runBlocking {
            val queue = arrayListOf(HARDCODED_ROOT)
            while (queue.isNotEmpty()) {
                // Send up to 100 roots per request
                for (roots in queue.chunked(100).also { queue.clear() }) {
                    val rootToMaterialized = roots.map { it to scanDirectory(it) }.toMap()
                    val deliveryResponse = FileDescriptions.deliverMaterializedFileSystem.call(
                        DeliverMaterializedFileSystemRequest(rootToMaterialized),
                        cloud
                    )

                    if (deliveryResponse !is RESTResponse.Ok) {
                        throw RuntimeException(
                            "Could not deliver reference FS: " +
                                    "${deliveryResponse.status} - ${deliveryResponse.rawResponseBody}"
                        )
                    }

                    // Continue on all roots that the FS agrees on
                    val rootsToContinueOn = deliveryResponse.result.shouldContinue.filterValues { it }.keys
                    val newRoots = rootsToContinueOn.flatMap {
                        rootToMaterialized[it]!!
                            .filter { it.fileType == FileType.DIRECTORY }
                            .map { it.path }
                    }

                    queue.addAll(newRoots)
                }
            }
        }
    }

    private fun scanDirectory(directory: String): List<EventMaterializedStorageFile> {
        val items = ArrayList<EventMaterializedStorageFile>()
        elasticClient.scrollThroughSearch<IndexedFile>(
            mapper,
            listOf(ElasticIndexingService.FILES_INDEX),
            builder = {
                source {
                    bool {
                        filter = listOf(
                            term { IndexedFile.PATH_FIELD to directory },
                            term { IndexedFile.FILE_DEPTH_FIELD to directory.depth() }
                        )
                    }
                }
            },

            handler = { items.add(it.toMaterializedFile()) }
        )
        return items
    }

    companion object : Loggable {
        override val log = logger()

        private const val HARDCODED_ROOT = "/home"
    }
}

private fun String.fileName(): String = File(this).name

private fun String.depth(): Int = File(this).path.split("/").size

private fun IndexedFile.toMaterializedFile(): EventMaterializedStorageFile = EventMaterializedStorageFile(
    id,
    path,
    owner,
    fileType,
    fileTimestamps,
    size,
    checksum,
    isLink,
    linkTarget,
    linkTargetId,
    annotations,
    sensitivity
)

