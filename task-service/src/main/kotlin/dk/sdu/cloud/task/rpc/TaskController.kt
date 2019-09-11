package dk.sdu.cloud.task.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.task.api.Tasks
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskService
import kotlinx.coroutines.delay

class TaskController(
    private val subscriptionService: SubscriptionService<*>,
    private val taskService: TaskService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(Tasks.listen) {
            val username = ctx.securityPrincipal.username
            withContext<WSCall> {
                ctx.session.addOnCloseHandler {
                    subscriptionService.onDisconnect(ctx.session)
                }
            }

            subscriptionService.onConnection(username, this@implement)

            while (true) {
                delay(1000)
            }
        }

        implement(Tasks.postStatus) {
            taskService.postStatus(ctx.securityPrincipal, request.id, request.update)
            ok(Unit)
        }

        implement(Tasks.list) {
            ok(taskService.list(ctx.securityPrincipal, request.normalize()))
        }

        implement(Tasks.view) {
            ok(taskService.find(ctx.securityPrincipal, request.id))
        }

        implement(Tasks.create) {
            ok(taskService.create(ctx.securityPrincipal, request.title, request.initialStatus, request.owner))
        }

        implement(Tasks.markAsComplete) {
            ok(taskService.markAsComplete(ctx.securityPrincipal, request.id))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
