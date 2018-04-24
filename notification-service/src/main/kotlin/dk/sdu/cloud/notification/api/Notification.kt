package dk.sdu.cloud.notification.api

import dk.sdu.cloud.FindByStringId

typealias NotificationId = String
typealias FindByNotificationId = FindByStringId

data class Notification(
    val type: String,
    val message: String,

    val id: NotificationId? = null,
    val meta: Map<String, Any?> = emptyMap(),
    val ts: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)