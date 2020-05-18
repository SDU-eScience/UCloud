package dk.sdu.cloud.project

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.service.CommonServer

data class Configuration(val enabled: Boolean = true)

object ProjectService : Service {
    override val description: ServiceDescription = ProjectServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)

        val configuration = micro.configuration.requestChunkAtOrNull("project") ?: Configuration()
        return Server(micro, configuration)
    }
}

fun main(args: Array<String>) {
    ProjectService.runAsStandalone(args)
}
