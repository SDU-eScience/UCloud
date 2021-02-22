package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class SLAService(
    private val serviceLicenseAgreement: ServiceAgreementText,
    private val db: DBContext,
    private val userDao: UserAsyncDAO
) {
    fun fetchText(): ServiceAgreementText = serviceLicenseAgreement

    suspend fun accept(version: Int, securityPrincipal: SecurityPrincipal) {
        if (version != serviceLicenseAgreement.version) {
            throw RPCException("Accepted version does not match current version", HttpStatusCode.BadRequest)
        }
        userDao.setAcceptedSlaVersion(db, securityPrincipal.username, version)
    }
}
