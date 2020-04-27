package dk.sdu.cloud.project

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.service.CommonServer
import org.apache.logging.log4j.Level

object ProjectService : Service {
    override val description: ServiceDescription = ProjectServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
        TODO("Not yet implemented")
    }
}

fun main(args: Array<String>) {
    ProjectService.runAsStandalone(args)
}
