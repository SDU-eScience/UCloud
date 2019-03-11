package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

class ActivityService<DBSession>(
    private val activityDao: ActivityEventDao<DBSession>,
    private val fileLookupService: FileLookupService
) {
    fun insert(
        session: DBSession,
        event: ActivityEvent
    ) {
        insertBatch(session, listOf(event))
    }

    fun insertBatch(
        session: DBSession,
        events: List<ActivityEvent>
    ) {
        activityDao.insertBatch(session, events)
    }

    suspend fun findEventsForPath(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        path: String,
        userAccessToken: String,
        causedBy: String? = null
    ): Page<ActivityEvent> {
        val fileStat = fileLookupService.lookupFile(path, userAccessToken, causedBy)
        return findEventsForFileId(session, pagination, fileStat.fileId)
    }

    fun findEventsForFileId(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return activityDao.findByFileId(session, pagination, fileId)
    }

    fun findEventsForUser(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<ActivityEvent> {
        return activityDao.findByUser(session, pagination, user)
    }
}

