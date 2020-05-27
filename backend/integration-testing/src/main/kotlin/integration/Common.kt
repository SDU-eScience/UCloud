package dk.sdu.cloud.integration

data class Configuration(
    val userA: User,
    val userB: User,
    val slack: SlackHook,
    val concurrency: Int?
)

data class SlackHook(val hook: String)

data class User(val username: String, val refreshToken: String) {
    init {
        require(username.isNotEmpty())
        require(refreshToken.isNotEmpty())
    }
}
