package dk.sdu.cloud.app.aau

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.app.aau.api.AppAauServiceDescription
import dk.sdu.cloud.app.kubernetes.api.AauComputeMaintenance
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.*

data class Configuration(
    val providerRefreshToken: String? = null,
    val ucloudCertificate: String? = null,
)
object AppAauService : Service {
    override val description = AppAauServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val configuration = micro.configuration.requestChunkAtOrNull<Configuration>("app", "aau")

        if (configuration == null && micro.developmentModeEnabled) {
            // Needed for correct codegen
            return object : CommonServer {
                override val micro: Micro = micro
                override val log: Logger = logger()

                override fun start() {
                    configureControllers(
                        object : Controller {
                            override fun configure(rpcServer: RpcServer) {
                                with(rpcServer) {
                                    implement(AauComputeMaintenance.retrieve) {
                                        throw IllegalStateException()
                                    }
                                    implement(AauComputeMaintenance.sendUpdate) {
                                        throw IllegalStateException()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        return Server(micro, configuration ?: Configuration())
    }
}

fun main(args: Array<String>) {
    AppAauService.runAsStandalone(args)
}
