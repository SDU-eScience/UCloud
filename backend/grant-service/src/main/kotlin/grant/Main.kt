package dk.sdu.cloud.grant

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.grant.api.GrantServiceDescription
import dk.sdu.cloud.service.CommonServer

object GrantService : Service {
    override val description = GrantServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    GrantService.runAsStandalone(args)
}
