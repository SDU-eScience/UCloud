package dk.sdu.cloud.file.gateway

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.file.gateway.api.FileGatewayServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object FileGatewayService : Service {
    override val description = FileGatewayServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    FileGatewayService.runAsStandalone(args)
}
