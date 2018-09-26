package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.db.HibernateSession

class TwoFactorHibernateDAO : TwoFactorDAO<HibernateSession> {
    override fun findEnforcedCredentialsOrNull(session: HibernateSession, principal: Principal): TwoFactorCredentials? {
        TODO("not implemented")
    }

    override fun findActiveChallengeOrNull(session: HibernateSession, challengeId: String): TwoFactorChallenge? {
        TODO("not implemented")
    }

    override fun createCredentials(session: HibernateSession, twoFactorCredentials: TwoFactorCredentials): Long {
        TODO("not implemented")
    }

    override fun createChallenge(session: HibernateSession, challenge: TwoFactorChallenge) {
        TODO("not implemented")
    }
}