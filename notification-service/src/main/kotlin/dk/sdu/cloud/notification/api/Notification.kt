package dk.sdu.cloud.notification.api

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.FindByLongIdBulk

typealias NotificationId = Long
typealias FindByNotificationId = FindByLongId
typealias FindByNotificationIdBulk = FindByLongIdBulk

data class Notification(
    val type: String,
    val message: String,

    val id: NotificationId? = null,
    val meta: Map<String, Any?> = emptyMap(),
    val ts: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
