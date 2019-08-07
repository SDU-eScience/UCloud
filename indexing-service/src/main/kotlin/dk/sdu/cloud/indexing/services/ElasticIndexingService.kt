package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.createdAt
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.modifiedAt
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.size
import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.indexing.util.fileName
import dk.sdu.cloud.indexing.util.scrollThroughSearch
import dk.sdu.cloud.indexing.util.source
import dk.sdu.cloud.indexing.util.term
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.Requests
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger

private const val CHUNK_SIZE = 1000
private const val NOT_FOUND_STATUSCODE = 404
private const val TIMEOUT_IN_MINUTES = 5L

/**
 * An implementation of [IndexingService] using an Elasticsearch backend.
 */
class ElasticIndexingService(
    private val elasticClient: RestHighLevelClient
) : IndexingService {
    private val mapper = defaultMapper

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
            },
            RequestOptions.DEFAULT).takeIf { it.isAcknowledged } ?: throw RuntimeException("Unable to create $name index")
    }

    override fun migrate() {
        try {
            elasticClient.indices().delete(Requests.deleteIndexRequest(FILES_INDEX), RequestOptions.DEFAULT)
        } catch (ex: ElasticsearchStatusException) {
            if (ex.status().status != NOT_FOUND_STATUSCODE) throw ex
        }

        createIndexFromJsonResource(FILES_INDEX)
    }

    override fun handleEvent(event: StorageEvent) {
        log.debug("Indexing based on storage event: $event")

        @Suppress("UNUSED_VARIABLE")
        val ignored: Any? = when (event) {
            is StorageEvent.CreatedOrRefreshed -> elasticClient.update(handleCreatedOrModified(event), RequestOptions.DEFAULT)
            is StorageEvent.Deleted -> elasticClient.delete(handleDeleted(event), RequestOptions.DEFAULT)
            is StorageEvent.Moved -> elasticClient.update(handleMoved(event), RequestOptions.DEFAULT)
            is StorageEvent.SensitivityUpdated -> elasticClient.update(handleSensitivityUpdated(event), RequestOptions.DEFAULT)
            is StorageEvent.Invalidated -> elasticClient.bulk(
                handleInvalidated(event).takeIf { it.requests().isNotEmpty() } ?: return, RequestOptions.DEFAULT)
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
                is StorageEvent.Invalidated -> requests.addAll(handleInvalidated(event).requests())
            }
        }

        val failures = ArrayList<String>()
        requests.chunked(CHUNK_SIZE).forEach { chunk ->
            val request = BulkRequest()
            request.add(chunk)
            request.timeout(TimeValue.timeValueMinutes(TIMEOUT_IN_MINUTES))

            if (request.requests().isNotEmpty()) {
                val resp = elasticClient.bulk(request, RequestOptions.DEFAULT)
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

    private fun handleCreatedOrModified(event: StorageEvent.CreatedOrRefreshed): UpdateRequest =
        updateDocWithNewFile(event.file)

    private fun handleDeleted(event: StorageEvent.Deleted): DeleteRequest {
        return DeleteRequest(FILES_INDEX, DOC_TYPE, event.file.fileId)
    }

    // TODO We should only update if event timestamp is lower than current. This protects somewhat against
    //  out of order delivery.
    private fun updateDocWithNewFile(file: StorageFile): UpdateRequest {
        val indexedFile = ElasticIndexedFile(
            id = file.fileId,
            owner = file.ownerName,

            path = file.path,
            fileName = file.path.fileName(),
            fileDepth = file.path.depth(),

            fileType = file.fileType,

            size = file.size,
            fileTimestamps = Timestamps(file.modifiedAt, file.createdAt, file.modifiedAt),

            sensitivity = file.ownSensitivityLevel
        )

        return UpdateRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
            val writeValueAsBytes = mapper.writeValueAsBytes(indexedFile)
            doc(writeValueAsBytes, XContentType.JSON)
            docAsUpsert(true)
        }
    }

    private fun handleMoved(event: StorageEvent.Moved): UpdateRequest = updateDocWithNewFile(event.file)

    private fun handleSensitivityUpdated(event: StorageEvent.SensitivityUpdated): UpdateRequest =
        updateDocWithNewFile(event.file)

    private fun handleInvalidated(event: StorageEvent.Invalidated): BulkRequest {
        // Note: It appears that the high level rest client for elastic doesn't support delete by query yet.
        // For this reason we just search through and delete in bulk

        val request = BulkRequest()
        elasticClient.scrollThroughSearch<ElasticIndexedFile>(
            mapper,
            listOf(FILES_INDEX),

            builder = {
                source {
                    term { ElasticIndexedFile.PATH_FIELD to event.path }
                }
            },

            handler = { request.add(DeleteRequest(FILES_INDEX, DOC_TYPE, it.id)) }
        )

        return request
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val FILES_INDEX = "files"
        internal const val DOC_TYPE = "doc"
    }
}
