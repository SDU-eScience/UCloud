package dk.sdu.cloud.accounting

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AccountingService : Service {
    override val description: ServiceDescription = AccountingServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        val config = micro.configuration.requestChunkAtOrNull<Configuration>("accountning") ?:
            Configuration(
                notificationLimit =  5000000
            )
        return Server(micro, config)
    }
}

data class Configuration(
    val notificationLimit: Long
)

fun main(args: Array<String>) {
    AccountingService.runAsStandalone(args)
}
