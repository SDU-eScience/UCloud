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

    /**
     * A unique numeric id for this principal. This is suitable for systems that require numeric identifiers.
     * Use of [id] is strongly preferred.
     */
    abstract val uid: Long

    @Deprecated("No longer in use")
    open val emailAddresses: List<String> = emptyList()

    @Deprecated("No longer in use")
    open val preferredEmailAddress: String? = null

    protected open fun validate() {
        require(id.isNotEmpty()) { "ID cannot be empty!" }
        require(!id.startsWith("__")) { "A principal's ID cannot start with '__'" }
    }
}

sealed class Person : Principal() {
    abstract val title: String?
    abstract val firstNames: String
    abstract val lastName: String
    abstract val phoneNumber: String?
    abstract val orcId: String?
    abstract val email: String?
    abstract val serviceLicenseAgreement: Int
    abstract val wantsEmails: Boolean?

    /**
     * Indicates if the Person is authenticated with more than one factor.
     *
     * A value of true _does not_ mean that TOTP is enabled on the user. Any additional factor provided by the
     * identity provider may count.
     */
    abstract val twoFactorAuthentication: Boolean

    override val emailAddresses: List<String> get() = listOfNotNull(email)
    override val preferredEmailAddress: String? get() = email

    abstract val displayName: String

    override fun validate() {
        super.validate()
        require(!id.startsWith("_")) { "A person's ID cannot start with '_'" }
        require(firstNames.isNotEmpty()) { "First name cannot be empty" }
        require(lastName.isNotEmpty()) { "Last name cannot be empty" }
        require(phoneNumber?.isEmpty() != true) { "Phone number cannot be empty if != null" }
        require(title?.isEmpty() != true) { "Title cannot be empty if != null" }
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
        override val email: String? = null,
        override val uid: Long = 0,
        override val serviceLicenseAgreement: Int,
        override val wantsEmails: Boolean? = true,

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

        // NOTE(Dan): WAYF is supposed to bring in additional factors. This should eliminate the need for us to
        //  use our own TOTP solution. It does not appear that we can trust the attribute we get from WAYF.
        //  As a result we have decided to set this to `true` for now.
        override val twoFactorAuthentication = true
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
        override val email: String? = null,
        override val uid: Long = 0,
        override val twoFactorAuthentication: Boolean,
        override val serviceLicenseAgreement: Int,
        override val wantsEmails: Boolean? = true,

        @JsonIgnore
        val password: ByteArray = ByteArray(0),

        @JsonIgnore
        val salt: ByteArray = ByteArray(0)
    ) : Person() {
        init {
            validate()
        }

        override val displayName: String = id

        override fun toString(): String {
            return "ByPassword(id='$id', role=$role, title=$title, firstNames='$firstNames', " +
                    "lastName='$lastName', phoneNumber=$phoneNumber, orcId=$orcId, " +
                    "email='$email', wantEmails='$wantsEmails')"
        }
    }
}

/**
 * Represents a service
 */
data class ServicePrincipal(
    override val id: String,
    override val role: Role,
    override val uid: Long = 0
) : Principal() {
    init {
        validate()
        require(id.startsWith("_")) { "A service's ID should start with a single underscore" }
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

data class AccessToken(override val accessToken: String) : WithAccessToken {
    override fun toString(): String = "AccessToken()"
}

data class AccessTokenAndCsrf(
    override val accessToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken {
    override fun toString(): String = "AccessTokenAndCsrf()"
}

data class RefreshTokenAndCsrf(
    override val refreshToken: String,
    override val csrfToken: String?
) : WithOptionalRefreshToken, WithOptionalCsrfToken {
    override fun toString(): String = "RefreshTokenAndCsrf()"
}

data class AuthenticationTokens(
    override val accessToken: String,
    override val refreshToken: String,
    override val csrfToken: String
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken {
    override fun toString() = "AuthenticationTokens()"
}

data class OptionalAuthenticationTokens(
    override val accessToken: String,
    override val csrfToken: String? = null,
    override val refreshToken: String? = null
) : WithAccessToken, WithOptionalCsrfToken, WithOptionalRefreshToken {
    override fun toString() = "OptionalAuthenticationTokens()"
}
