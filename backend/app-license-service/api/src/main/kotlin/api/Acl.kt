package dk.sdu.cloud.app.license.api

data class DetailedAccessEntityWithPermission(
    val entity: DetailedAccessEntity,
    val permission: ServerAccessRight
)


enum class ServerAccessRight {
    READ,
    READ_WRITE
}

data class Project(
    val id: String,
    val title: String
)

typealias ProjectGroup = Project

data class DetailedAccessEntity(
    val user: String?,
    val project: Project?,
    val group: ProjectGroup?
) {
    init {
        require(!user.isNullOrBlank() || (project != null && group != null)) { "No access entity defined" }
    }
}

data class ProjectAndGroup(
    val project: String,
    val group: String
)

data class AccessEntity(
    val user: String?,
    val project: String?,
    val group: String?
) {
    init {
        require(!user.isNullOrBlank() || (!project.isNullOrBlank() && !group.isNullOrBlank())) { "No access entity defined" }
    }
}
