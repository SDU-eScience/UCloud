package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
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

    fun createUserByPassword(firstNames: String, lastName: String, email: String, role: Role,
                             password: String): Person.ByPassword {
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
        val id = authenticatedUser.attributes[AttributeURIs.EduPersonTargetedId]?.firstOrNull() ?:
                throw IllegalArgumentException("Missing EduPersonTargetedId")
        val firstNames = authenticatedUser.attributes["gn"]?.firstOrNull() ?:
                throw IllegalArgumentException("Missing gn")
        val lastNames = authenticatedUser.attributes["sn"]?.firstOrNull() ?:
                throw IllegalArgumentException("Missing sn")
        val organization = authenticatedUser.attributes["schacHomeOrganization"]?.firstOrNull() ?:
                throw IllegalArgumentException("Missing schacHomeOrganization")

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

class CommonTable {
    lateinit var modifiedAt: Column<DateTime>
        private set
    lateinit var createdAt: Column<DateTime>
        private set
    lateinit var markedForDelete: Column<Boolean>
        private set
    lateinit var active: Column<Boolean>
        private set

    companion object {
        fun register(table: Table) = with(table) {
            CommonTable().apply {
                modifiedAt = datetime("modified_at")
                createdAt = datetime("created_at")
                markedForDelete = bool("markedfordelete")
                active = bool("active")
            }
        }
    }

    fun setValuesForUpdate(row: UpdateBuilder<*>) {
        row[modifiedAt] = DateTime.now()
    }

    fun setValuesForCreation(row: UpdateBuilder<*>, active: Boolean = true) {
        row[modifiedAt] = DateTime.now()
        row[createdAt] = DateTime.now()
        row[markedForDelete] = false
        row[this.active] = active
    }
}

object Principals : Table() {
    val id = varchar("id", 255).primaryKey()
    val loginType = varchar("logintype", 32)
    val role = varchar("role", 32)

    // Common
    val common = CommonTable.register(this)

    // Person
    val title = varchar("title", 255).nullable()
    val firstNames = varchar("firstname", 255).nullable()
    val lastName = varchar("lastname", 255).nullable()
    val phoneNumber = varchar("phoneno", 255).nullable()
    val orcId = varchar("orcid", 255).nullable()

    // Person by WAYF
    val orgId = varchar("orgid", 255).nullable()

    // Person by password
    val hashed = binary("hashed_password", 512).nullable() // TODO I don't remember how large the hash is
    val salt = binary("salt", 16).nullable()
}

object UserDAO {
    fun findById(id: String): Principal? {
        val users = transaction {
            Principals.select { Principals.id eq id }.limit(1).toList()
        }

        return users.singleOrNull()?.let {
            val rowId = it[Principals.id]
            val role = Role.valueOf(it[Principals.role])
            val loginType = LoginType.valueOf(it[Principals.loginType])

            when (loginType) {
                LoginType.WAYF, LoginType.PASSWORD -> {
                    val title = it[Principals.title]
                    val firstNames = it[Principals.firstNames]!!
                    val lastName = it[Principals.lastName]!!
                    val phoneNumber = it[Principals.phoneNumber]
                    val orcId = it[Principals.orcId]

                    when (loginType) {
                        LoginType.WAYF -> {
                            val organizationId = it[Principals.orgId]!!

                            Person.ByWAYF(
                                    id = rowId,
                                    title = title,
                                    role = role,
                                    firstNames = firstNames,
                                    lastName = lastName,
                                    phoneNumber = phoneNumber,
                                    orcId = orcId,
                                    organizationId = organizationId,

                                    // TODO
                                    emailAddresses = emptyList(),
                                    preferredEmailAddress = null
                            )
                        }

                        LoginType.PASSWORD -> {
                            val password = it[Principals.hashed]!!
                            val salt = it[Principals.salt]!!

                            Person.ByPassword(
                                    id = rowId,
                                    title = title,
                                    role = role,
                                    firstNames = firstNames,
                                    lastName = lastName,
                                    phoneNumber = phoneNumber,
                                    orcId = orcId,
                                    password = password,
                                    salt = salt,

                                    // TODO
                                    emailAddresses = emptyList(),
                                    preferredEmailAddress = null
                            )
                        }

                        else -> throw IllegalStateException()
                    }
                }

                LoginType.SERVICE -> {
                    ServicePrincipal(rowId, role)
                }
            }
        }
    }

    enum class LoginType {
        WAYF,
        PASSWORD,
        SERVICE
    }

    private fun principalToType(principal: Principal): LoginType = when (principal) {
        is Person.ByWAYF -> LoginType.WAYF
        is Person.ByPassword -> LoginType.PASSWORD
        is ServicePrincipal -> LoginType.SERVICE
    }

    private fun mapFieldsIntoStatement(it: UpdateBuilder<*>, user: Principal) {
        it[Principals.id] = user.id
        it[Principals.role] = user.role.name
        it[Principals.loginType] = principalToType(user).name

        when (user) {
            is Person -> {
                it[Principals.title] = user.title
                it[Principals.firstNames] = user.firstNames
                it[Principals.lastName] = user.lastName
                it[Principals.phoneNumber] = user.phoneNumber
                it[Principals.orcId] = user.orcId
                when (user) {
                    is Person.ByPassword -> {
                        it[Principals.hashed] = user.password
                        it[Principals.salt] = user.salt
                    }

                    is Person.ByWAYF -> {
                        it[Principals.orgId] = user.organizationId
                    }
                }
            }
        }
    }

    fun insert(user: Principal) {
        return transaction {
            Principals.insert {
                mapFieldsIntoStatement(it, user)
                Principals.common.setValuesForCreation(it)
            }
        }
    }

    fun update(user: Principal): Boolean {
        return transaction {
            Principals.update(
                    limit = 1,
                    where = { Principals.id eq user.id },
                    body = {
                        mapFieldsIntoStatement(it, user)
                        Principals.common.setValuesForUpdate(it)
                    }
            )
        } == 1
    }

    fun delete(user: Principal): Boolean {
        return transaction { Principals.deleteWhere { Principals.id eq user.id } } == 1
    }

}

