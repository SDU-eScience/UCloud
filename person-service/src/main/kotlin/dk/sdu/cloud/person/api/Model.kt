package dk.sdu.cloud.person.api

data class Person(
        val id: Long,
        val name: String,
        val startAt: Long,
        val endAt: Long,
        val shortName: String
)

data class PersonMember(val user: Long, val role: ProjectRole)

data class PersonWithMembers(val project: Person, val members: List<PersonMember>)

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