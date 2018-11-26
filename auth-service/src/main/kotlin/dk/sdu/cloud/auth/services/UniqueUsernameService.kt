package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import java.security.SecureRandom

class UniqueUsernameService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDAO: UserDAO<DBSession>
) {
    /**
     * Attempts to generate a unique username based on existing users.
     *
     * This function will not perform any locking of the username and could become unavailable immediately after this
     * function returns.
     */
    fun generateUniqueName(idealUsername: String): String {
        val normalizedUsername = idealUsername.lines().first().replace(SEPARATOR.toString(), "")
        if (normalizedUsername.length > 250) throw IllegalArgumentException("Username too long")

        val existingNames = db.withTransaction { session ->
            userDAO.findByUsernamePrefix(session, normalizedUsername + SEPARATOR)
        }.map { it.id }.toSet()

        val alphabet = when (existingNames.size) {
            in 0..2500 -> SIMPLE_ALPHABET
            else -> ALPHABET
        }

        // The full alphabet at 7 chars can handle significantly more than Int.MAX_VALUE (~78 billion)
        val range = if (existingNames.size < 100_000) 4 else 7

        fun generateSuffix(): String {
            return (0 until range).map {
                alphabet[secureRandom.nextInt() % alphabet.size]
            }.joinToString(separator = "")
        }

        // We better hope we are nowhere near the limit, because this will not perform near the limit.
        var iteration = 0
        while (iteration < 50_000) { // It will technically never loop forever
            val guess = normalizedUsername + SEPARATOR + generateSuffix()
            if (guess !in existingNames) return guess
            iteration++
        }

        throw RuntimeException("Could not generate a unique username (too many guesses)")
    }

    companion object {
        private val secureRandom = SecureRandom()
        const val SEPARATOR: Char = '#'
        private val SIMPLE_ALPHABET = ('0'..'9').toList()
        private val ALPHABET = ('A'..'Z') + ('0'..'9').toList()
    }
}
