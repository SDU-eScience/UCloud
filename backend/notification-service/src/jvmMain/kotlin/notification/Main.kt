package dk.sdu.cloud.notification

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.notification.api.NotificationServiceDescription
import dk.sdu.cloud.service.CommonServer

object NotificationService : Service {
    override val description: ServiceDescription = NotificationServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    NotificationService.runAsStandalone(args)
}
