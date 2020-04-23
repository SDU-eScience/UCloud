package dk.sdu.cloud.downtime.management

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.downtime.management.rpc.*
import dk.sdu.cloud.downtime.management.services.DowntimeDAO
import dk.sdu.cloud.downtime.management.services.DowntimeHibernateDao
import dk.sdu.cloud.downtime.management.services.DowntimeManagementService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {

        val db = micro.hibernateDatabase
        val downtimeDAO = DowntimeHibernateDao()
        val downtimeService = DowntimeManagementService(db, downtimeDAO)
        with(micro.server) {
            configureControllers(
                DowntimeManagementController(downtimeService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
