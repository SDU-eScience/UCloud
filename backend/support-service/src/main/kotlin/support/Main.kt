package dk.sdu.cloud.support

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

import dk.sdu.cloud.support.api.SupportServiceDescription

object SupportService : Service {
    override val description = SupportServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(
            micro
        )
    }
}

fun main(args: Array<String>) {
    SupportService.runAsStandalone(args)
}
