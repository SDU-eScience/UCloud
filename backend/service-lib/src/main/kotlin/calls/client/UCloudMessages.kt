package dk.sdu.cloud.calls.client

import io.ktor.http.*

private val UCloudMessagesContentType = ContentType("application", "ucloud-msg")
val ContentType.Application.UCloudMessage get() = UCloudMessagesContentType

