package dk.sdu.cloud.app.aau

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.app.aau.api.AppAauServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.service.CommonServer

data class Configuration(
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
)
object AppAauService : Service {
    override val description = AppAauServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val configuration = micro.configuration.requestChunkAtOrNull("app", "aau") ?: Configuration()
        return Server(micro, configuration)
    }
}

fun main(args: Array<String>) {
    AppAauService.runAsStandalone(args)
}
