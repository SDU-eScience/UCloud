package dk.sdu.cloud.project.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger
import java.security.SecureRandom
import kotlin.math.absoluteValue

class UniqueNameService(
    private val db: DBSessionFactory<AsyncDBConnection>,
    private val dao: ProjectDao
) {
    /**
     * Attempts to generate a unique username based on existing users.
     *
     * This function will not perform any locking of the ID and could become unavailable immediately after this
     * function returns.
     */
    suspend fun generateUniqueId(idealId: String): String {
        val normalizedUsername = idealId.lines().first().replace(SEPARATOR.toString(), "")
        if (normalizedUsername.length > CreateProjectRequest.TITLE_MAX_LENGTH) {
            throw RPCException("Project ID too long", HttpStatusCode.BadRequest)
        }

        val existingNames = db.withTransaction { session ->
            dao.findByIdPrefix(session, normalizedUsername + SEPARATOR)
        }.toSet()

        log.debug("Found ${existingNames.size} existing names!")

        val alphabet = when (existingNames.size) {
            in 0..2500 -> SIMPLE_ALPHABET
            else -> ALPHABET
        }

        // The full alphabet at 7 chars can handle significantly more than Int.MAX_VALUE (~78 billion)
        val range = if (existingNames.size < 100_000) 4 else 7

        fun generateSuffix(): String {
            return (0 until range).map {
                alphabet[secureRandom.nextInt().absoluteValue % alphabet.size]
            }.joinToString(separator = "")
        }

        // We better hope we are nowhere near the limit, because this will not perform near the limit.
        var iteration = 0
        while (iteration < 50_000) { // Guarantee that we will not loop forever
            val guess = normalizedUsername + SEPARATOR + generateSuffix()
            if (guess !in existingNames) return guess
            iteration++
        }

        throw RPCException(
            "Could not generate a unique ID (too many guesses)",
            HttpStatusCode.InternalServerError
        )
    }

    companion object : Loggable {
        override val log: Logger = logger()
        private val secureRandom = SecureRandom()
        const val SEPARATOR: Char = '#'
        private val SIMPLE_ALPHABET = ('0'..'9').toList()
        private val ALPHABET = ('A'..'Z') + ('0'..'9').toList()
    }
}
