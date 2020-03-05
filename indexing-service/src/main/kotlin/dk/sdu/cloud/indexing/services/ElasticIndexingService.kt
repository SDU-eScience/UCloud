package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.defaultMapper
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
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.Requests
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger

private const val NOT_FOUND_STATUSCODE = 404

class ElasticIndexingService(
    private val elasticClient: RestHighLevelClient
) {
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
            RequestOptions.DEFAULT
        ).takeIf { it.isAcknowledged } ?: throw RuntimeException("Unable to create $name index")
    }

    fun migrate() {
        try {
            elasticClient.indices().delete(Requests.deleteIndexRequest(FILES_INDEX), RequestOptions.DEFAULT)
        } catch (ex: ElasticsearchStatusException) {
            if (ex.status().status != NOT_FOUND_STATUSCODE) throw ex
        }

        createIndexFromJsonResource(FILES_INDEX)
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

        return UpdateRequest(FILES_INDEX, indexedFile.id).apply {
            val writeValueAsBytes = mapper.writeValueAsBytes(indexedFile)
            doc(writeValueAsBytes, XContentType.JSON)
            docAsUpsert(true)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        internal const val FILES_INDEX = "files"
    }
}
