package dk.sdu.cloud.project.repository

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.project.repository.api.ProjectRepositoryServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object ProjectRepositoryService : Service {
    override val description: ServiceDescription = ProjectRepositoryServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ProjectRepositoryService.runAsStandalone(args)
}
