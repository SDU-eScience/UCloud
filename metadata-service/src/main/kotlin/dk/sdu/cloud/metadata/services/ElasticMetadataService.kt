package dk.sdu.cloud.metadata.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.UserEditableProjectMetadata
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.stackTraceToString
import mbuhot.eskotlin.query.fulltext.match
import mbuhot.eskotlin.query.term.match_all
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.LoggerFactory

private const val MIN_GRAM_FOR_TOKENIZER = 2
private const val MAX_GRAM_FOR_TOKENIZER = 10
private const val MAX_ALIVE_TIME_IN_MINUTES = 5L

class ElasticMetadataService(
    private val elasticClient: RestHighLevelClient,
    private val projectService: ProjectService<*>
) : MetadataCommandService, MetadataQueryService, MetadataAdvancedQueryService {
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // The correct way to update these would be to use a (stored) script in elastic to update the document
    // or, alternatively, use the optimistic concurrency control
    private val singleInstanceOfMicroServiceLockBadIdeaButWorksForNow = Any()

    override fun create(metadata: ProjectMetadata) {
        val id = metadata.id.toLongOrNull() ?: throw MetadataException.NotFound()
        if (internalGetById(id) != null) throw MetadataException.Duplicate()

        elasticClient.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    override fun canEdit(user: String, projectId: Long): Boolean {
        val project = projectService.findById(projectId) ?: throw MetadataException.NotFound()
        return canEdit(user, project)
    }

    private fun canEdit(user: String, project: Project): Boolean {
        return project.owner == user
    }

    override fun update(user: String, projectId: Long, metadata: UserEditableProjectMetadata) {
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
                relatedIdentifiers = if (metadata.relatedIdentifiers != null) metadata.relatedIdentifiers!!
                else existing.relatedIdentifiers
            )

            internalUpdate(newMetadata)
        }
    }

    private fun internalUpdate(metadata: ProjectMetadata) {
        elasticClient.index(
            IndexRequest(index, doc, metadata.id).apply {
                source(mapper.writeValueAsString(metadata), XContentType.JSON)
            }
        )
    }

    private fun internalGetById(id: Long): ProjectMetadata? {
        val getResponse = elasticClient[GetRequest(index, doc, id.toString())]
        return getResponse?.takeIf { it.isExists }?.sourceAsBytes?.let { mapper.readValue(it) }
    }

    override fun getById(user: String, id: Long): ProjectMetadata? {
        return internalGetById(id)
    }

    override fun simpleQuery(user: String, query: String, paging: NormalizedPaginationRequest): Page<ProjectMetadata> {
        val request = SearchRequest(index).apply {
            source(SearchSourceBuilder().apply {
                val q = match { "full_search" to query }

                from(paging.itemsPerPage * paging.page)
                query(q)
            })
        }

        val response = elasticClient.search(request)
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

    private fun internalDelete(projectId: Long) {
        elasticClient.delete(DeleteRequest(index, doc, projectId.toString()))
    }

    override fun delete(user: String, projectId: Long) {
        if (canEdit(user, projectId)) {
            internalDelete(projectId)
        } else {
            throw MetadataException.NotAllowed()
        }
    }

    fun initializeElasticSearch() {
        try {
            elasticClient.indices().delete(DeleteIndexRequest(index))
        } catch (ex: Exception) {
            log.debug(ex.stackTraceToString())
        }

        try {
            val request = CreateIndexRequest(index)
            request.settings(
                Settings.builder()
                    .put("analysis.analyzer.autocomplete.tokenizer", "autocomplete")
                    .put("analysis.analyzer.autocomplete.filter", "lowercase")
                    .put("analysis.analyzer.autocomplete_search.tokenizer", "lowercase")
                    .put("analysis.tokenizer.autocomplete.type", "edge_ngram")
                    .put("analysis.tokenizer.autocomplete.min_gram", MIN_GRAM_FOR_TOKENIZER)
                    .put("analysis.tokenizer.autocomplete.max_gram", MAX_GRAM_FOR_TOKENIZER)
                    .put("analysis.tokenizer.autocomplete.token_chars", "letter")
            )

            request.mapping(
                doc, """
        {
          "properties": {
            "title": {
              "type":  "text",
              "copy_to": "full_search"
            },
            "description": {
              "type":  "text",
              "copy_to": "full_search"
            },
            "keywords": {
              "type":  "text",
              "copy_to": "full_search"
            },
            "notes": {
              "type":  "text",
              "copy_to": "full_search"
            },
            "full_search": {
              "type":  "text",
              "analyzer" : "autocomplete",
              "search_analyzer" : "autocomplete_search"
            }
          }
        }
        """, XContentType.JSON
            )

            elasticClient.indices().create(request)
        } catch (ex: Exception) {
            log.debug("Exception caught when creating settings and mapping for index")
            log.debug(ex.stackTraceToString())
        }
    }

    private fun listAllProjectsRaw(): List<Map<String, *>> {
        val collectedResults = ArrayList<Map<String, *>>()
        var results = elasticClient.search(SearchRequest(index).apply {
            scroll(TimeValue.timeValueMinutes(MAX_ALIVE_TIME_IN_MINUTES))

            source(
                SearchSourceBuilder().apply {
                    val q = match_all { }
                    query(q)
                }
            )
        })

        while (results.hits.hits.isNotEmpty()) {
            collectedResults.addAll(
                results.hits.filter { it.hasSource() }.mapNotNull { it.sourceAsMap }
            )

            results = elasticClient.searchScroll(SearchScrollRequest(results.scrollId).apply {
                scroll(
                    TimeValue.timeValueMinutes(MAX_ALIVE_TIME_IN_MINUTES)
                )
            })
        }

        return collectedResults
    }

    companion object {
        private const val index = "project_metadata"
        private const val doc = "doc"

        private val log = LoggerFactory.getLogger(ElasticMetadataService::class.java)
    }
}

