package dk.sdu.cloud.activity.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.api.type
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.project.repository.api.Repository
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.share.api.Shares
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.util.*
import kotlin.collections.ArrayList

data class AuditEntry<E>(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: E
)

class ActivityEventElasticDao(private val client: RestHighLevelClient) : ActivityEventDao {
    override fun findByFilePath(
        pagination: NormalizedPaginationRequest,
        filePath: String
    ): Page<ActivityForFrontend> {
        val normalizedFilePath = filePath.normalize()
        val request = SearchRequest(*CallWithActivity.allIndices.toTypedArray())
        val query = QueryBuilders.boolQuery()

        CallWithActivity.all.forEach { call ->
            if (call.usesAllDescendants) {
                // Drop /home and /projects
                val allParents = normalizedFilePath.parents().drop(1)

                allParents.forEach { parent ->
                    call.jsonPathToAffectedFiles.forEach { jsonPath ->
                        query.should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.$jsonPath",
                                parent
                            )
                        )
                    }
                }
            }

            call.jsonPathToAffectedFiles.forEach { jsonPath ->
                query.should(
                    QueryBuilders.matchPhraseQuery(
                        "requestJson.$jsonPath",
                        normalizedFilePath
                    )
                )
            }
        }

        query.minimumShouldMatch(1)

        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(query)
                .filter(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.rangeQuery(
                                "responseCode"
                            ).lte(299)
                        )
                        .minimumShouldMatch(1)
                )
        ).from(pagination.itemsPerPage * pagination.page)
            .size(pagination.itemsPerPage)
            .sort("@timestamp", SortOrder.DESC)

        request.source(source)

        val searchResponse = client.search(request, RequestOptions.DEFAULT)

        val activityEventList = mapEventsBasedOnIndex(
            searchResponse,
            isFileSearch = true,
            normalizedFilePath = normalizedFilePath
        ).map { ActivityForFrontend(it.type, it.timestamp, it) }

        val numberOfItems = searchResponse.hits.totalHits?.value?.toInt()!!
        return Page(numberOfItems, pagination.itemsPerPage, pagination.page, activityEventList)
    }

    private fun getIndexByType(type: ActivityEventType?): List<String> {
        return if (type != null) {
            (CallWithActivity.callsByType[type] ?: emptyList()).map { it.index }
        } else {
            CallWithActivity.allIndices
        }
    }

    private fun applyTimeFilter(filter: ActivityEventFilter): BoolQueryBuilder {
        val query = QueryBuilders.boolQuery()
        if (filter.minTimestamp != null) {
            query.filter(QueryBuilders.rangeQuery("@timestamp").gte(filter.minTimestamp))
        }
        if (filter.maxTimestamp != null) {
            query.filter(QueryBuilders.rangeQuery("@timestamp").lte(filter.maxTimestamp))
        }

        return query
    }

    override fun findProjectEvents(
        scrollSize: Int,
        filter: ActivityEventFilter,
        projectID: String,
        repos: List<Repository>
    ): List<ActivityEvent> {
        val query = applyTimeFilter(filter)
        if (filter.user != null) {
            query.filter(QueryBuilders.matchPhraseQuery("token.principal.username", filter.user))
        }

        val validRepos = repos.filter { it.name.isNotBlank() }
        if (validRepos.isEmpty()) return emptyList()

        for (repo in validRepos) {
            val repoPath = "/projects/$projectID/${repo.name}"

            CallWithActivity.all.forEach { call ->
                call.jsonPathToAffectedFiles.forEach { jsonPath ->
                    query.should(
                        QueryBuilders.matchPhrasePrefixQuery(
                            "requestJson.$jsonPath",
                            repoPath
                        )
                    )
                }
            }
        }

        query.minimumShouldMatch(1)

        val index = getIndexByType(filter.type).toTypedArray()

        val request = SearchRequest(*index)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.rangeQuery(
                                "responseCode"
                            ).lte(299).gte(200)
                        )
                        .minimumShouldMatch(1)
                )
                .filter(query)
        ).from(filter.offset ?: 0)
            .size(scrollSize)
            .sort("@timestamp", SortOrder.DESC)
        request.source(source)
        val searchResponse = client.search(request, RequestOptions.DEFAULT)
        return mapEventsBasedOnIndex(searchResponse, isUserSearch = true)

    }

    override fun findUserEvents(scrollSize: Int, filter: ActivityEventFilter): List<ActivityEvent> {
        val query = applyTimeFilter(filter)
        val index = getIndexByType(filter.type).toTypedArray()
        val userHome = "/home/${filter.user}"
        val request = SearchRequest(*index)

        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(
                    QueryBuilders.boolQuery().also { innerQuery ->
                        CallWithActivity.all.forEach { call ->
                            call.jsonPathToAffectedFiles.forEach { jsonPath ->
                                innerQuery.should(
                                    QueryBuilders.matchPhrasePrefixQuery(
                                        "requestJson.$jsonPath",
                                        userHome
                                    )
                                )
                            }
                        }
                    }
                )
                .filter(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.rangeQuery(
                                "responseCode"
                            ).lte(299).gte(200)
                        )
                        .minimumShouldMatch(1)
                )
                .filter(query)
        ).from(filter.offset ?: 0)
            .size(scrollSize)
            .sort("@timestamp", SortOrder.DESC)

        request.source(source)
        val searchResponse = client.search(request, RequestOptions.DEFAULT)
        return mapEventsBasedOnIndex(searchResponse, isUserSearch = true)
    }

    private fun mapEventsBasedOnIndex(
        searchResponse: SearchResponse,
        isFileSearch: Boolean = false,
        normalizedFilePath: String = "",
        isUserSearch: Boolean = false
    ): List<ActivityEvent> {
        val activityEventList = arrayListOf<ActivityEvent>()
        searchResponse.hits.hits.forEach { doc ->
            val responsibleMapper = CallWithActivity.all
                .find { doc.index.startsWith(it.index.dropLast(1)) } as CallWithActivity<Any>?

            if (responsibleMapper != null) {
                try {
                    val value = defaultMapper.readValue(doc.sourceAsString, responsibleMapper.typeRef)
                    activityEventList.addAll(
                        responsibleMapper.createActivityEvents(value, isUserSearch, isFileSearch, normalizedFilePath)
                    )
                } catch (ex: Throwable) {
                    log.warn("Caught exception: ${ex.stackTraceToString()}")
                }
            }
        }
        return activityEventList.toList()
    }

    companion object : Loggable {
        override val log = logger()

        sealed class CallWithActivity<AuditEvent>(
            val eventType: ActivityEventType,
            call: CallDescription<*, *, *>,
            val typeRef: TypeReference<AuditEntry<AuditEvent>>,
            val jsonPathToAffectedFiles: List<String>,
            val usesAllDescendants: Boolean = false
        ) {
            val index = "http_logs_${call.fullName.toLowerCase()}-*"

            abstract fun createActivityEvents(
                doc: AuditEntry<AuditEvent>,
                isUserSearch: Boolean,
                isFileSearch: Boolean,
                normalizedFilePath: String
            ): List<ActivityEvent>

            object FilesCopy : CallWithActivity<BulkFileAudit<CopyRequest>>(
                ActivityEventType.copy,
                FileDescriptions.copy,
                jacksonTypeRef(),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<CopyRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Copy(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request.path,
                            doc.requestJson.request.newPath
                        )
                    )
                }
            }

            object FilesCreateDirectory : CallWithActivity<CreateDirectoryRequest>(
                ActivityEventType.directoryCreated,
                FileDescriptions.createDirectory,
                jacksonTypeRef(),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<CreateDirectoryRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.DirectoryCreated(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.path
                        )
                    )
                }
            }

            object FilesDeleteFile : CallWithActivity<SingleFileAudit<DeleteFileRequest>>(
                ActivityEventType.deleted,
                FileDescriptions.deleteFile,
                jacksonTypeRef(),
                listOf(
                    "request.request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<DeleteFileRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Deleted(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesDownload : CallWithActivity<BulkFileAudit<FindByPath>>(
                ActivityEventType.download,
                FileDescriptions.download,
                jacksonTypeRef(),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<FindByPath>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Download(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesFavoriteToggle : CallWithActivity<ToggleFavoriteAudit>(
                ActivityEventType.favorite,
                FileFavoriteDescriptions.toggleFavorite,
                jacksonTypeRef(),
                listOf(
                    "files.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<ToggleFavoriteAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return doc.requestJson.files.filter { it.newStatus != null }.map {
                        ActivityEvent.Favorite(
                            doc.token.principal.username,
                            doc.requestJson.files.single().newStatus!!,
                            doc.timestamp.time,
                            doc.requestJson.files.single().path
                        )
                    }
                }
            }

            object FilesMoved : CallWithActivity<SingleFileAudit<MoveRequest>>(
                ActivityEventType.moved,
                FileDescriptions.move,
                jacksonTypeRef(),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<MoveRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Moved(
                            doc.token.principal.username,
                            doc.requestJson.request.newPath,
                            doc.timestamp.time,
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesReclassified : CallWithActivity<SingleFileAudit<ReclassifyRequest>>(
                ActivityEventType.reclassify,
                FileDescriptions.reclassify,
                jacksonTypeRef(),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<ReclassifyRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Reclassify(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request.path,
                            doc.requestJson.request.sensitivity?.name ?: "Inherit"
                        )
                    )
                }
            }

            object FilesUpdateAcl : CallWithActivity<BulkFileAudit<UpdateAclRequest>>(
                ActivityEventType.updatedACL,
                FileDescriptions.updateAcl,
                jacksonTypeRef(),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<UpdateAclRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    val changes = ArrayList<ActivityEvent.RightsAndUser>()
                    doc.requestJson.request.changes.forEach { update ->
                        changes.add(ActivityEvent.RightsAndUser(update.rights, update.entity.username))
                    }

                    return listOf(
                        ActivityEvent.UpdatedAcl(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request.path,
                            changes.toList()
                        )
                    )
                }
            }

            object FilesProjectAcl : CallWithActivity<UpdateProjectAclRequest>(
                ActivityEventType.updatedACL,
                FileDescriptions.updateProjectAcl,
                jacksonTypeRef(),
                listOf("path")
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<UpdateProjectAclRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(ActivityEvent.UpdateProjectAcl(
                        doc.token.principal.username,
                        doc.timestamp.time,
                        doc.requestJson.path,
                        doc.requestJson.project,
                        doc.requestJson.newAcl.map { ActivityEvent.ProjectAclEntry(it.group, it.rights) }
                    ))
                }
            }

            object FilesBulkUpload : CallWithActivity<BulkUploadAudit>(
                ActivityEventType.upload,
                MultiPartUploadDescriptions.simpleBulkUpload,
                jacksonTypeRef(),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkUploadAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Uploaded(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.path
                        )
                    )
                }
            }

            object FilesUpload : CallWithActivity<MultiPartUploadAudit>(
                ActivityEventType.upload,
                MultiPartUploadDescriptions.simpleUpload,
                jacksonTypeRef(),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<MultiPartUploadAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Uploaded(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.request?.path!!
                        )
                    )
                }
            }

            object ApplicationStart : CallWithActivity<StartJobRequest>(
                ActivityEventType.usedInApp,
                JobDescriptions.start,
                jacksonTypeRef(),
                listOf(
                    "mounts.source",
                    "parameters.*.source"
                ),
                usesAllDescendants = true
            ) {
                private fun checkSource(
                    element: String,
                    normalizedFilePath: String,
                    inUserSearch: Boolean = false
                ): String? {
                    val clearElement = element.removePrefix("{").removeSuffix("}")
                    if (clearElement.contains("source")) {
                        val startIndex = clearElement.indexOf("source=") + "source=".length
                        val sourceStartString = clearElement.substring(startIndex)
                        val path = sourceStartString.substring(0, sourceStartString.indexOf(", ")).normalize()
                        if (path == normalizedFilePath || inUserSearch) {
                            return path
                        }
                    }
                    return null
                }

                override fun createActivityEvents(
                    doc: AuditEntry<StartJobRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    val activityEventList = ArrayList<ActivityEvent>()

                    if (isFileSearch) {
                        doc.requestJson.mounts.forEach { mount ->
                            val path = checkSource(mount.toString(), normalizedFilePath)
                            if (path != null) {
                                activityEventList.add(
                                    ActivityEvent.SingleFileUsedByApplication(
                                        doc.token.principal.username,
                                        doc.timestamp.time,
                                        path,
                                        doc.requestJson.application.name,
                                        doc.requestJson.application.version
                                    )
                                )
                            }
                        }
                        doc.requestJson.parameters.forEach { (t, u) ->
                            if (t == "directory") {
                                val path = checkSource(u.toString(), normalizedFilePath)
                                if (path != null) {
                                    activityEventList.add(
                                        ActivityEvent.SingleFileUsedByApplication(
                                            doc.token.principal.username,
                                            doc.timestamp.time,
                                            path,
                                            doc.requestJson.application.name,
                                            doc.requestJson.application.version
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (isUserSearch) {
                        var filesUsed = ""
                        doc.requestJson.mounts.forEach { mount ->
                            val path = checkSource(mount.toString(), normalizedFilePath, inUserSearch = true)
                            if (path != null) {
                                filesUsed += "$path, "
                            }
                        }
                        doc.requestJson.parameters.forEach { (t, u) ->
                            if (t == "directory") {
                                val path = checkSource(u.toString(), normalizedFilePath, inUserSearch = true)
                                if (path != null) {
                                    filesUsed += "$path, "
                                }
                            }
                        }
                        activityEventList.add(
                            ActivityEvent.AllFilesUsedByApplication(
                                doc.token.principal.username,
                                doc.timestamp.time,
                                filesUsed,
                                doc.requestJson.application.name,
                                doc.requestJson.application.version
                            )
                        )
                    }

                    return activityEventList
                }
            }

            object ShareCreated : CallWithActivity<Shares.Create.Request>(
                ActivityEventType.sharedWith,
                Shares.create,
                jacksonTypeRef(),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<Shares.Create.Request>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.SharedWith(
                            doc.token.principal.username,
                            doc.timestamp.time,
                            doc.requestJson.path,
                            doc.requestJson.sharedWith,
                            doc.requestJson.rights
                        )
                    )
                }
            }

            companion object {
                val all by lazy { CallWithActivity::class.sealedSubclasses.map { it.objectInstance!! } }
                val allIndices by lazy { all.map { it.index } }
                val callsByType: Map<ActivityEventType, List<CallWithActivity<*>>> by lazy {
                    all.groupBy { it.eventType }
                }
            }
        }
    }
}
