package dk.sdu.cloud.zenodo.services.hibernate

import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.zenodo.services.OAuthTokens
import dk.sdu.cloud.zenodo.services.ZenodoOAuthStateStore
import java.util.Date

class ZenodoOAuthHibernateStateStore : ZenodoOAuthStateStore<HibernateSession> {
    override fun storeStateTokenForUser(session: HibernateSession, cloudUser: String, token: String, returnTo: String) {
        val existing = ZenStateTokenEntity[session, cloudUser] ?: ZenStateTokenEntity(cloudUser)
        existing.token = token
        existing.returnTo = returnTo
        session.saveOrUpdate(existing)
    }

    override fun resolveUserAndRedirectFromStateToken(
        session: HibernateSession,
        stateToken: String
    ): Pair<String, String>? {
        return session
            .criteria<ZenStateTokenEntity> { entity[ZenStateTokenEntity::token] equal stateToken }
            .uniqueResult()
            ?.let { it.owner to it.returnTo }
    }

    override fun storeAccessAndRefreshToken(session: HibernateSession, cloudUser: String, token: OAuthTokens) {
        val existing = ZenOAuthTokenEntity[session, cloudUser] ?: ZenOAuthTokenEntity(cloudUser)
        existing.accessToken = token.accessToken
        existing.refreshToken = token.refreshToken
        existing.expiresAt = Date(token.expiresAt)
        session.saveOrUpdate(existing)
    }

    override fun retrieveCurrentTokenForUser(session: HibernateSession, cloudUser: String): OAuthTokens? {
        return ZenOAuthTokenEntity[session, cloudUser]?.toModel()
    }

    override fun invalidateUser(session: HibernateSession, cloudUser: String) {
        session.delete(ZenOAuthTokenEntity[session, cloudUser] ?: return)
    }
}
