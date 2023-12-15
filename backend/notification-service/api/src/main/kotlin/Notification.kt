package dk.sdu.cloud.notification.api

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.service.Time
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias NotificationId = Long
typealias FindByNotificationId = FindByLongId

@Serializable
data class Notification(
    val type: String,
    val message: String,

    val id: NotificationId? = null,
    val meta: JsonObject = JsonObject(emptyMap()),
    val ts: Long = Time.now(),
    val read: Boolean = false
)

@Serializable
enum class NotificationType{
    PROJECT_ROLE_CHANGE,
    PROJECT_INVITE,
    PROJECT_USER_LEFT,
    PROJECT_USER_REMOVED,
    SHARE_REQUEST,
    REVIEW_PROJECT,
    JOB_STARTED,
    JOB_COMPLETED,
}
