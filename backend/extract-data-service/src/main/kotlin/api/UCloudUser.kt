package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class UCloudUser(
    val username: String,
    @Contextual
    val createdAt: LocalDateTime
)
