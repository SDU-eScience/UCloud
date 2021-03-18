package dk.sdu.cloud.task

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.task.api.TaskServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object TaskService : Service {
    override val description: ServiceDescription = TaskServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    TaskService.runAsStandalone(args)
}
