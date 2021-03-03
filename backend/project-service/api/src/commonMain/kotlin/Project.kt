package dk.sdu.cloud.project.api

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val title: String,
    val parent: String? = null,
    val archived: Boolean,
    val fullPath: String? = null
)

@Serializable
data class ProjectGroup(
    val id: String,
    val title: String
)

@Serializable
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    val memberOfAnyGroup: Boolean? = null
)

@Serializable
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
