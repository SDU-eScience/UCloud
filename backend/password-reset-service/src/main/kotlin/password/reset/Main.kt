package dk.sdu.cloud.password.reset

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.password.reset.api.PasswordResetServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object PasswordResetService : Service {
    override val description: ServiceDescription = PasswordResetServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    PasswordResetService.runAsStandalone(args)
}
