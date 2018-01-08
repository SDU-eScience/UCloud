package dk.sdu.cloud.project.api

data class Project(
        val id: Long,
        val name: String,
        val startAt: Long,
        val endAt: Long
)

