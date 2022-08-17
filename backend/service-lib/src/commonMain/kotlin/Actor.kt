package dk.sdu.cloud

// Note(Dan): Trying out an abstraction for who is performing an action within the system.
sealed class Actor {
    abstract val username: String

    /**
     * Performed by the system itself. Should bypass all permission checks.
     */
    object System : Actor() {
        override val username: String
            get() = throw IllegalStateException("No username associated with system")

        override fun toString(): String = "Actor.System"
    }

    /**
     * Performed by the system on behalf of a user.
     * This should use permission checks against the user.
     */
    class SystemOnBehalfOfUser(override val username: String) : Actor() {
        override fun toString(): String = "Actor.SystemOnBehalfOfUser($username)"
    }

    /**
     * Performed by the user. Should check permissions against the user.
     */
    class User(val principal: SecurityPrincipal) : Actor() {
        override val username = principal.username

        override fun toString(): String = "Actor.User(${username}, ${principal.role})"
    }

    companion object {
        val guest = SystemOnBehalfOfUser("__guest")
    }
}

fun Actor.safeUsername(systemUsername: String = "_ucloud"): String {
    return when (this) {
        Actor.System -> systemUsername
        else -> username
    }
}

fun SecurityPrincipalToken.toActor(): Actor.User = Actor.User(principal)
fun SecurityPrincipal.toActor(): Actor.User = Actor.User(this)

fun SecurityPrincipal?.toActorOrGuest(): Actor = this?.toActor() ?: Actor.guest
fun SecurityPrincipalToken?.toActorOrGuest(): Actor = this?.toActor() ?: Actor.guest

data class ActorAndProject(val actor: Actor, val project: String?, val signedIntentFromUser: String? = null)
