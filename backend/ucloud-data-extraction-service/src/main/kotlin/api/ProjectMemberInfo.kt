package dk.sdu.cloud.ucloud.data.extraction.api

import org.joda.time.LocalDateTime

data class ProjectMemberInfo (
    val addedToProjectAt: LocalDateTime,
    val username: String,
    val projectId: String,
)
