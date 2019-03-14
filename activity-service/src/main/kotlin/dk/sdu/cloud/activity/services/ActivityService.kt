package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventGroup
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.type
import dk.sdu.cloud.service.Loggable
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
        scroll: NormalizedScrollRequest<Int>,
        user: String,
        collapseThreshold: Int
    ): ScrollResult<ActivityEventGroup, Int> {
        return db.withTransaction { session ->
            val filter = ActivityEventFilter(
                offset = scroll.offset,
                user = user
            )

            val allEvents = activityDao.findEvents(
                session,
                scroll.scrollSize,
                filter
            )

            data class GroupBuilder(val type: ActivityEventType, val timestamp: Long) {
                val items = ArrayList<ActivityEvent>()
            }

            var currentGroup: GroupBuilder? = null
            val groupBuilders = ArrayList<GroupBuilder>()

            fun finalizeGroup() {
                val group = currentGroup ?: return
                groupBuilders.add(group)
            }

            for (event in allEvents) {
                if (currentGroup == null || currentGroup.type != event.type || (currentGroup.timestamp - event.timestamp) > GROUP_TIME_THRESHOLD) {
                    if (currentGroup != null) finalizeGroup()

                    currentGroup = GroupBuilder(event.type, event.timestamp).also {
                        it.items.add(event)
                    }
                } else {
                    currentGroup.items.add(event)
                }
            }
            finalizeGroup()

            val groups = groupBuilders.mapIndexed { i, group ->
                val nextGroup = groupBuilders.getOrNull(i + 1)
                val minTimestamp = nextGroup?.timestamp ?: group.timestamp - GROUP_TIME_THRESHOLD

                if (group.items.size > collapseThreshold) {
                    val numberOfEvents = activityDao.countEvents(session, filter.let {
                        it.copy(
                            type = group.type,
                            maxTimestamp = group.timestamp,
                            minTimestamp = listOfNotNull(
                                it.minTimestamp,
                                minTimestamp
                            ).min()!!
                        )
                    })
                    val items = group.items.take(collapseThreshold)

                    ActivityEventGroup(
                        group.type,
                        group.timestamp,
                        numberOfEvents - items.size,
                        items
                    )
                } else {
                    ActivityEventGroup(
                        group.type,
                        group.timestamp,
                        null,
                        group.items
                    )
                }
            }

            val returnedRows = groups.sumBy { it.items.size }

            // Note: We provide no strict guarantees that this will actually skip the correct stuff. The other
            // settings, such as collapseAt GROUP_TIME_THRESHOLD are meant to make it unlikely we skip
            // significant portions. Setting collapseAt = 0 will allow the user to see all events.
            val skippedRows: Int = groups.sumBy { it.numberOfHiddenResults?.toInt() ?: 0 }

            log.debug("Returned rows: $returnedRows. Skipped: $skippedRows. All rows: ${allEvents.size}")

            val nextOffset = returnedRows + skippedRows + (scroll.offset ?: 0)

            ScrollResult(groups, nextOffset, endOfScroll = groups.isEmpty())
        }
    }

    companion object : Loggable {
        const val GROUP_TIME_THRESHOLD = 1000L * 60 * 5
        override val log = logger()
    }
}

