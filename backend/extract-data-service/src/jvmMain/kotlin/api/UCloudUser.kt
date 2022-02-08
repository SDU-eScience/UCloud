package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joda.time.LocalDateTime

@Serializable
data class UCloudUser(
    val username: String,
    @Contextual
    val createdAt: LocalDateTime
)
