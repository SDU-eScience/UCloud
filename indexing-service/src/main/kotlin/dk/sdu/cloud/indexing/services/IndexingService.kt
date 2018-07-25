package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel
import dk.sdu.cloud.storage.api.StorageEvent
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.ResourceNotFoundException
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger

data class IndexedFile(
    val id: String,
    val path: String,
    val owner: String,
    val fileType: FileType,
    val lastModified: Long,

    // TODO Last two not currently supported. See #230
    val sensitivity: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
    val annotations: List<String> = emptyList()
)

class IndexingService(
    private val elasticClient: RestHighLevelClient
) {
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

    fun migrate() {
        createIndexFromJsonResource(FILES_INDEX)
    }

    fun handleEvent(event: StorageEvent) {
        log.debug("Indexing based on storage event: $event")

        when (event) {
            is StorageEvent.CreatedOrModified -> handleCreatedOrModified(event)
            is StorageEvent.Deleted -> handleDeleted(event)
            is StorageEvent.Moved -> handleMoved(event)
        }
    }

    private fun handleCreatedOrModified(event: StorageEvent.CreatedOrModified) {
        val indexedFile = IndexedFile(
            event.id,
            event.path,
            event.owner,
            event.fileType,
            event.timestamp
        )

        elasticClient.index(IndexRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
            source(mapper.writeValueAsBytes(indexedFile), XContentType.JSON)
        })
    }

    private fun handleDeleted(event: StorageEvent.Deleted) {
        elasticClient.delete(DeleteRequest(FILES_INDEX, DOC_TYPE, event.id))
    }

    private fun handleMoved(event: StorageEvent.Moved, shouldRetry: Boolean = true) {
        try {
            elasticClient.update(UpdateRequest(FILES_INDEX, DOC_TYPE, event.id).apply {
                doc(
                    mapOf(
                        IndexedFile::path.name to event.path
                    )
                )
            })
        } catch (ex: ResourceNotFoundException) {
            log.info("Could not find document. Inserting default document instead")
            val indexedFile = IndexedFile(
                event.id,
                event.path,
                event.owner,
                // TODO We have no idea
                FileType.FILE,
                event.timestamp
            )

            try {
                elasticClient.index(IndexRequest(FILES_INDEX, DOC_TYPE, indexedFile.id).apply {
                    source(mapper.writeValueAsBytes(indexedFile), XContentType.JSON)
                })
            } catch (ex: ResourceAlreadyExistsException) {
                if (shouldRetry) {
                    Thread.sleep(100)
                    handleMoved(event, shouldRetry = false)
                } else {
                    throw RuntimeException("Could not handle moved event: $event", ex)
                }
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        private const val FILES_INDEX = "files"
        private const val DOC_TYPE = "doc"
    }
}
