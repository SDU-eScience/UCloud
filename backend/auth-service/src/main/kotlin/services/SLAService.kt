package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext

class SLAService(
    private val serviceLicenseAgreement: ServiceAgreementText,
    private val db: DBContext,
    private val userDao: PrincipalService
) {
    fun fetchText(): ServiceAgreementText = serviceLicenseAgreement

    suspend fun accept(version: Int, securityPrincipal: SecurityPrincipal) {
        if (version != serviceLicenseAgreement.version) {
            throw RPCException("Accepted version does not match current version", HttpStatusCode.BadRequest)
        }
        userDao.setAcceptedSlaVersion(securityPrincipal.username, version, db)
    }
}
