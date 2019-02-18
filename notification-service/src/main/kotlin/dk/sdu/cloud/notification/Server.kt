package dk.sdu.cloud.notification

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.NotificationHibernateDAO
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger

class Server(override val micro: Micro) : CommonServer {
    override val log: Logger = logger()

    override fun start() {
        val db = micro.hibernateDatabase
        log.info("Creating core services")
        val notificationDao = NotificationHibernateDAO()
        log.info("Core services constructed!")

        with(micro.server) {
            configureControllers(
                NotificationController(
                    db,
                    notificationDao
                )
            )
        }

        startServices()
    }
}
