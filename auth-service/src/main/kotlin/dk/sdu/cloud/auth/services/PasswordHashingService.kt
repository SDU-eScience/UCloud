package dk.sdu.cloud.auth.services

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHashingService {
    fun hashPassword(password: String, salt: ByteArray = genSalt()): HashedPasswordAndSalt {
        val passwordArr = password.toCharArray()
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(passwordArr, salt, iterations, keyLength)
            val key = skf.generateSecret(spec)
            return HashedPasswordAndSalt(key.encoded, salt)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException(e)
        }
    }

    fun checkPassword(correctPassword: ByteArray, salt: ByteArray, plainPassword: String): Boolean {
        val incomingPasswordHashed = hashPassword(plainPassword, salt)
        return incomingPasswordHashed.hashedPassword.contentEquals(correctPassword)
    }

    private fun genSalt(): ByteArray = ByteArray(saltLength).also { secureRandom.nextBytes(it) }

    companion object {
        private val secureRandom = SecureRandom()
        private const val saltLength = 16
        private const val iterations = 10000
        private const val keyLength = 256
    }
}
