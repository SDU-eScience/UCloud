package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

data class ActivityEventFilter(
    val minTimestamp: Long? = null,
    val maxTimestamp: Long? = null,
    val type: ActivityEventType? = null,
    val fileId: String? = null,
    val user: String? = null,
    val offset: Int? = null
)

data class CountAggregation(val count: Long, val maxId: Long)

interface ActivityEventDao<Session> {
    fun findByFileId(
        session: Session,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent>

    fun findByUser(
        session: Session,
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<ActivityEvent>

    fun findEvents(
        session: Session,
        items: Int,
        filter: ActivityEventFilter = ActivityEventFilter()
    ): List<ActivityEvent>

    fun countEvents(
        session: Session,
        filter: ActivityEventFilter
    ): CountAggregation

    fun insertBatch(
        session: Session,
        events: List<ActivityEvent>
    ) {
        events.forEach { insert(session, it) }
    }

    fun insert(
        session: Session,
        event: ActivityEvent
    )
}
