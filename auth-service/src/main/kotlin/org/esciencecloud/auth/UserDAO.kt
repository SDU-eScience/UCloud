package org.esciencecloud.auth

import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.NoSuchAlgorithmException
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)

enum class Role {
    USER,
    ADMIN
}

class User internal constructor(
        val fullName: String,
        val email: String,
        val roles: List<Role>,
        private val hashedPassword: ByteArray? = null,
        private val salt: ByteArray? = null
) {
    companion object {
        private val RNG = SecureRandom()
        private val SALT_LENGTH = 16
        private val ITERATIONS = 10000
        private val KEY_LENGTH = 256

        fun createUserWithPassword(fullName: String, email: String, roles: List<Role>,
                                   password: String): User {
            val (hashed, salt) = hashPassword(password)
            return User(
                    fullName,
                    email,
                    roles,
                    hashed,
                    salt
            )
        }

        fun createUserNoPassword(fullName: String, email: String, roles: List<Role>): User {
            return User(fullName, email, roles)
        }

        private fun hashPassword(password: String, salt: ByteArray = genSalt()): HashedPasswordAndSalt {
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

    fun checkPassword(plainPassword: String): Boolean {
        if (hashedPassword == null || salt == null) return false

        val incomingPasswordHashed = hashPassword(plainPassword, salt)
        return incomingPasswordHashed.hashedPassword.contentEquals(hashedPassword)
    }
}

object UserDAO {
    private val inMemoryDb = HashMap<String, User>()

    init {
        insert(User.createUserWithPassword("dan", "dan@localhost", listOf(Role.USER, Role.ADMIN), "password"))
    }

    fun findById(id: String): User? {
        return inMemoryDb[id]
    }

    fun insert(user: User): Boolean {
        if (user.primaryKey !in inMemoryDb) {
            inMemoryDb[user.primaryKey] = user
            return true
        }
        return false
    }

    fun update(user: User): Boolean {
        if (user.primaryKey in inMemoryDb) {
            inMemoryDb[user.primaryKey] = user
            return true
        }
        return false
    }

    fun delete(user: User): Boolean {
        if (user.primaryKey in inMemoryDb) {
            inMemoryDb.remove(user.primaryKey)
            return true
        }
        return false
    }

}

// We don't know, yet, which identifiers we can use that are unique. For now assume this is
val User.primaryKey: String get() = email
