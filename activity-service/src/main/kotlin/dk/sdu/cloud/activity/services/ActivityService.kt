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
            class GroupBuilder(val type: ActivityEventType, val timestamp: Long) {
                val items = ArrayList<ActivityEvent>()
            }

            val filter = ActivityEventFilter(
                offset = scroll.offset,
                user = user
            )

            val collapsedTypes = HashSet<ActivityEventType>()
            val groups = ArrayList<ActivityEventGroup>()
            var currentGroup: GroupBuilder? = null

            fun finalizeGroup() {
                val group = currentGroup ?: return
                if (collapsedTypes.contains(group.type)) return

                val newestTimestamp = group.items.maxBy { it.timestamp }?.timestamp ?: 0L

                log.debug("${group.items.size} > $collapseThreshold")
                val groupToAdd = if (group.items.size > collapseThreshold) {
                    collapsedTypes.add(group.type)

                    group.items.forEach { log.debug(it.toString()) }

                    val countAggregation = activityDao.countEvents(session, filter.let {
                        it.copy(
                            type = group.type,
                            maxTimestamp = group.timestamp,
                            minTimestamp = listOfNotNull(
                                it.minTimestamp,
                                group.timestamp - GROUP_TIME_THRESHOLD
                            ).min()!!
                        )
                    })
                    val items = group.items.take(collapseThreshold)

                    ActivityEventGroup(
                        group.type,
                        newestTimestamp,
                        countAggregation.count - items.size,
                        items
                    )
                } else {
                    ActivityEventGroup(
                        group.type,
                        newestTimestamp,
                        null,
                        group.items
                    )
                }

                groups.add(groupToAdd)
            }

            val allEvents = activityDao.findEvents(
                session,
                scroll.scrollSize,
                filter
            )

            for (event in allEvents) {
                if (
                    currentGroup == null ||
                    currentGroup.type != event.type ||
                    (currentGroup.timestamp - event.timestamp) > GROUP_TIME_THRESHOLD
                ) {
                    if (currentGroup != null) finalizeGroup()

                    currentGroup = GroupBuilder(event.type, event.timestamp).also {
                        it.items.add(event)
                    }
                } else {
                    currentGroup.items.add(event)
                }
            }

            finalizeGroup()

            val offsetBasedOnReturnedRows = groups.sumBy { it.items.size } + (scroll.offset ?: 0)
            val skippedRows: Int = run {
                if (groups.isEmpty()) 0
                else {
                    // We only need to skip forward if last group is collapsed, they will otherwise have been consumed.
                    val lastGroup = groups.last()
                    if (lastGroup.numberOfHiddenResults != null) {
                        lastGroup.numberOfHiddenResults.toInt()
                    } else {
                        0
                    }
                }
            }

            log.debug("Returned rows: $offsetBasedOnReturnedRows. Skipped: $skippedRows")

            val nextOffset = offsetBasedOnReturnedRows + skippedRows

            ScrollResult(groups, nextOffset, endOfScroll = groups.isEmpty())
        }
    }

    companion object : Loggable {
        const val GROUP_TIME_THRESHOLD = 1000L * 60 * 15
        override val log = logger()
    }
}

