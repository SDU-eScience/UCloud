package dk.sdu.cloud.metadata.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.UserEditableProjectMetadata
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.stackTraceToString
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.fulltext.match
import org.apache.http.HttpHost
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.LoggerFactory

class ElasticMetadataService(
    elasticHost: String,
    elasticPort: Int,
    elasticScheme: String,

    private val projectService: ProjectService
) : MetadataCommandService, MetadataQueryService, MetadataAdvancedQueryService {
    private val client = RestHighLevelClient(RestClient.builder(HttpHost(elasticHost, elasticPort, elasticScheme)))

    private val mapper = jacksonObjectMapper()
    private val singleInstanceOfMicroServiceLockBadIdeaButWorksForNow = Any()
    override fun create(metadata: ProjectMetadata) {
        if (internalGetById(metadata.id) != null) throw MetadataException.Duplicate()

        client.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    override fun canEdit(user: String, projectId: String): Boolean {
        val project = projectService.findById(projectId) ?: throw MetadataException.NotFound()
        return canEdit(user, project)
    }

    private fun canEdit(user: String, project: Project): Boolean {
        return project.owner == user
    }

    override fun update(user: String, projectId: String, metadata: UserEditableProjectMetadata) {
        synchronized(singleInstanceOfMicroServiceLockBadIdeaButWorksForNow) {
            val project = projectService.findById(projectId) ?: throw MetadataException.NotFound()
            if (!canEdit(user, project)) {
                log.debug("Not allowed. Project owner is '${project.owner}' current user is '$user'")
                throw MetadataException.NotAllowed()
            }

            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!
            val existing = internalGetById(projectId) ?: throw MetadataException.NotFound()
            val newMetadata = existing.copy(
                title = if (metadata.title != null) metadata.title!! else existing.title,
                description = if (metadata.description != null) metadata.description!! else existing.description,
                license = if (metadata.license != null) metadata.license!! else existing.license,
                keywords = if (metadata.keywords != null) metadata.keywords!! else existing.keywords,
                contributors = if (metadata.contributors != null) metadata.contributors!! else existing.contributors,
                references = if (metadata.references != null) metadata.references!! else existing.references,
                grants = if (metadata.grants != null) metadata.grants!! else existing.grants,
                subjects = if (metadata.subjects != null) metadata.subjects!! else existing.subjects,
                relatedIdentifiers = if (metadata.relatedIdentifiers != null) metadata.relatedIdentifiers!! else existing.relatedIdentifiers
            )

            internalUpdate(newMetadata)
        }
    }

    private fun internalUpdate(metadata: ProjectMetadata) {
        client.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    // The correct way to update these would be to use a (stored) script in elastic to update the document
    // or, alternatively, use the optimistic concurrency control

    override fun addFiles(projectId: String, files: Set<FileDescriptionForMetadata>) {
        synchronized(singleInstanceOfMicroServiceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = internalGetById(projectId) ?: throw MetadataException.NotFound()
            internalUpdate(existing.copy(files = (existing.files.toSet() + files).toList()))
        }
    }

    override fun removeFilesById(projectId: String, files: Set<String>) {
        synchronized(singleInstanceOfMicroServiceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = internalGetById(projectId) ?: throw MetadataException.NotFound()
            internalUpdate(existing.copy(files = existing.files.toSet().filter { it.id !in files }))
        }
    }

    override fun updatePathOfFile(projectId: String, fileId: String, newPath: String) {
        synchronized(singleInstanceOfMicroServiceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = internalGetById(projectId) ?: throw MetadataException.NotFound()
            val actualFile = existing.files.toSet().find { it.id == fileId } ?: throw MetadataException.NotFound()

            val set = existing.files.toSet().filter { it.id != fileId } + actualFile.copy(path = newPath)
            internalUpdate(existing.copy(files = set.toList()))
        }
    }

    override fun removeAllFiles(projectId: String) {
        synchronized(singleInstanceOfMicroServiceLockBadIdeaButWorksForNow) {
            // TODO This is NOT correct. We have no global locking on the object and stuff may happen in-between!

            val existing = internalGetById(projectId) ?: throw MetadataException.NotFound()
            internalUpdate(existing.copy(files = emptyList()))
        }
    }

    private fun internalGetById(id: String): ProjectMetadata? {
        val getResponse = client[GetRequest(index, doc, id)]
        return getResponse?.takeIf { it.isExists }?.sourceAsBytes?.let { mapper.readValue(it) }
    }

    override fun getById(user: String, id: String): ProjectMetadata? {
        return internalGetById(id)
    }

    override fun simpleQuery(user: String, query: String, paging: NormalizedPaginationRequest): Page<ProjectMetadata> {
        val request = SearchRequest().apply {
            source(SearchSourceBuilder().apply {
                val q = bool {
                    should = listOf(
                        match { "description" to query },
                        match { "title" to query }
                    )
                }

                from(paging.itemsPerPage * paging.page)
                query(q)
            })
        }

        val response = client.search(request)
        val records = response.hits.hits.mapNotNull {
            try {
                mapper.readValue<ProjectMetadata>(it.sourceAsString)
            } catch (ex: Exception) {
                log.debug("Exception caught when de-serializing source")
                log.debug(ex.stackTraceToString())
                null
            }
        }

        return Page(response.hits.totalHits.toInt(), paging.itemsPerPage, paging.page, records)
    }

    companion object {
        private const val index = "project_metadata"
        private const val doc = "doc"

        private val log = LoggerFactory.getLogger(ElasticMetadataService::class.java)
    }
}

