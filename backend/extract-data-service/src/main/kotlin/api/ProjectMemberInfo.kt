package dk.sdu.cloud.extract.data.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class ProjectMemberInfo (
    @Contextual
    val addedToProjectAt: LocalDateTime,
    val username: String,
    val projectId: String,
)
