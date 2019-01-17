package dk.sdu.cloud.project.api

data class Project(
    val id: String,
    val title: String,
    val members: List<ProjectMember> = emptyList()
)

data class ProjectMember(
    val username: String,
    val role: ProjectRole
)

enum class ProjectRole {
    PI,
    ADMIN,
    DATA_STEWARD,
    USER
}
