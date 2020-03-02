package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.Logger

class ActivityEventElasticDao(client: RestHighLevelClient): ActivityEventDao {
    override fun countEvents(filter: ActivityEventFilter): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteOldActivity(numberOfDaysInPast: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findByFilePath(pagination: NormalizedPaginationRequest, filePath: String): Page<ActivityEvent> {
        val normalizedFilePath = filePath.normalize()
        val folderOfFile = normalizedFilePath.parent()
        val request = SearchRequest(APP_START_INDEX, FILES_INDICES)
        val source = SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                //App.start
                .should(
                    QueryBuilders.matchQuery(
                        "requestJson.mounts.source", folderOfFile
                    )
                )
                //SimpleUpload, download, updateAcl,
                .should(
                    QueryBuilders.matchQuery(
                        "requestJson.request.path", normalizedFilePath
                    )
                )
                //createDirectory
                .should(
                    QueryBuilders.matchQuery(
                        "requestJson.path", normalizedFilePath
                    )
                )
        ).from(pagination.itemsPerPage*pagination.page)
            .size(pagination.itemsPerPage)
            .sort("@timestamp", SortOrder.DESC)
    }

    override fun findByUser(pagination: NormalizedPaginationRequest, user: String): Page<ActivityEvent> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findEvents(items: Int, filter: ActivityEventFilter): List<ActivityEvent> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun insert(event: ActivityEvent) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun insertBatch(events: List<ActivityEvent>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object: Loggable {
        override val log = logger()
        const val FILES_COPY = "http_logs_files.copy*"
        const val FILES_CREATE_DIR = "http_logs_files.createdirectory*"
        const val FILES_DELETE_FILE = "http_logs_files.deletefile*"
        const val FILES_DOWNLOAD = "http_logs_files.download*"
        const val FILES_FAVORITE_TOGGLE = "http_logs_files.favorite.togglefavorite*"
        const val APP_START_INDEX = "http_logs_hpc.jobs.start*"
    }
}
