package dk.sdu.cloud.filesearch

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.filesearch.api.FilesearchServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object FileSearchService : Service {
    override val description = FilesearchServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    FileSearchService.runAsStandalone(args)
}
