package dk.sdu.cloud.app.license

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.license.rpc.*
import dk.sdu.cloud.app.license.services.AppLicenseHibernateDao
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    val db = micro.hibernateDatabase
    val aclDao = AclHibernateDao()
    val appLicenseDao = AppLicenseHibernateDao()
    val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
    val aclService = AclService(db, authenticatedClient, aclDao)
    val appLicenseService = AppLicenseService(db, aclService, appLicenseDao)

    override fun start() {
        with(micro.server) {
            configureControllers(
                AppLicenseController(appLicenseService)
            )
        }

        startServices()
    }

}
