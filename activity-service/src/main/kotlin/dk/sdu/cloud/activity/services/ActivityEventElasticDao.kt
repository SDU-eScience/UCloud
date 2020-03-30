package dk.sdu.cloud.activity.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.api.type
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.BulkUploadAudit
import dk.sdu.cloud.file.api.CopyRequest
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.MultiPartUploadAudit
import dk.sdu.cloud.file.api.ReclassifyRequest
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
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

data class DeleteActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: SingleFileAudit<DeleteFileRequest>
)

data class DownloadActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: BulkFileAudit<FindByPath>
)
data class FavoriteActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: ToggleFavoriteAudit
)

data class MoveActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: SingleFileAudit<MoveRequest>
)

data class CopyActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: SingleFileAudit<CopyRequest>
)

data class ReclassifyActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: SingleFileAudit<ReclassifyRequest>
)

data class UpdateACLActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: SingleFileAudit<UpdateAclRequest>
)

data class UploadActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: MultiPartUploadAudit
)

data class BulkUploadActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: BulkUploadAudit
)

data class CreateDirectoryActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: CreateDirectoryRequest
)

data class UsedInAppActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: StartJobRequest
)

data class SharedActivity(
    @get:JsonProperty("@timestamp") val timestamp: Date,
    val token: SecurityPrincipalToken,
    val requestJson: Shares.Create.Request
)

class ActivityEventElasticDao(private val client: RestHighLevelClient): ActivityEventDao {
    override fun findByFilePath(pagination: NormalizedPaginationRequest, filePath: String): Page<ActivityForFrontend> {
        val normalizedFilePath = filePath.normalize()
        val request = SearchRequest(*ALL_RELEVANT_INDICES)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(
                    QueryBuilders.boolQuery()
                        //AppStart
                        .should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.mounts.source",
                                normalizedFilePath
                            )
                        )
                        .should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.parameters.*.source",
                                normalizedFilePath
                            )
                        )
                        //SimpleUpload, download, updateAcl, SimpleBulkUpload, reclassify, move, copy, delete
                        //createDirectory, create share, Toggle Favorite
                        .should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.files.path",
                                normalizedFilePath
                            )
                        )
                        .should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.path",
                                normalizedFilePath
                            )
                        )
                        .should(
                            QueryBuilders.matchPhraseQuery(
                                "requestJson.request.path",
                                normalizedFilePath
                            )
                        )
                        .minimumShouldMatch(1)

                )
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

    private fun getIndexByType(type: ActivityEventType?): Array<String> {
        return if (type != null) {
            when (type){
                ActivityEventType.download -> arrayOf(FILES_DOWNLOAD)
                ActivityEventType.favorite -> arrayOf(FILES_FAVORITE_TOGGLE)
                ActivityEventType.moved -> arrayOf(FILES_MOVED)
                ActivityEventType.deleted -> arrayOf(FILES_DELETE_FILE)
                ActivityEventType.usedInApp -> arrayOf(APP_START_INDEX)
                ActivityEventType.directoryCreated -> arrayOf(FILES_CREATE_DIR)
                ActivityEventType.updatedACL -> arrayOf(FILES_UPDATEDACL)
                ActivityEventType.upload -> arrayOf(FILES_SIMPLE_UPLOAD, FILES_SIMPLE_BULK_UPLOAD)
                ActivityEventType.reclassify -> arrayOf(FILES_RECLASSYFIED)
                ActivityEventType.copy -> arrayOf(FILES_COPY)
                ActivityEventType.sharedWith -> arrayOf(SHARES_CREATED)
                ActivityEventType.allUsedInApp -> arrayOf(APP_START_INDEX)
            }
        } else {
            ALL_RELEVANT_INDICES
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
        projectID: String
    ): List<ActivityEvent> {
        val query = applyTimeFilter(filter)
        if (filter.user != null) {
            query.filter(QueryBuilders.matchPhraseQuery("token.principal.username", filter.user))
        }
        val index = getIndexByType(filter.type)

        val request = SearchRequest(*index)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchPhraseQuery(
                                "project",
                                projectID
                            )
                        )
                )
                .filter(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.rangeQuery(
                                "responseCode"
                            ).lte(299)
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
        val index = getIndexByType(filter.type)

        val request = SearchRequest(*index)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .filter(
                    QueryBuilders.boolQuery()
                        .must(
                            QueryBuilders.matchPhraseQuery(
                                "token.principal.username",
                                filter.user
                            )
                        )
                )
                .mustNot(
                    QueryBuilders.existsQuery(
                        "project"
                    )
                )
                .filter(
                    QueryBuilders.boolQuery()
                        .should(
                            QueryBuilders.rangeQuery(
                                "responseCode"
                            ).lte(299)
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
            when {
                doc.index.startsWith(FILES_SIMPLE_UPLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UploadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Uploaded(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request?.path!!
                    ))
                }
                doc.index.startsWith(FILES_SIMPLE_BULK_UPLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<BulkUploadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Uploaded(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_UPDATEDACL.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UpdateACLActivity>(doc.sourceAsString)
                    val changes = ArrayList<ActivityEvent.RightsAndUser>()
                    source.requestJson.request.changes.forEach { update ->
                        changes.add(ActivityEvent.RightsAndUser(update.rights, update.entity))
                    }
                    activityEventList.add(ActivityEvent.UpdatedAcl(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request.path,
                        changes.toList()
                    ))
                }
                doc.index.startsWith(FILES_RECLASSYFIED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<ReclassifyActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Reclassify(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request.path,
                        source.requestJson.request.sensitivity?.name ?: "Inherit"
                    ))
                }
                doc.index.startsWith(FILES_MOVED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<MoveActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Moved(
                        source.token.principal.username,
                        source.requestJson.request.newPath,
                        source.timestamp.time,
                        source.requestJson.request.path
                    ))
                }
                doc.index.startsWith(FILES_FAVORITE_TOGGLE.dropLast(1)) -> {
                    val source = defaultMapper.readValue<FavoriteActivity>(doc.sourceAsString)
                    if (source.requestJson.files.single().newStatus != null) {
                        activityEventList.add(ActivityEvent.Favorite(
                            source.token.principal.username,
                            source.requestJson.files.single().newStatus!!,
                            source.timestamp.time,
                            source.requestJson.files.single().path
                        ))
                    }
                }
                doc.index.startsWith(FILES_DOWNLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<DownloadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Download(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request.path
                    ))
                }
                doc.index.startsWith(FILES_DELETE_FILE.dropLast(1)) -> {
                    val source = defaultMapper.readValue<DeleteActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Deleted(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request.path
                    ))
                }
                doc.index.startsWith(FILES_CREATE_DIR.dropLast(1)) -> {
                    val source = defaultMapper.readValue<CreateDirectoryActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.DirectoryCreated(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_COPY.dropLast(1)) -> {
                    val source = defaultMapper.readValue<CopyActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Copy(
                        source.token.principal.username,
                        source.timestamp.time,
                        source.requestJson.request.path,
                        source.requestJson.request.newPath
                    ))
                }
                doc.index.startsWith(APP_START_INDEX.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UsedInAppActivity>(doc.sourceAsString)
                    if (isFileSearch) {
                        source.requestJson.mounts.forEach { mount ->
                            val path = checkSource(mount.toString(), normalizedFilePath)
                            if (path != null) {
                                activityEventList.add(
                                    ActivityEvent.SingleFileUsedByApplication(
                                        source.token.principal.username,
                                        source.timestamp.time,
                                        path,
                                        source.requestJson.application.name,
                                        source.requestJson.application.version
                                    )
                                )
                            }
                        }
                        source.requestJson.parameters.forEach { (t, u) ->
                            if (t == "directory") {
                                val path = checkSource(u.toString(), normalizedFilePath)
                                if (path != null ) {
                                    activityEventList.add(
                                        ActivityEvent.SingleFileUsedByApplication(
                                            source.token.principal.username,
                                            source.timestamp.time,
                                            path,
                                            source.requestJson.application.name,
                                            source.requestJson.application.version
                                        )
                                    )
                                }
                            }
                        }

                    }
                    if (isUserSearch) {
                        var filesUsed = ""
                        source.requestJson.mounts.forEach { mount ->
                            val path = checkSource(mount.toString(), normalizedFilePath, inUserSearch = true)
                            if (path != null) {
                                filesUsed += "$path, "
                            }
                        }
                        source.requestJson.parameters.forEach { (t, u) ->
                            if (t == "directory") {
                                val path = checkSource(u.toString(), normalizedFilePath, inUserSearch = true)
                                if (path != null) {
                                    filesUsed += "$path, "
                                }
                            }
                        }
                        activityEventList.add(
                            ActivityEvent.AllFilesUsedByApplication(
                                source.token.principal.username,
                                source.timestamp.time,
                                filesUsed,
                                source.requestJson.application.name,
                                source.requestJson.application.version
                            )
                        )

                    }
                }
                doc.index.startsWith(SHARES_CREATED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<SharedActivity>(doc.sourceAsString)
                    activityEventList.add(
                        ActivityEvent.SharedWith(
                            source.token.principal.username,
                            source.timestamp.time,
                            source.requestJson.path,
                            source.requestJson.sharedWith,
                            source.requestJson.rights
                        )
                    )
                }
            }
        }
        return activityEventList.toList()
    }

    //Definitely not a good way to check source!!
    private fun checkSource(element: String, normalizedFilePath: String, inUserSearch: Boolean = false): String? {
        val clearElement = element.removePrefix("{").removeSuffix("}")
        if (clearElement.contains("source")) {
            val startIndex = clearElement.indexOf("source=")+"source=".length
            val sourceStartString = clearElement.substring(startIndex)
            val path = sourceStartString.substring(0, sourceStartString.indexOf(", ")).normalize()
            if (path == normalizedFilePath || inUserSearch) {
                return path
            }
        }
        return null
    }

    companion object: Loggable {
        override val log = logger()
        const val FILES_COPY = "http_logs_files.copy-*" //requestJson.request.path
        const val FILES_CREATE_DIR = "http_logs_files.createdirectory-*" //requestJson.path
        const val FILES_DELETE_FILE = "http_logs_files.deletefile-*" //requestJson.request.path
        const val FILES_DOWNLOAD = "http_logs_files.download-*" //requestJson.request.path
        const val FILES_FAVORITE_TOGGLE = "http_logs_files.favorite.togglefavorite-*" //requestJson.files.path
        const val FILES_MOVED = "http_logs_files.move-*" //requestJson.request.path
        const val FILES_RECLASSYFIED = "http_logs_files.reclassify-*" //requestJson.request.path
        const val FILES_UPDATEDACL = "http_logs_files.updateacl-*" //requestJson.request.path
        const val FILES_SIMPLE_BULK_UPLOAD = "http_logs_files.upload.simplebulkupload-*" //requestJson.request.path
        const val FILES_SIMPLE_UPLOAD = "http_logs_files.upload.simpleupload-*" //requestJson.request.path
        const val APP_START_INDEX = "http_logs_hpc.jobs.start-*" //requestJson.mounts.source
        const val SHARES_CREATED = "http_logs_shares.create-*" //requestJson.path

        val ALL_RELEVANT_INDICES = arrayOf(FILES_COPY, FILES_CREATE_DIR, FILES_DELETE_FILE, FILES_DOWNLOAD,
            FILES_FAVORITE_TOGGLE, FILES_MOVED, FILES_RECLASSYFIED, FILES_UPDATEDACL, FILES_SIMPLE_BULK_UPLOAD,
            FILES_SIMPLE_UPLOAD, APP_START_INDEX, SHARES_CREATED)

    }
}
