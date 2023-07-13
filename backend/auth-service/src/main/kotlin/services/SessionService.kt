package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.Session
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.mapItems
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.Page
import dk.sdu.cloud.service.db.async.DBContext

class SessionService(
    private val db: DBContext,
    private val refreshTokenDao: RefreshTokenAsyncDAO
) {
    suspend fun listSessions(user: SecurityPrincipalToken, paging: NormalizedPaginationRequest): Page<Session> {
        validateToken(user)

        return refreshTokenDao.findUserSessions(db, user.principal.username, paging)
            .mapItems {
                Session(
                    it.ip ?: "Unknown location",
                    it.userAgent ?: "Unknown device",
                    it.createdAt
                )
            }
    }

    suspend fun invalidateSessions(user: SecurityPrincipalToken) {
        validateToken(user)
        refreshTokenDao.invalidateUserSessions(db, user.principal.username)
    }

    private fun validateToken(user: SecurityPrincipalToken) {
        if (user.extendedByChain.isNotEmpty()) throw RPCException("Invalid token", HttpStatusCode.Forbidden)

        if (user.extendedBy != null) throw RPCException("Invalid token", HttpStatusCode.Forbidden)

        if (!user.scopes.contains(SecurityScope.ALL_WRITE)) {
            throw RPCException("Invalid token", HttpStatusCode.Forbidden)
        }
    }
}
