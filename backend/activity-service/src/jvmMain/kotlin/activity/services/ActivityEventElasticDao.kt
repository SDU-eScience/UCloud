package dk.sdu.cloud.activity.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.api.type
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.app.orchestrator.api.JobsCreateRequest
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.search.MatchQuery
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

@Serializable
data class AuditEntry<E>(
    @SerialName("@timestamp") val timestamp: String,
    val token: SecurityPrincipalToken,
    val requestJson: E,
)

data class ActivityEventFilter(
    val minTimestamp: Long? = null,
    val maxTimestamp: Long? = null,
    val type: ActivityEventType? = null,
    val user: String? = null,
    val offset: Int? = null,
)

class ActivityEventElasticDao(private val client: RestHighLevelClient) {
    fun findByFilePath(
        pagination: NormalizedPaginationRequest,
        filePath: String,
    ): Page<ActivityForFrontend> {
        /*
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
                            QueryBuilders.multiMatchQuery(
                                parent,
                                "requestJson.$jsonPath"
                            ).type(MatchQuery.Type.PHRASE_PREFIX)
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
         */
        TODO()
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

    /*
    fun findProjectEvents(
        scrollSize: Int,
        filter: ActivityEventFilter,
        projectID: String,
        repos: List<Repository>,
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

     */

    fun findUserEvents(scrollSize: Int, filter: ActivityEventFilter): List<ActivityEvent> {
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
                                    QueryBuilders.multiMatchQuery(
                                        userHome,
                                        "requestJson.$jsonPath"
                                    ).type(MatchQuery.Type.PHRASE_PREFIX)
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
        isUserSearch: Boolean = false,
    ): List<ActivityEvent> {
        val activityEventList = arrayListOf<ActivityEvent>()
        searchResponse.hits.hits.forEach { doc ->
            val responsibleMapper = CallWithActivity.all
                .find { doc.index.startsWith(it.index.dropLast(1)) } as CallWithActivity<Any>?

            if (responsibleMapper != null) {
                try {
                    val value = defaultMapper.decodeFromString(responsibleMapper.typeRef, doc.sourceAsString)
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
            val typeRef: KSerializer<AuditEntry<AuditEvent>>,
            val jsonPathToAffectedFiles: List<String>,
            val usesAllDescendants: Boolean = false,
        ) {
            val index = "http_logs_${call.fullName.toLowerCase()}-*"

            abstract fun createActivityEvents(
                doc: AuditEntry<AuditEvent>,
                isUserSearch: Boolean,
                isFileSearch: Boolean,
                normalizedFilePath: String,
            ): List<ActivityEvent>

            /*
            object FilesCopy : CallWithActivity<BulkFileAudit<CopyRequest>>(
                ActivityEventType.copy,
                FileDescriptions.copy,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<CopyRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Copy(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path,
                            doc.requestJson.request.newPath
                        )
                    )
                }
            }

            object FilesCreateDirectory : CallWithActivity<CreateDirectoryRequest>(
                ActivityEventType.directoryCreated,
                FileDescriptions.createDirectory,
                AuditEntry.serializer(serializer()),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<CreateDirectoryRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.DirectoryCreated(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.path
                        )
                    )
                }
            }

            object FilesDeleteFile : CallWithActivity<SingleFileAudit<DeleteFileRequest>>(
                ActivityEventType.deleted,
                FileDescriptions.deleteFile,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<DeleteFileRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Deleted(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesDownload : CallWithActivity<BulkFileAudit<FindByPath>>(
                ActivityEventType.download,
                FileDescriptions.download,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<FindByPath>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Download(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesFavoriteToggle : CallWithActivity<ToggleFavoriteAudit>(
                ActivityEventType.favorite,
                FileFavoriteDescriptions.toggleFavorite,
                AuditEntry.serializer(serializer()),
                listOf(
                    "files.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<ToggleFavoriteAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return doc.requestJson.files.filter { it.newStatus != null }.map {
                        ActivityEvent.Favorite(
                            doc.token.principal.username,
                            doc.requestJson.files.single().newStatus!!,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.files.single().path
                        )
                    }
                }
            }
             */

            /*
            object FilesMoved : CallWithActivity<SingleFileAudit<MoveRequest>>(
                ActivityEventType.moved,
                FileDescriptions.move,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<MoveRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Moved(
                            doc.token.principal.username,
                            doc.requestJson.request.newPath,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path
                        )
                    )
                }
            }

            object FilesReclassified : CallWithActivity<SingleFileAudit<ReclassifyRequest>>(
                ActivityEventType.reclassify,
                FileDescriptions.reclassify,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<SingleFileAudit<ReclassifyRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Reclassify(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path,
                            doc.requestJson.request.sensitivity?.name ?: "Inherit"
                        )
                    )
                }
            }

            object FilesUpdateAcl : CallWithActivity<BulkFileAudit<UpdateAclRequest>>(
                ActivityEventType.updatedACL,
                FileDescriptions.updateAcl,
                AuditEntry.serializer(serializer()),
                listOf(
                    "request.path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkFileAudit<UpdateAclRequest>>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    val changes = ArrayList<ActivityEvent.RightsAndUser>()
                    doc.requestJson.request.changes.forEach { update ->
                        changes.add(ActivityEvent.RightsAndUser(update.rights, update.entity.username))
                    }

                    return listOf(
                        ActivityEvent.UpdatedAcl(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request.path,
                            changes.toList()
                        )
                    )
                }
            }

            object FilesProjectAcl : CallWithActivity<UpdateProjectAclRequest>(
                ActivityEventType.updatedACL,
                FileDescriptions.updateProjectAcl,
                AuditEntry.serializer(serializer()),
                listOf("path")
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<UpdateProjectAclRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(ActivityEvent.UpdateProjectAcl(
                        doc.token.principal.username,
                        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                        doc.requestJson.path,
                        doc.requestJson.project,
                        doc.requestJson.newAcl.map { ActivityEvent.ProjectAclEntry(it.group, it.rights) }
                    ))
                }
            }

            object FilesBulkUpload : CallWithActivity<BulkUploadAudit>(
                ActivityEventType.upload,
                MultiPartUploadDescriptions.simpleBulkUpload,
                AuditEntry.serializer(serializer()),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<BulkUploadAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Uploaded(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.path
                        )
                    )
                }
            }

            object FilesUpload : CallWithActivity<MultiPartUploadAudit>(
                ActivityEventType.upload,
                MultiPartUploadDescriptions.simpleUpload,
                AuditEntry.serializer(serializer()),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<MultiPartUploadAudit>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.Uploaded(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.request?.path!!
                        )
                    )
                }
            }

            object ApplicationStart : CallWithActivity<JobsCreateRequest>(
                ActivityEventType.usedInApp,
                Jobs.create,
                AuditEntry.serializer(serializer()),
                listOf(
                    "resources.path",
                    "parameters.*.path"
                ),
                usesAllDescendants = true
            ) {
                private fun checkSource(
                    element: AppParameterValue,
                    normalizedFilePath: String,
                    inUserSearch: Boolean = false,
                ): String? {
                    if (element is AppParameterValue.File) {
                        if (inUserSearch) {
                            return element.path
                        }
                        if (element.path == normalizedFilePath) {
                            return element.path
                        }
                        if (normalizedFilePath.startsWith("${element.path}/")) {
                            return normalizedFilePath
                        }
                    }
                    return null
                }

                override fun createActivityEvents(
                    doc: AuditEntry<JobsCreateRequest>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    val activityEventList = ArrayList<ActivityEvent>()

                    if (isFileSearch) {
                        doc.requestJson.items.forEach { item ->
                            item.resources?.forEach { resource ->
                                val path = checkSource(resource, normalizedFilePath)
                                if (path != null) {
                                    activityEventList.add(
                                        ActivityEvent.SingleFileUsedByApplication(
                                            doc.token.principal.username,
                                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                                            path,
                                            item.application.name,
                                            item.application.version
                                        )
                                    )
                                }
                            }
                            item.parameters?.values?.forEach { parameter ->
                                val path = checkSource(parameter, normalizedFilePath)
                                if (path != null) {
                                    activityEventList.add(
                                        ActivityEvent.SingleFileUsedByApplication(
                                            doc.token.principal.username,
                                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                                            path,
                                            item.application.name,
                                            item.application.version
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (isUserSearch) {
                        var filesUsed = mutableListOf<String>()
                        doc.requestJson.items.forEach { item ->
                            item.resources?.forEach { resource ->
                                val path = checkSource(resource, normalizedFilePath, inUserSearch = true)
                                if (path != null) {
                                    filesUsed.add(path)
                                }
                            }

                            item.parameters?.values?.forEach { parameter ->
                                val path = checkSource(parameter, normalizedFilePath, inUserSearch = true)
                                if (path != null) {
                                    filesUsed.add(path)
                                }
                            }
                        }

                        doc.requestJson.items.forEach { item ->
                            filesUsed.forEach { fileUsed ->
                                activityEventList.add(
                                    ActivityEvent.AllFilesUsedByApplication(
                                        doc.token.principal.username,
                                        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                                        fileUsed,
                                        item.application.name,
                                        item.application.version
                                    )
                                )
                            }
                        }
                    }

                    return activityEventList
                }
            }

            /*
            object ShareCreated : CallWithActivity<Shares.Create.Request>(
                ActivityEventType.sharedWith,
                Shares.create,
                AuditEntry.serializer(serializer()),
                listOf(
                    "path"
                )
            ) {
                override fun createActivityEvents(
                    doc: AuditEntry<Shares.Create.Request>,
                    isUserSearch: Boolean,
                    isFileSearch: Boolean,
                    normalizedFilePath: String,
                ): List<ActivityEvent> {
                    return listOf(
                        ActivityEvent.SharedWith(
                            doc.token.principal.username,
                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(doc.timestamp)).toEpochMilli(),
                            doc.requestJson.path,
                            doc.requestJson.sharedWith,
                            doc.requestJson.rights
                        )
                    )
                }
            }
             */
             */

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
