package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.*
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.filesearch.api.LookupDescriptions
import dk.sdu.cloud.filesearch.api.ReverseLookupRequest
import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll

class ActivityService<DBSession>(
    private val activityDao: ActivityEventDao<DBSession>,
    private val streamDao: ActivityStreamDao<DBSession>,
    private val fileLookupService: FileLookupService,
    private val cloud: AuthenticatedCloud
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

        val byFileId = events.groupBy { it.fileId }.mapValues { it.value.toStreamEntries() }
        val byUsername = events.groupBy { it.username }.mapValues { it.value.toStreamEntries() }

        byFileId.forEach { fileId, entries ->
            val stream = ActivityStream(ActivityStreamSubject.File(fileId))
            streamDao.createStreamIfNotExists(session, stream)
            streamDao.insertBatchIntoStream(session, stream, entries)
        }

        byUsername.forEach { username, entries ->
            val stream = ActivityStream(ActivityStreamSubject.User(username))
            streamDao.createStreamIfNotExists(session, stream)
            streamDao.insertBatchIntoStream(session, stream, entries)
        }
    }

    private fun List<ActivityEvent>.toStreamEntries(): List<ActivityStreamEntry<*>> {
        // TODO Here we currently assume they all should go in the same timestamp. This will work almost all of the time
        val timestamp = minBy { it.timestamp }!!.timestamp
        val byType = groupBy { it.javaClass }

        return byType.flatMap { (_, allEventsOfType) ->
            // All events have the same operation, so we use the first event to determine operation.
            val firstEvent = allEventsOfType.first()

            val users = allEventsOfType
                .asSequence()
                .map { it.username }
                .map { UserReference(it) }
                .toSet()

            CountedFileActivityOperation.fromEventOrNull(firstEvent)?.let { operation ->
                val eventsByFileId = allEventsOfType.groupBy { it.fileId }

                val counts = eventsByFileId.map { (fileId, eventsForFile) ->
                    fileId to eventsForFile.size
                }

                return@flatMap listOf(
                    ActivityStreamEntry.Counted(
                        operation,
                        timestamp,
                        counts.asSequence().map {
                            StreamFileReference.WithOpCount(
                                it.first,
                                null,
                                it.second
                            )
                        }.toSet(),
                        users
                    )
                )
            }

            TrackedFileActivityOperation.fromEventOrNull(firstEvent)?.let { operation ->
                val fileReferences =
                    allEventsOfType.asSequence().map { StreamFileReference.Basic(it.fileId, null) }.toSet()

                return@flatMap listOf(ActivityStreamEntry.Tracked(operation, timestamp, fileReferences, users))
            }

            return@flatMap emptyList<ActivityStreamEntry<*>>()
        }
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

    suspend fun findStreamForUser(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<ActivityStreamEntry<*>> {
        return loadStreamWithFileLookup(session, pagination, ActivityStream(ActivityStreamSubject.User(user)))
    }

    suspend fun findStreamForPath(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        path: String,
        userAccessToken: String,
        causedBy: String? = null
    ): Page<ActivityStreamEntry<*>> {
        val fileStat = fileLookupService.lookupFile(path, userAccessToken, causedBy)
        return findStreamForFileId(session, pagination, fileStat.fileId)
    }

    suspend fun findStreamForFileId(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityStreamEntry<*>> {
        return loadStreamWithFileLookup(
            session,
            pagination,
            ActivityStream(ActivityStreamSubject.File(fileId))
        )
    }

    private suspend fun loadStreamWithFileLookup(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        stream: ActivityStream
    ): Page<ActivityStreamEntry<*>> {
        val resultFromDao = streamDao.loadStream(session, stream, pagination)
        val fileIdsInChunks = resultFromDao.items.flatMap { entry ->
            when (entry) {
                is ActivityStreamEntry.Counted -> {
                    entry.files.map { it.id }
                }

                is ActivityStreamEntry.Tracked -> {
                    entry.files.map { it.id }
                }
            }
        }.asSequence().toSet().asSequence().chunked(100).toList()

        val fileIdToCanonicalPath = fileIdsInChunks
            .map { chunkOfIds ->
                async {
                    chunkOfIds.zip(
                        LookupDescriptions.reverseLookup
                            .call(
                                ReverseLookupRequest(chunkOfIds),
                                cloud
                            )
                            .orThrow()
                            .canonicalPath
                    )
                }
            }
            .awaitAll()
            .flatten()
            .toMap()

        return resultFromDao.mapItems { entry ->
            when (entry) {
                is ActivityStreamEntry.Counted -> {
                    entry.copy(
                        files = entry.files
                            .asSequence()
                            .map { it.withPath(path = fileIdToCanonicalPath[it.id]) }
                            .toSet()
                    )
                }

                is ActivityStreamEntry.Tracked -> {
                    entry.copy(
                        files = entry.files.asSequence().map {
                            StreamFileReference.Basic(it.id, fileIdToCanonicalPath[it.id])
                        }.toSet()
                    )
                }
            }
        }
    }
}

internal fun <T> RESTResponse<T, *>.orThrow(): T {
    if (this !is RESTResponse.Ok) {
        throw RPCException(rawResponseBody, HttpStatusCode.fromValue(status))
    }
    return result
}

internal fun AuthenticatedCloud.optionallyCausedBy(causedBy: String?): AuthenticatedCloud {
    return if (causedBy != null) withCausedBy(causedBy)
    else this
}

fun TokenExtensionResponse.asCloud(context: CloudContext, causedBy: String?): AuthenticatedCloud {
    return context.jwtAuth(this.accessToken).optionallyCausedBy(causedBy)
}