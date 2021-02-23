package dk.sdu.cloud.avatar

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.avatar.api.AvatarServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AvatarService : Service {
    override val description: ServiceDescription = AvatarServiceDescription
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AvatarService.runAsStandalone(args)
}
