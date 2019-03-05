package dk.sdu.cloud.notification

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.serviceInstance
import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.NotificationHibernateDAO
import dk.sdu.cloud.notification.services.SubscriptionHibernateDao
import dk.sdu.cloud.notification.services.SubscriptionService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger

class Server(override val micro: Micro) : CommonServer {
    override val log: Logger = logger()
    private lateinit var subscriptionService: SubscriptionService<HibernateSession>

    override fun start() {
        val db = micro.hibernateDatabase
        log.info("Creating core services")
        val notificationDao = NotificationHibernateDAO()
        val localhost = run {
            val ip = micro.serviceInstance.ipAddress
                ?: throw IllegalStateException("micro.serviceInstance.ipAddress == null")

            val port = micro.serviceInstance.port

            HostInfo(ip, port = port)
        }
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)
        subscriptionService = SubscriptionService(localhost, wsClient, micro.hibernateDatabase, SubscriptionHibernateDao())
        log.info("Core services constructed!")

        with(micro.server) {
            configureControllers(
                NotificationController(
                    db,
                    notificationDao,
                    subscriptionService
                )
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        subscriptionService.close()
    }
}
