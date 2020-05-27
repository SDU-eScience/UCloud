package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class SLAService<Session>(
    private val serviceLicenseAgreement: ServiceAgreementText,
    private val db: DBSessionFactory<Session>,
    private val userDao: UserDAO<Session>
) {
    fun fetchText(): ServiceAgreementText = serviceLicenseAgreement

    suspend fun accept(version: Int, securityPrincipal: SecurityPrincipal) {
        if (version != serviceLicenseAgreement.version) {
            throw RPCException("Accepted version does not match current version", HttpStatusCode.BadRequest)
        }

        db.withTransaction { session ->
            userDao.setAcceptedSlaVersion(session,securityPrincipal.username, version)
        }
    }
}
