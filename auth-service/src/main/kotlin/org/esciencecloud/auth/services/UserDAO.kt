package org.esciencecloud.auth.services

import org.esciencecloud.auth.api.Role
import org.esciencecloud.auth.api.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)

internal object UserUtils {
    private val RNG = SecureRandom()
    private val SALT_LENGTH = 16
    private val ITERATIONS = 10000
    private val KEY_LENGTH = 256

    fun createUserWithPassword(fullName: String, email: String, role: Role,
                               password: String): User {
        val (hashed, salt) = hashPassword(password)
        return User(
                fullName,
                email,
                role,
                hashed,
                salt
        )
    }

    fun createUserNoPassword(fullName: String, email: String, role: Role): User {
        return User(fullName, email, role)
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

fun User.checkPassword(plainPassword: String): Boolean {
    if (hashedPassword == null || salt == null) return false

    val incomingPasswordHashed = UserUtils.hashPassword(plainPassword, salt)
    return incomingPasswordHashed.hashedPassword.contentEquals(hashedPassword)
}

object Users : Table() {
    val email = varchar("email", 255).primaryKey()
    val fullName = varchar("full_name", 255)
    val role = integer("role")
    val hashed = binary("hashed_password", 512).nullable() // TODO I don't remember how large the hash is
    val salt = binary("salt", 16).nullable()
}

object UserDAO {
    init {
        //insert(UserUtils.createUserWithPassword("dan", "dan@localhost", Role.ADMIN, "password"))
    }

    fun findById(id: String): User? {
        val users = transaction {
            Users.select { Users.email eq id }.limit(1).toList()
        }

        return users.singleOrNull()?.let {
            User(
                    fullName = it[Users.fullName],
                    email = it[Users.email],
                    role = Role.values()[it[Users.role]],
                    hashedPassword = it[Users.hashed],
                    salt = it[Users.salt]
            )
        }
    }

    private fun mapFieldsIntoStatement(it: UpdateBuilder<*>, user: User) {
        it[Users.email] = user.email
        it[Users.fullName] = user.fullName
        it[Users.role] = user.role.ordinal
        it[Users.hashed] = user.hashedPassword
        it[Users.salt] = user.salt
    }

    fun insert(user: User): Boolean {
        return try {
            transaction {
                Users.insert {
                    mapFieldsIntoStatement(it, user)
                }

                true
            }
        } catch (_: Exception) {
            // TODO Shouldn't just ignore all exceptions
            false
        }
    }

    fun update(user: User): Boolean {
        return transaction {
            Users.update(
                    limit = 1,
                    where = { Users.email eq user.email },
                    body = { mapFieldsIntoStatement(it, user) }
            )
        } == 1
    }

    fun delete(user: User): Boolean {
        return transaction { Users.deleteWhere { Users.email eq user.email } } == 1
    }

}

// We don't know, yet, which identifiers we can use that are unique. For now assume this is
val User.primaryKey: String get() = email
