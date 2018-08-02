package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.api.SearchResult
import dk.sdu.cloud.indexing.util.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.storage.api.*
import kotlinx.coroutines.experimental.joinAll
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.fulltext.match_phrase_prefix
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilder
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
     * Example: /a/b/c will have a depth of 3 and /a/b/c/d will have a depth of 4 and / will have a depth of 0
     */
    val fileDepth: Int,

    val fileType: FileType,

    val size: Long,
    val fileTimestamps: Timestamps,
    val checksum: FileChecksum,

    val fileIsLink: Boolean,
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
        val FILE_NAME_KEYWORD = IndexedFile::fileName.name + ".keyword"
        val OWNER_FIELD = IndexedFile::owner.name
        val FILE_DEPTH_FIELD = IndexedFile::fileDepth.name
        val FILE_TYPE_FIELD = IndexedFile::fileType.name

        val SIZE_FIELD = IndexedFile::size.name
        val FILE_TIMESTAMPS_FIELD = IndexedFile::fileTimestamps.name
        val CHECKSUM_FIELD = IndexedFile::checksum.name

        val FILE_IS_LINK_FIELD = IndexedFile::fileIsLink.name
        val LINK_TARGET_FIELD = IndexedFile::linkTarget.name
        val LINK_TARGET_ID_FIELD = IndexedFile::linkTargetId.name

        val SENSITIVITY_FIELD = IndexedFile::sensitivity.name

        val ANNOTATIONS_FIELD = IndexedFile::annotations.name
    }
}

interface Migratable {
    fun migrate() {}
}

fun EventMaterializedStorageFile.toExternalResult(): SearchResult = SearchResult(path, fileType)

data class BulkIndexingResponse(
    val failures: List<String>
)

interface IndexingService : Migratable {
    fun handleEvent(event: StorageEvent)

    fun bulkHandleEvent(events: List<StorageEvent>): BulkIndexingResponse {
        events.forEach { handleEvent(it) }
        return BulkIndexingResponse(emptyList())
    }
}

interface IndexQueryService {
    fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile>

    fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        fileType: FileType?,
        lastModified: Long?,
        sensitivity: List<SensitivityLevel>?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile>

    fun findFileByIdOrNull(id: String): EventMaterializedStorageFile?
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
            is StorageEvent.Invalidated -> elasticClient.bulk(
                handleInvalidated(event).takeIf { it.requests().isNotEmpty() } ?: return
            )
        }
    }

    override fun bulkHandleEvent(events: List<StorageEvent>): BulkIndexingResponse {
        val requests = ArrayList<DocWriteRequest<*>>()

        events.forEach { event ->
            @Suppress("UNUSED_VARIABLE")
            val ignored: Any? = when (event) {
                is StorageEvent.CreatedOrRefreshed -> requests.add(handleCreatedOrModified(event))
                is StorageEvent.Deleted -> requests.add(handleDeleted(event))
                is StorageEvent.Moved -> requests.add(handleMoved(event))
                is StorageEvent.SensitivityUpdated -> requests.add(handleSensitivityUpdated(event))
                is StorageEvent.AnnotationsUpdated -> requests.add(handleAnnotationsUpdated(event))
                is StorageEvent.Invalidated -> requests.addAll(handleInvalidated(event).requests())
            }
        }

        val failures = ArrayList<String>()
        requests.chunked(1000).forEach { chunk ->
            val request = BulkRequest()
            request.add(chunk)
            request.timeout(TimeValue.timeValueMinutes(5))

            if (request.requests().isNotEmpty()) {
                val resp = elasticClient.bulk(request)
                failures.addAll(resp.items.filter { it.isFailed }.map { it.failureMessage })
            }
        }

        if (failures.isNotEmpty()) {
            log.info("${failures.size} failures occurred while handling bulk indexing")
            if (log.isDebugEnabled) {
                failures.forEachIndexed { i, failure ->
                    log.debug("  $i: $failure")
                }
            }
        }
        return BulkIndexingResponse(failures)
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

            fileIsLink = event.isLink,
            linkTarget = event.linkTarget,
            linkTargetId = event.linkTargetId,

            sensitivity = event.sensitivityLevel,

            annotations = event.annotations
        )

        return UpdateRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
            val writeValueAsBytes = mapper.writeValueAsBytes(indexedFile)
            doc(writeValueAsBytes, XContentType.JSON)
            docAsUpsert(true)
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

    override fun findFileByIdOrNull(id: String): EventMaterializedStorageFile? {
        return elasticClient[GetRequest(FILES_INDEX, DOC_TYPE, id)]
            ?.takeIf { it.isExists }
            ?.let { mapper.readValue<IndexedFile>(it.sourceAsString) }
            ?.toMaterializedFile()
    }

    override fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> = elasticClient
        .search<IndexedFile>(mapper, paging, FILES_INDEX) {
            sort(IndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = listOf(
                    match_phrase_prefix {
                        IndexedFile.FILE_NAME_FIELD to {
                            this.query = query
                            max_expansions = 10
                        }
                    },

                    term {
                        boost = 0.5f
                        IndexedFile.OWNER_FIELD to query
                    }
                )

                filter {
                    terms {
                        IndexedFile.PATH_FIELD to roots
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

        elasticClient.search<IndexedFile>(mapper, paging, FILES_INDEX) {
            sort(IndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = ArrayList<QueryBuilder>().apply {
                    if (name != null) {
                        add(match_phrase_prefix {
                            IndexedFile.FILE_NAME_FIELD to {
                                query = name
                                max_expansions= 10
                            }
                        })
                    }

                    if (owner != null) {
                        add(term {
                            IndexedFile.OWNER_FIELD to owner
                        })
                    }

                    if (fileType != null) {
                        add(term {
                            IndexedFile.FILE_TYPE_FIELD to fileType.name
                        })
                    }

                    if (sensitivity != null) {
                        add(terms {
                            IndexedFile.SENSITIVITY_FIELD to sensitivity.map { it.name }
                        })
                    }

                    if (annotations != null) {
                        add(terms {
                            IndexedFile.ANNOTATIONS_FIELD to annotations
                        })
                    }
                }

                filter {
                    terms { IndexedFile.PATH_FIELD to roots }
                }
            }.also {
                it.minimumShouldMatch(1)
            }
        }

        TODO()
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
        // We will end up traversing the entries it has just corrected for us.
        // Although, some are likely to be missed since we do a breadth-first scan.

        // TODO It is possible to have orphaned files in the index.
        // This can happen due to Invalidated not being keyed correctly (its not possible to key it correctly).
        // In that case we may invalidated a directory before all events in the queue for the affected files are
        // processed.
        //
        // These won't be delivered to the FS, and as a result they won't be deleted. We should probably just detect
        // them during a scan and delete them. I see no reason to notify the storage service about it (the event
        // stream was correct).

        runBlocking {
            val queueLock = Any()
            val queue = arrayListOf(HARDCODED_ROOT)
            while (queue.isNotEmpty()) {
                // Send up to 100 roots per request
                val queueInChunks = queue.chunked(100).also { queue.clear() }

                queueInChunks.map { roots ->
                    launch {
                        val rootToMaterialized = HashMap<String, List<EventMaterializedStorageFile>>()
                        roots.groupBy { it.depth() }.forEach {
                            rootToMaterialized.putAll(scanDirectoriesOfDepth(it.key, it.value))
                        }

                        // TODO JSON payload can become gigantic with 100 roots.
                        // Will increase memory usage by _a lot_
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
                                .filter { it.fileType == FileType.DIRECTORY && !it.isLink }
                                .map { it.path }
                        }

                        synchronized(queueLock) {
                            queue.addAll(newRoots)
                        }
                    }
                }.joinAll()
            }
        }
    }

    private fun scanDirectoriesOfDepth(
        depth: Int,
        directories: List<String>
    ): Map<String, List<EventMaterializedStorageFile>> {
        lazyAssert("directories need to be of the same depth") {
            directories.all { it.depth() == depth }
        }

        log.debug("Scanning the following directories together:")
        log.debug('[' + directories.joinToString(", ") { "\"$it\"" } + ']')

        val items = ArrayList<EventMaterializedStorageFile>()
        elasticClient.scrollThroughSearch<IndexedFile>(
            mapper,
            listOf(ElasticIndexingService.FILES_INDEX),
            builder = {
                source {
                    bool {
                        filter = listOf(
                            terms { IndexedFile.PATH_FIELD to directories },

                            // We want the files stored in it, thus we have to increment the depth by
                            // one (to get its direct children)
                            term { IndexedFile.FILE_DEPTH_FIELD to depth + 1 }
                        )
                    }
                }
            },

            handler = { items.add(it.toMaterializedFile()) }
        )

        return items.groupBy { it.path.parent() }
    }

    companion object : Loggable {
        override val log = logger()

        private const val HARDCODED_ROOT = "/home"
    }
}

// TODO A lot of these are likely to fail under Windows.

private fun String.fileName(): String = File(this).name

private fun String.depth(): Int = split("/").size - 1

private fun String.parent(): String = File(this).parent

private fun IndexedFile.toMaterializedFile(): EventMaterializedStorageFile = EventMaterializedStorageFile(
    id,
    path,
    owner,
    fileType,
    fileTimestamps,
    size,
    checksum,
    fileIsLink,
    linkTarget,
    linkTargetId,
    annotations,
    sensitivity
)

