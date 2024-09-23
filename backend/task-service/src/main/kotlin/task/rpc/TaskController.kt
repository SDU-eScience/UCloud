package dk.sdu.cloud.task.rpc

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.task.api.TaskState
import dk.sdu.cloud.task.api.Tasks
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskService
import kotlinx.coroutines.delay

class TaskController(
    private val subscriptionService: SubscriptionService,
    private val taskService: TaskService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(Tasks.listen) {
            try {
                val username = actorAndProject.actor.safeUsername()
                withContext<WSCall> {
                    ctx.session.addOnCloseHandler {
                        subscriptionService.onDisconnect(ctx.session)
                    }
                }

                subscriptionService.onConnection(username, this@implement)
            } catch (ex: Throwable) {
                log.warn(ex.stackTraceToString())
            }

            while (true) {
                delay(1000)
            }
        }

        implement(Tasks.pauseOrCancel) {
            taskService.pauseOrCancel(actorAndProject, request)
            ok(Unit)
        }

        implement(Tasks.postStatus) {
            taskService.postStatus(actorAndProject, request.update)
            ok(Unit)
        }

        implement(Tasks.browse) {
            ok(taskService.browse(actorAndProject, request))
        }

        implement(Tasks.retrieve) {
            ok(
                taskService.findById(
                    actorAndProject,
                    request.id
                )
            )
        }

        implement(Tasks.create) {
            ok(taskService.create(actorAndProject, request))
        }

        implement(Tasks.markAsComplete) {
            ok(taskService.markAsComplete(actorAndProject, request.id))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
