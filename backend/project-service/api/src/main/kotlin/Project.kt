package dk.sdu.cloud.project.api

data class Project(
    val id: String,
    val title: String
)

data class ProjectMember(
    val username: String,
    val role: ProjectRole
)

enum class ProjectRole {
    PI,
    ADMIN,
    USER;

    fun isAdmin(): Boolean {
        return this == PI || this == ADMIN
    }
}
