package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope

class AuthDSlException(why: String) : RuntimeException(why)

class AuthDescription<R : Any, S : Any, E : Any> internal constructor(
    val context: CallDescription<R, S, E>,
    val roles: Set<Role>,
    val access: AccessRight,
    val requiredScope: SecurityScope
) {
    companion object {
        val descriptionKey = AttributeKey<AuthDescription<*, *, *>>("auth-description")
    }
}

class AuthDescriptionBuilder<R : Any, S : Any, E : Any> internal constructor(
    private val context: CallDescription<R, S, E>
) {
    var roles: Set<Role> = Roles.END_USER
    var access: AccessRight? = null
    var requiredScope: SecurityScope? = null

    fun build(): AuthDescription<R, S, E> {
        val accessRight = access ?: throw AuthDSlException(
            """
                Missing auth.access!

                This property describes if the operation is read or read/write.
                In cases where it can be both it should be set to read. This property is
                enforced through authorization scopes.

                Each authentication token contains a number of scopes. A scope describes what a
                user can do with a certain resource, and only reads are allowed or if read/writes
                is possible.

                These scopes are used for third party applications (OAuth) and internally for token extensions.

                Examples:

                callDescription<..., ..., ...> {
                    name = "uploadFile"

                    auth {
                        // Uploading a file will write new data into the system
                        access = AccessRight.READ_WRITE
                    }
                }

                callDescription<..., ..., ...> {
                    name = "listDirectory"

                    auth {
                        // Listing files in a directory will not modify any data in the system
                        access = AccessRight.READ
                    }
                }
            """.trimIndent()
        )

        return AuthDescription(
            context,
            roles,
            accessRight,
            requiredScope ?: SecurityScope.parseFromString(context.fullName + ':' + accessRight.scopeName)
        )
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.auth(consumer: AuthDescriptionBuilder<R, S, E>.() -> Unit) {
    attributes[AuthDescription.descriptionKey] = AuthDescriptionBuilder(this).also(consumer).build()
}

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.authDescription: AuthDescription<R, S, E>
    get() = attributes[AuthDescription.descriptionKey] as AuthDescription<R, S, E>
