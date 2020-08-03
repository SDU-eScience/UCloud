package dk.sdu.cloud.notification.api

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.service.Time

typealias NotificationId = Long
typealias FindByNotificationId = FindByLongId

data class Notification(
    val type: String,
    val message: String,

    val id: NotificationId? = null,
    val meta: Map<String, Any?> = emptyMap(),
    val ts: Long = Time.now(),
    val read: Boolean = false
)
 enum class NotificationType{
     APP_COMPLETE,
     PROJECT_ROLE_CHANGE,
     PROJECT_INVITE,
     PROJECT_USER_LEFT,
     PROJECT_USER_REMOVED,
     SHARE_REQUEST,
     REVIEW_PROJECT,
 }
