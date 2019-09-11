package dk.sdu.cloud.task

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.serviceInstance
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.task.rpc.TaskController
import dk.sdu.cloud.task.services.SubscriptionHibernateDao
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskHibernateDao
import dk.sdu.cloud.task.services.TaskService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsServiceClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        val subscriptionService =
            SubscriptionService(
                HostInfo(micro.serviceInstance.ipAddress ?: "localhost", port = micro.serviceInstance.port),
                wsServiceClient,
                db,
                SubscriptionHibernateDao(),
                serviceClient
            )

        val taskService = TaskService(db, TaskHibernateDao(), subscriptionService)

        with(micro.server) {
            configureControllers(
                TaskController(subscriptionService, taskService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
