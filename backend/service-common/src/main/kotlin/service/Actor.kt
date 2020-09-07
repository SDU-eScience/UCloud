package dk.sdu.cloud.service

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken

// Note(Dan): Trying out an abstraction for who is performing an action within the system.
sealed class Actor {
    abstract val username: String

    /**
     * Performed by the system itself. Should bypass all permission checks.
     */
    object System : Actor() {
        override val username: String
            get() = throw IllegalStateException("No username associated with system")
    }

    /**
     * Performed by the system on behalf of a user.
     * This should use permission checks against the user.
     */
    class SystemOnBehalfOfUser(override val username: String) : Actor()

    /**
     * Performed by the user. Should check permissions against the user.
     */
    class User(val principal: SecurityPrincipal) : Actor() {
        override val username = principal.username
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
