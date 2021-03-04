package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException

/**
 * Retrieves the database error code from a [GenericDatabaseException] or returns null.
 *
 * This relies on internal implementation details in jasync.sql.
 */
val GenericDatabaseException.errorCode: String?
    get() = errorMessage.fields['C']

/**
 * An object containing various Postgres error codes.
 *
 * Reference: https://www.postgresql.org/docs/9.3/errcodes-appendix.html
 */
object PostgresErrorCodes {
    // Codes can also be found here. Add entries as needed.
    // https://www.postgresql.org/docs/9.3/errcodes-appendix.html
    const val UNIQUE_VIOLATION = "23505"
}

