package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityStreamEntry

/**
 * An activity stream. It contains instances of [ActivityStreamEntry]
 */
data class ActivityStream(
    val subject : ActivityStreamSubject
)

sealed class ActivityStreamSubject {
    data class File(val id: String) : ActivityStreamSubject()
    data class User(val username: String) : ActivityStreamSubject()
}

interface ActivityStreamDao<Session> {
    fun createStreamIfNotExists(session: Session, stream: ActivityStream)

    fun insertIntoStream(session: Session, stream: ActivityStream, entry: ActivityStreamEntry<*>)

    fun insertBatchIntoStream(session: Session, stream: ActivityStream, entries: List<ActivityStreamEntry<*>>) {
        entries.forEach {
            insertIntoStream(session, stream, it)
        }
    }
}

