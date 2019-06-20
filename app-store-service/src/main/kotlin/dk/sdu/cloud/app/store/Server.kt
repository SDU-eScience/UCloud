package dk.sdu.cloud.app.store

import dk.sdu.cloud.app.store.rpc.AppStoreController
import dk.sdu.cloud.app.store.rpc.ToolController
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.store.services.ToolHibernateDAO
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val toolDAO = ToolHibernateDAO()
        val applicationDAO = ApplicationHibernateDAO(toolDAO)

        val db = micro.hibernateDatabase
        val appStoreService = AppStoreService(db, applicationDAO, toolDAO)

        with(micro.server) {
            configureControllers(
                AppStoreController(appStoreService),
                ToolController(db, toolDAO)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
