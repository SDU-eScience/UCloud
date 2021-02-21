package dk.sdu.cloud.project

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.service.CommonServer

object ProjectService : Service {
    override val description: ServiceDescription = ProjectServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ProjectService.runAsStandalone(args)
}
