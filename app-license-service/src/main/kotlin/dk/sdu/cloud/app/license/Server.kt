package dk.sdu.cloud.app.license

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.license.rpc.*
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    val db = micro.hibernateDatabase
    val aclDao = AclHibernateDao()
    val aclService = AclService(db, aclDao)
    val appLicenseService = AppLicenseService(aclService)

    override fun start() {
        with(micro.server) {
            configureControllers(
                AppLicenseController(appLicenseService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
