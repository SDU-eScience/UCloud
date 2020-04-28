package dk.sdu.cloud.downtime.management

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.downtime.management.api.DowntimeManagementServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object DowntimeManagementService : Service {
    override val description: ServiceDescription = DowntimeManagementServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    DowntimeManagementService.runAsStandalone(args)
}
