package dk.sdu.cloud.ucloud.data.extraction.api

import org.joda.time.LocalDateTime

data class UCloudUser(
    val username: String,
    val createdAt: LocalDateTime
)
