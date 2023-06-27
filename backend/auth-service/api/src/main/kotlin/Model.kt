package dk.sdu.cloud.auth.api

import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a security principal, i.e., any entity which can authenticate with the system. A security principal
 * can be both a person or any other type of non-human entity (Usually other services).
 */
sealed class Principal {
    /**
     * A unique ID for this principal. It should generally not contain sensitive data as this ID will be used a public
     * identifier of a person.
     */
    abstract val id: String

    /**
     * The role of this principle in the entire system. Other services are generally encouraged to implement their
     * own authorization control as opposed to relying on this. This should only be used when more general authorization
     * can be used.
     */
    abstract val role: Role

    protected open fun validate() {
        require(id.isNotEmpty()) { "ID cannot be empty!" }
        require(!id.startsWith("__")) { "A principal's ID cannot start with '__'" }
    }
}

@Serializable
data class IdentityProviderConnection(
    val identityProvider: Int,
    val identity: String,
    val organizationId: String? = null,
)

data class Person(
    override val id: String,
    override val role: Role,

    val firstNames: String,
    val lastName: String,
    val email: String?,
    val serviceLicenseAgreement: Int,
    val organizationId: String?,

    /**
     * Indicates if the Person is authenticated with more than one factor.
     *
     * A value of true _does not_ mean that TOTP is enabled on the user. Any additional factor provided by the
     * identity provider may count.
     */
    val twoFactorAuthentication: Boolean,

    val connections: List<IdentityProviderConnection>,

    @Transient val password: ByteArray? = null,
    @Transient val salt: ByteArray? = null,
) : Principal() {
    override fun validate() {
        super.validate()
        require(!id.startsWith("_")) { "A person's ID cannot start with '_'" }
        require(firstNames.isNotEmpty()) { "First name cannot be empty" }
        require(lastName.isNotEmpty()) { "Last name cannot be empty" }
        require(password == null || salt != null) { "password and salt must be supplied together" }

        if (id.contains(Regex("[\\\\?/!\$%^&*)(\\[\\]}{':;\\r\\n]+"))) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Username contains illegal chars")
        }
    }
}

/**
 * Represents a service
 */
data class ServicePrincipal(
    override val id: String,
    override val role: Role,
) : Principal() {
    init {
        validate()
        require(id.startsWith("_")) { "A service's ID should start with a single underscore" }
    }
}

data class ProviderPrincipal(
    override val id: String,
) : Principal() {
    override val role: Role = Role.PROVIDER

    init {
        validate()
        require(id.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            "A provider must start with the provider prefix ('${AuthProviders.PROVIDER_PREFIX}')"
        }
    }
}

interface WithAccessToken {
    val accessToken: String
}

interface WithOptionalCsrfToken {
    val csrfToken: String?
}

interface WithOptionalRefreshToken {
    val refreshToken: String?
}

@Serializable
data class RefreshToken(override val refreshToken: String) : WithOptionalRefreshToken {
    override fun toString(): String = "RefreshToken()"
}

@Serializable
data class AccessToken(override val accessToken: String) : WithAccessToken {
    override fun toString(): String = "AccessToken()"
}

@Serializable
data class AccessTokenAndCsrf(
    override val accessToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken {
    override fun toString(): String = "AccessTokenAndCsrf()"
}

@Serializable
data class RefreshTokenAndCsrf(
    override val refreshToken: String,
    override val csrfToken: String? = null,
) : WithOptionalRefreshToken, WithOptionalCsrfToken {
    override fun toString(): String = "RefreshTokenAndCsrf()"
}

@Serializable
data class AuthenticationTokens(
    override val accessToken: String,
    override val refreshToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken {
    override fun toString() = "AuthenticationTokens()"
}

@Serializable
data class OptionalAuthenticationTokens(
    override val accessToken: String,
    override val csrfToken: String? = null,
    override val refreshToken: String? = null
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken {
    override fun toString() = "OptionalAuthenticationTokens()"
}
