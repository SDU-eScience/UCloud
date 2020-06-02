package dk.sdu.cloud.project.api

data class Project(
    val id: String,
    val title: String,
    val parent: String?,
    val archived: Boolean
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

    companion object {
        val ALL = setOf(PI, ADMIN, USER)
        val ADMINS = setOf(PI, ADMIN)
    }
}
