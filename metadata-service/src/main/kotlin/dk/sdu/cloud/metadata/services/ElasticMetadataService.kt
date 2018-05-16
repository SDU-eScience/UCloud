package dk.sdu.cloud.metadata.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.ProjectMetadata
import org.apache.http.HttpHost
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType

class ElasticMetadataService : MetadataCommandService, MetadataQueryService, MetadataAdvancedQueryService {
    private val client = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
    private val mapper = jacksonObjectMapper()
    private val singleInstanceOfMicroserviceLockBadIdeaButWorksForNow = Any()

    override fun create(metadata: ProjectMetadata) {
        client.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    override fun update(metadata: ProjectMetadata) {
        client.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    // The correct way to update these would be to use a (stored) script in elastic to update the document
    // or, alternatively, use the optimistic concurrency control

    override fun addFiles(projectId: String, files: Set<FileDescriptionForMetadata>) {
        synchronized(singleInstanceOfMicroserviceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = getById(projectId) ?: throw MetadataException.NotFound()
            update(existing.copy(files = (existing.files.toSet() + files).toList()))
        }
    }

    override fun removeFilesById(projectId: String, files: Set<String>) {
        synchronized(singleInstanceOfMicroserviceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = getById(projectId) ?: throw MetadataException.NotFound()
            update(existing.copy(files = existing.files.toSet().filter { it.id !in files }))
        }
    }

    override fun updatePathOfFile(projectId: String, fileId: String, newPath: String) {
        synchronized(singleInstanceOfMicroserviceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = getById(projectId) ?: throw MetadataException.NotFound()
            val actualFile = existing.files.toSet().find { it.id == fileId } ?: throw MetadataException.NotFound()

            val set = existing.files.toSet().filter { it.id != fileId } + actualFile.copy(path = newPath)
            update(existing.copy(files = set.toList()))
        }
    }

    override fun removeAllFiles(projectId: String) {
        synchronized(singleInstanceOfMicroserviceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = getById(projectId) ?: throw MetadataException.NotFound()
            update(existing.copy(files = emptyList()))
        }
    }

    override fun getById(id: String): ProjectMetadata? {
        val getResponse = client[GetRequest(index, doc, id)]
        return getResponse?.takeIf { it.isExists }?.sourceAsBytes?.let { mapper.readValue(it) }
    }

    fun test() {
        val result = client[GetRequest(index, doc, "d96e206f-5346-4f49-82de-4f9428c2ea9e")]
        println(result)
    }

    companion object {
        private const val index = "project_metadata"
        private const val doc = "doc"
    }
}

fun main(args: Array<String>) {
    ElasticMetadataService().test()
}
