package dk.sdu.cloud.provider

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.provider.api.ProviderServiceDescription
import dk.sdu.cloud.service.CommonServer

object ProviderService : Service {
    override val description = ProviderServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ProviderService.runAsStandalone(args)
}
