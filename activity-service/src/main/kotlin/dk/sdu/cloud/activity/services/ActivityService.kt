package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventGroup
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedScrollRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ScrollResult
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ActivityService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val activityDao: ActivityEventDao<DBSession>,
    private val fileLookupService: FileLookupService
) {
    fun insertBatch(events: List<ActivityEvent>) {
        db.withTransaction { session ->
            activityDao.insertBatch(session, events)
        }
    }

    suspend fun findEventsForPath(
        pagination: NormalizedPaginationRequest,
        path: String,
        userAccessToken: String,
        causedBy: String? = null
    ): Page<ActivityEvent> {
        val fileStat = fileLookupService.lookupFile(path, userAccessToken, causedBy)
        return db.withTransaction { session ->
            activityDao.findByFileId(session, pagination, fileStat.fileId)
        }
    }

    fun findEventsForFileId(
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return db.withTransaction { session ->
            activityDao.findByFileId(session, pagination, fileId)
        }
    }

    fun findEventsForUser(
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<ActivityEvent> {
        return db.withTransaction { session ->
            activityDao.findByUser(session, pagination, user)
        }
    }

    fun browseForUser(
        scroll: NormalizedScrollRequest<Long>,
        user: String
    ): ScrollResult<ActivityEventGroup, Long> {
        TODO()
    }
}

