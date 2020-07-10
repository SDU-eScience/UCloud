package dk.sdu.cloud.app.license

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.license.rpc.*
import dk.sdu.cloud.app.license.processors.ProjectProcessor
import dk.sdu.cloud.app.license.services.AppLicenseAsyncDao
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.app.license.services.acl.AclAsyncDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    val db = AsyncDBSessionFactory(micro.databaseConfig)
    val streams = micro.eventStreamService
    val aclDao = AclAsyncDao()
    val appLicenseDao = AppLicenseAsyncDao()
    val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
    val aclService = AclService(db, authenticatedClient, aclDao)
    val appLicenseService = AppLicenseService(db, aclService, appLicenseDao, authenticatedClient)

    override fun start() {
        ProjectProcessor(streams, appLicenseService).init()
        with(micro.server) {
            configureControllers(
                AppLicenseController(appLicenseService)
            )
        }

        startServices()
    }

}
