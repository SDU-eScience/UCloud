package dk.sdu.cloud.integration

data class Configuration(
    val userA: User,
    val userB: User,
    val concurrency: Int?
)

data class User(val username: String, val refreshToken: String) {
    init {
        require(username.isNotEmpty())
        require(refreshToken.isNotEmpty())
    }
}
