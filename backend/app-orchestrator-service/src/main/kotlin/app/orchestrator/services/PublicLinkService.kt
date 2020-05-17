package dk.sdu.cloud.app.orchestrator.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import com.github.jasync.sql.db.util.size
import dk.sdu.cloud.app.orchestrator.api.PublicLink
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

object PublicLinkTable : SQLTable("public_links") {
    val url = text("url")
    val username = text("username")
}

class PublicLinkService {
    suspend fun create(
        ctx: DBContext,
        requestedBy: String,
        urlId: String
    ) {
        if (urlId.size < 5) {
            throw RPCException(
                "Link must be at least 5 characters long",
                HttpStatusCode.BadRequest
            )
        }

        for (badWord in blacklistedWords) {
            if (urlId.contains(badWord)) {
                throw RPCException(
                    "Invalid link. Try a different name.",
                    HttpStatusCode.BadRequest
                )
            }
        }

        if (!urlId.toLowerCase().matches(regex) || urlId.toLowerCase().matches(uuidRegex)) {
            throw RPCException(
                "Invalid public link requested. Must only contain letters a-z, and numbers 0-9.",
                HttpStatusCode.BadRequest
            )
        }

        try {
            ctx.withSession { session ->
                session.insert(PublicLinkTable) {
                    set(PublicLinkTable.url, urlId)
                    set(PublicLinkTable.username, requestedBy)
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    suspend fun delete(
        ctx: DBContext,
        requestedBy: String,
        urlId: String
    ) {
        ctx.withSession { session ->
            val found = session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", requestedBy)
                        setParameter("urlId", urlId)
                    },
                    """
                        delete from public_links  
                        where
                            username = ?requestedBy and
                            url = ?urlId
                    """
                )
                .rowsAffected > 0L

            if (!found) throw RPCException("Not found", HttpStatusCode.NotFound)
        }
    }

    companion object : Loggable {
        override val log = logger()
        private val regex = Regex("([-_a-z0-9]){5,255}")
        private val uuidRegex = Regex("\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b")

        // This is, to put it mildly, a silly attempt at avoiding malicious URLs.
        // I am not too worried about malicious use at the moment though.
        private val blacklistedWords = hashSetOf(
            "login",
            "logon",
            "password",
            "passw0rd",
            "log1n",
            "log0n",
            "l0gon",
            "l0g0n"
        )
    }
}
