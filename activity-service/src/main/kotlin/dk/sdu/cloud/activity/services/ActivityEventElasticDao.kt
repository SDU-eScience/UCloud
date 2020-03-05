package dk.sdu.cloud.activity.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.FileTransferDescription
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.CopyRequest
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.ReclassifyRequest
import dk.sdu.cloud.file.api.SimpleBulkUpload
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.share.api.Shares
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.util.*
import kotlin.collections.ArrayList

data class FavoriteFiles(
    val path: String,
    val newStatus: Boolean
)

data class DeleteActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: DeleteFileRequest
)

data class DownloadActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: DownloadByURI
)
data class FavoriteActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: List<FavoriteFiles>
)

data class MoveActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: MoveRequest
)

data class CopyActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: CopyRequest
)

data class ReclassifyActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: ReclassifyRequest
)

data class UpdateACLActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: UpdateAclRequest
)

data class UploadActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: SimpleUploadRequest
)

data class BulkUploadActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: SimpleBulkUpload
)

data class CreateDirectoryActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: CreateDirectoryRequest
)

data class UsedInAppActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: StartJobRequest
)

data class SharedActivity(
    val timestamp: Date,
    val token: SecurityPrincipal,
    val requestJson: Shares.Create.Request
)

class ActivityEventElasticDao(private val client: RestHighLevelClient): ActivityEventDao {
    override fun countEvents(filter: ActivityEventFilter): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteOldActivity(numberOfDaysInPast: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findByFilePath(pagination: NormalizedPaginationRequest, filePath: String): Page<ActivityEvent> {
        val normalizedFilePath = filePath.normalize()
        val folderOfFile = normalizedFilePath.parent()
        val request = SearchRequest(*ALL_RELEVATE_INDCIES)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                //App.start
                .should(
                    QueryBuilders.multiMatchQuery(
                        folderOfFile,
                        "requestJson.mounts.source",
                        "requestJson.parameters.*.source"
                    )
                )
                .should(
                    QueryBuilders.multiMatchQuery(
                        normalizedFilePath,
                        "requestJson.mounts.source",
                        "requestJson.parameters.*.source"
                    )
                )
                //SimpleUpload, download, updateAcl, SimpleBulkUpload, reclassify, move, copy, delete
                //createDirectory, create share, Toggle Favorite

                .should(
                    QueryBuilders.multiMatchQuery(
                        normalizedFilePath,
                        "requestJson.request.path",
                        "requestJson.files.path",
                        "requestJson.path"
                    )
                )
                .filter(
                    QueryBuilders.matchQuery(
                        "responseCode", 200
                    )
                )
        ).from(pagination.itemsPerPage*pagination.page)
            .size(pagination.itemsPerPage)
            .sort("@timestamp", SortOrder.DESC)

        request.source(source)
        val searchResponse = client.search(request, RequestOptions.DEFAULT)

        val activityEventList = arrayListOf<ActivityEvent>()

        searchResponse.hits.hits.forEach { doc ->
            when {
                doc.index.startsWith(FILES_SIMPLE_UPLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UploadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Uploaded(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.location
                    ))
                }
                doc.index.startsWith(FILES_SIMPLE_BULK_UPLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<BulkUploadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Uploaded(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.location
                    ))
                }
                doc.index.startsWith(FILES_UPDATEDACL.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UpdateACLActivity>(doc.sourceAsString)
                    val changes = ArrayList<Pair<Set<AccessRight>, String>>()
                    source.requestJson.changes.forEach { update ->
                        changes.add(Pair(update.rights, update.entity))
                    }
                    activityEventList.add(ActivityEvent.UpdatedAcl(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path,
                        changes.toList()
                    ))
                }
                doc.index.startsWith(FILES_RECLASSYFIED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<ReclassifyActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Reclassify(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path,
                        source.requestJson.sensitivity?.name!!
                    ))
                }
                doc.index.startsWith(FILES_MOVED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<MoveActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Moved(
                        source.token.username,
                        source.requestJson.newPath,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_FAVORITE_TOGGLE.dropLast(1)) -> {
                    val source = defaultMapper.readValue<FavoriteActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Favorite(
                        source.token.username,
                        source.requestJson.single().newStatus,
                        source.timestamp.time,
                        source.requestJson.single().path
                    ))
                }
                doc.index.startsWith(FILES_DOWNLOAD.dropLast(1)) -> {
                    val source = defaultMapper.readValue<DownloadActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Download(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_DELETE_FILE.dropLast(1)) -> {
                    val source = defaultMapper.readValue<DeleteActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Deleted(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_CREATE_DIR.dropLast(1)) -> {
                    val source = defaultMapper.readValue<CreateDirectoryActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.DirectoryCreated(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path
                    ))
                }
                doc.index.startsWith(FILES_COPY.dropLast(1)) -> {
                    val source = defaultMapper.readValue<CopyActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.Copy(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path,
                        source.requestJson.newPath
                    ))
                }
                doc.index.startsWith(APP_START_INDEX.dropLast(1)) -> {
                    val source = defaultMapper.readValue<UsedInAppActivity>(doc.sourceAsString)
                    source.requestJson.mounts.forEach { mount ->
                        val castedMount = mount as? FileTransferDescription
                        if (castedMount != null) {
                            if (castedMount.source == folderOfFile || castedMount.source == normalizedFilePath) {
                                activityEventList.add(
                                    ActivityEvent.UsedByApplication(
                                        source.token.username,
                                        source.timestamp.time,
                                        castedMount.source,
                                        source.requestJson.application.name,
                                        source.requestJson.application.version
                                    )
                                )
                            }
                        }
                    }
                    source.requestJson.parameters.values.forEach { param ->
                        val castedParam = param as? FileTransferDescription
                        if (castedParam != null) {
                            if (param.source == normalizedFilePath || param.source == folderOfFile) {
                                activityEventList.add(
                                    ActivityEvent.UsedByApplication(
                                        source.token.username,
                                        source.timestamp.time,
                                        param.source,
                                        source.requestJson.application.name,
                                        source.requestJson.application.version
                                    )
                                )
                            }
                        }
                    }
                }
                doc.index.startsWith(SHARES_CREATED.dropLast(1)) -> {
                    val source = defaultMapper.readValue<SharedActivity>(doc.sourceAsString)
                    activityEventList.add(ActivityEvent.SharedWith(
                        source.token.username,
                        source.timestamp.time,
                        source.requestJson.path,
                        source.requestJson.sharedWith,
                        source.requestJson.rights
                    ))
                }
            }
        }
        val numberOfItems = searchResponse.hits.totalHits?.value?.toInt()!!
        return Page(numberOfItems, pagination.itemsPerPage, pagination.page, activityEventList)
    }

    override fun findByUser(pagination: NormalizedPaginationRequest, user: String): Page<ActivityEvent> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findEvents(items: Int, filter: ActivityEventFilter): List<ActivityEvent> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

        val ALL_RELEVATE_INDCIES = arrayOf(FILES_COPY, FILES_CREATE_DIR, FILES_DELETE_FILE, FILES_DOWNLOAD,
            FILES_FAVORITE_TOGGLE, FILES_MOVED, FILES_RECLASSYFIED, FILES_UPDATEDACL, FILES_SIMPLE_BULK_UPLOAD,
            FILES_SIMPLE_UPLOAD, APP_START_INDEX, SHARES_CREATED)

    }
}
