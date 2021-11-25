package dk.sdu.cloud.project.api

import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiOwnedBy(Projects::class)
data class Project(
    val id: String,
    val title: String,
    val parent: String? = null,
    val archived: Boolean,
    val fullPath: String? = null
)

@Serializable
@UCloudApiOwnedBy(Projects::class)
data class MemberInProject(
    val role: ProjectRole? = null,
    val project: Project
)

@Serializable
data class ProjectGroup(
    val id: String,
    val title: String
)

@Serializable
@UCloudApiOwnedBy(Projects::class)
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    val memberOfAnyGroup: Boolean? = null
)

@Serializable
@UCloudApiOwnedBy(Projects::class)
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
