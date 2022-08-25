package dk.sdu.cloud.task

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.task.rpc.TaskController
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskAsyncDao
import dk.sdu.cloud.task.services.TaskService
import kotlinx.coroutines.GlobalScope

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val broadcastingStream = BroadcastingStream(micro)
        val subscriptionService = SubscriptionService(broadcastingStream, GlobalScope)
        val taskService = TaskService(db, TaskAsyncDao(), subscriptionService)

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
