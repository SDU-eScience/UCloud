package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)

internal object PersonUtils {
    private val RNG = SecureRandom()
    private val SALT_LENGTH = 16
    private val ITERATIONS = 10000
    private val KEY_LENGTH = 256

    fun createUserByPassword(
        firstNames: String, lastName: String, email: String, role: Role,
        password: String
    ): Person.ByPassword {
        val (hashed, salt) = hashPassword(password)
        return Person.ByPassword(
            id = email,
            role = role,
            title = null,
            firstNames = firstNames,
            lastName = lastName,
            phoneNumber = null,
            orcId = null,
            emailAddresses = listOf(email),
            preferredEmailAddress = email,
            password = hashed,
            salt = salt
        )
    }

    fun createUserByWAYF(authenticatedUser: Auth): Person.ByWAYF {
        if (!authenticatedUser.authenticated) throw IllegalStateException("User is not authenticated")
        val id = authenticatedUser.attributes[AttributeURIs.EduPersonTargetedId]?.firstOrNull()
                ?: throw IllegalArgumentException("Missing EduPersonTargetedId")
        val firstNames =
            authenticatedUser.attributes["gn"]?.firstOrNull() ?: throw IllegalArgumentException("Missing gn")
        val lastNames =
            authenticatedUser.attributes["sn"]?.firstOrNull() ?: throw IllegalArgumentException("Missing sn")
        val organization = authenticatedUser.attributes["schacHomeOrganization"]?.firstOrNull()
                ?: throw IllegalArgumentException("Missing schacHomeOrganization")

        val role = Role.USER

        return Person.ByWAYF(
            id = id,
            firstNames = firstNames,
            lastName = lastNames,
            role = role,
            title = null,
            phoneNumber = null,
            orcId = null,
            emailAddresses = emptyList(),
            preferredEmailAddress = null,
            organizationId = organization
        )
    }

    fun hashPassword(password: String, salt: ByteArray = genSalt()): HashedPasswordAndSalt {
        val passwordArr = password.toCharArray()
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(passwordArr, salt, ITERATIONS, KEY_LENGTH)
            val key = skf.generateSecret(spec)
            return HashedPasswordAndSalt(key.encoded, salt)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException(e)
        }
    }

    private fun genSalt(): ByteArray = ByteArray(SALT_LENGTH).also { RNG.nextBytes(it) }
}

fun Person.ByPassword.checkPassword(plainPassword: String): Boolean {
    val incomingPasswordHashed = PersonUtils.hashPassword(plainPassword, salt)
    return incomingPasswordHashed.hashedPassword.contentEquals(password)
}

interface UserDAO<Session> {
    fun findById(session: Session, id: String): Principal
    fun findByIdOrNull(session: Session, id: String): Principal?
    fun findAllByIds(session: Session, ids: List<String>): Map<String, Principal?>
    fun insert(session: Session, principal: Principal)
    fun updatePassword(
        session: Session,
        id: String,
        newPassword: String,
        currentPasswordForVerification: String?
    )

    fun delete(session: Session, id: String)

    fun listAll(session: Session): List<Principal>
}

