package dk.sdu.cloud.auth.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.Role
import dk.sdu.cloud.service.TYPE_PROPERTY

/**
 * Represents a security principal, i.e., any entity which can authenticate with the system. A security principal
 * can be both a person or any other type of non-human entity (Usually other services).
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Person.ByWAYF::class, name = "wayf"),
    JsonSubTypes.Type(value = Person.ByPassword::class, name = "password"),
    JsonSubTypes.Type(value = ServicePrincipal::class, name = "service")
)
sealed class Principal {
    /**
     * A unique ID for this principle. It should generally not contain sensitive data as this ID will be used a public
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
        if (id.isEmpty()) throw IllegalArgumentException("ID cannot be empty!")
        if (id.startsWith("__")) throw IllegalArgumentException("A principal's ID cannot start with '__'")
    }
}

sealed class Person : Principal() {
    abstract val title: String?
    abstract val firstNames: String
    abstract val lastName: String
    abstract val phoneNumber: String?
    abstract val orcId: String?
    abstract val emailAddresses: List<String>
    abstract val preferredEmailAddress: String?

    abstract val displayName: String

    // TODO Proper email validation
    private fun isValidEmail(email: String): Boolean = email.contains("@")

    override fun validate() {
        super.validate()
        if (id.startsWith("_")) throw IllegalArgumentException("A person's ID cannot start with '_'")
        if (firstNames.isEmpty()) throw IllegalArgumentException("First name cannot be empty")
        if (lastName.isEmpty()) throw IllegalArgumentException("Last name cannot be empty")
        if (phoneNumber?.isEmpty() == true) throw IllegalArgumentException("Phone number cannot be empty if != null")
        if (title?.isEmpty() == true) throw IllegalArgumentException("Title cannot be empty if != null")

        if (preferredEmailAddress != null && preferredEmailAddress!! !in emailAddresses) {
            throw IllegalArgumentException("Preferred email address is not in primary list of addresses")
        }

        // TODO Validate orcId
    }

    /**
     * Represents a [Person] authenticated by WAYF
     */
    data class ByWAYF(
        override val id: String,
        override val role: Role,
        override val title: String?,
        override val firstNames: String,
        override val lastName: String,
        override val phoneNumber: String?,
        override val orcId: String?,
        override val emailAddresses: List<String>,
        override val preferredEmailAddress: String?,

        /**
         * Given by WAYF in the property `schacHomeOrganization`
         */
        val organizationId: String,

        /**
         * Given by WAYF in the property `eduPersonTargetedID`
         */
        val wayfId: String
    ) : Person() {
        init {
            validate()

            if (organizationId.isEmpty()) throw IllegalArgumentException("organizationId cannot be empty")
        }

        override val displayName: String = "$firstNames $lastName"
    }

    /**
     * Represents a [Person] authenticated by a password
     */
    data class ByPassword(
        override val id: String,
        override val role: Role,
        override val title: String?,
        override val firstNames: String,
        override val lastName: String,
        override val phoneNumber: String?,
        override val orcId: String?,
        override val emailAddresses: List<String>,
        override val preferredEmailAddress: String?,

        @JsonIgnore
        val password: ByteArray = ByteArray(0),

        @JsonIgnore
        val salt: ByteArray = ByteArray(0)
    ) : Person() {
        init {
            validate()

            if (password.isEmpty()) throw IllegalArgumentException("Password cannot be empty")
            if (salt.isEmpty()) throw IllegalArgumentException("Salt cannot be empty")
        }

        override val displayName: String = id

        override fun toString(): String {
            return "ByPassword(id='$id', role=$role, title=$title, firstNames='$firstNames', " +
                    "lastName='$lastName', phoneNumber=$phoneNumber, orcId=$orcId, " +
                    "emailAddresses=$emailAddresses, preferredEmailAddress=$preferredEmailAddress)"
        }
    }
}

/**
 * Represents a service
 */
data class ServicePrincipal(
    override val id: String,
    override val role: Role
) : Principal() {
    init {
        validate()
        if (!id.startsWith("_")) throw IllegalArgumentException("A service's ID should start with a single underscore")
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

data class AccessToken(override val accessToken: String) : WithAccessToken

data class AccessTokenAndCsrf(
    override val accessToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken

data class AuthenticationTokens(
    override val accessToken: String,
    override val refreshToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken

data class OptionalAuthenticationTokens(
    override val accessToken: String,
    override val csrfToken: String?,
    override val refreshToken: String?
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken
