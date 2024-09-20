package dk.sdu.cloud.controllers

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.task.api.TasksProvider

class TaskController(
    val controllerContext: ControllerContext,
    ): Controller {
    override fun configure(rpcServer: RpcServer) {
        val tasks = TasksProvider(controllerContext.configuration.core.providerId)
        with (rpcServer) {
            implement(tasks.userAction) {
                controllerContext.configuration.plugins.files.values.forEach { plugin ->
                    with(requestContext(controllerContext)) {
                        with(plugin) {
                            ok(modifyTask(request))
                        }
                    }
                }
            }
        }
    }
}