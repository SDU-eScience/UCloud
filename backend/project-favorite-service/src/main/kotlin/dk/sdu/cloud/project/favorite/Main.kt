package dk.sdu.cloud.project.favorite

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.favorite.api.ProjectFavoriteServiceDescription
import dk.sdu.cloud.service.CommonServer

object ProjectFavoriteService : Service {
    override val description = ProjectFavoriteServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ProjectFavoriteService.runAsStandalone(args)
}
