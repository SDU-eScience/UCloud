package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.indexing.util.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.file.api.StorageEvent
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger
import java.util.ArrayList

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
        val indexedFile = ElasticIndexedFile(
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
            ElasticIndexedFile.PATH_FIELD to event.path,
            ElasticIndexedFile.FILE_NAME_FIELD to event.path.fileName(),
            ElasticIndexedFile.FILE_DEPTH_FIELD to event.path.depth()
        )
    )

    private fun handleSensitivityUpdated(event: StorageEvent.SensitivityUpdated): UpdateRequest = updateDoc(
        event.id, mapOf(
            ElasticIndexedFile.SENSITIVITY_FIELD to event.sensitivityLevel
        )
    )

    private fun handleAnnotationsUpdated(event: StorageEvent.AnnotationsUpdated): UpdateRequest = updateDoc(
        event.id, mapOf(ElasticIndexedFile.ANNOTATIONS_FIELD to event.annotations)
    )

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