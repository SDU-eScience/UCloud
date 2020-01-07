package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.Session
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.time.LocalDateTime
import java.time.ZoneOffset

class SessionService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val refreshTokenDao: RefreshTokenDAO<DBSession>
) {
    suspend fun listSessions(user: SecurityPrincipalToken, paging: NormalizedPaginationRequest): Page<Session> {
        validateToken(user)

        return db.withTransaction { session ->
            refreshTokenDao.findUserSessions(session, user.principal.username, paging)
        }.mapItems {
            Session(
                it.ip ?: "Unknown location",
                it.userAgent ?: "Unknown device",
                it.createdAt
            )
        }
    }

    suspend fun invalidateSessions(user: SecurityPrincipalToken) {
        validateToken(user)

        db.withTransaction { session ->
            refreshTokenDao.invalidateUserSessions(session, user.principal.username)
        }
    }

    private fun validateToken(user: SecurityPrincipalToken) {
        if (user.extendedByChain.isNotEmpty()) throw RPCException("Invalid token", HttpStatusCode.Forbidden)

        if (user.extendedBy != null) throw RPCException("Invalid token", HttpStatusCode.Forbidden)

        if (!user.scopes.contains(SecurityScope.ALL_WRITE)) {
            throw RPCException("Invalid token", HttpStatusCode.Forbidden)
        }
    }

    companion object {
        // In case there is no expiry for a token we will list it as expiring in ~thousand years.
        // Feel free to update this if the software somehow survives for that long ;)
        private val DEFAULT_EXPIRY = LocalDateTime.of(3000, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC) * 1000L
    }
}
