package dk.sdu.cloud.task

import dk.sdu.cloud.accounting.util.ProductCache
import dk.sdu.cloud.accounting.util.ProviderCommunicationsV2
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.task.rpc.TaskController
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskService
import kotlinx.coroutines.GlobalScope

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val broadcastingStream = BroadcastingStream(micro)
        val subscriptionService = SubscriptionService(broadcastingStream, GlobalScope)
        val productCache = ProductCache(db)
        val taskService = TaskService(
            db,
            subscriptionService,
            ProviderCommunicationsV2(
                micro.backgroundScope,
                micro.authenticator.authenticateClient(OutgoingHttpCall),
                productCache
            ))

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
