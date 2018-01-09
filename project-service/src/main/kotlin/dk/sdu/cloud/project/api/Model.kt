package dk.sdu.cloud.project.api

data class Project(
        val id: Long,
        val name: String,
        val startAt: Long,
        val endAt: Long,
        val shortName: String
)

data class ProjectMember(val user: Long, val role: ProjectRole)

data class ProjectWithMembers(val project: Project, val members: List<ProjectMember>)

enum class ProjectRole(val roleName: String) {
    LEADER("Leader"),
    TEAM_MEMBER("Team member"),
    ADMIN("Admin"),
    DATA_FACILITATOR("Data facilitator"),
    APP_DESIGNER("App Designer"),
    UNKNOWN("Unknown")
}

enum class ProjectType(val typeName: String) {
    ORDINARY("Ordinary"),
    TEST("Test project"),
    DEVELOPMENT("Development"),
    UNKNOWN("Unknown")
}