package dk.sdu.cloud.app.aau

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.app.aau.api.AppAauServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.service.CommonServer

object AppAauService : Service {
    override val description = AppAauServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AppAauService.runAsStandalone(args)
}
