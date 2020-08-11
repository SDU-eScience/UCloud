package dk.sdu.cloud.project.api

data class Project(
    val id: String,
    val title: String,
    val parent: String?,
    val archived: Boolean
)

data class ProjectGroup(
    val id: String,
    val title: String
)

data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    val memberOfAnyGroup: Boolean? = null
)

enum class ProjectRole(val uiFriendly: String) {
    PI("PI"),
    ADMIN("Admin"),
    USER("User");

    override fun toString() = uiFriendly

    fun isAdmin(): Boolean {
        return this == PI || this == ADMIN
    }

    companion object {
        val ALL = setOf(PI, ADMIN, USER)
        val ADMINS = setOf(PI, ADMIN)
    }
}
