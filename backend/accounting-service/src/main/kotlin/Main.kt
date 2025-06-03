package dk.sdu.cloud.accounting

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AccountingService : Service {
    override val description: ServiceDescription = AccountingServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val config = micro.configuration.requestChunkAtOrNull<Configuration>("accounting") ?:
            Configuration(
                computeUnitsNotificationLimit = 1200 ,
                storageQuotaNotificationLimitInGB = 100,
                storageUnitsNotificationLimitInGB = 100
            )
        return Server(micro, config)
    }
}

data class Configuration(
    val computeUnitsNotificationLimit: Long,
    val storageQuotaNotificationLimitInGB: Long,
    val storageUnitsNotificationLimitInGB: Long,

    val defaultTemplate: String = "Please describe the reason for applying for resources"
)

fun main(args: Array<String>) {
    AccountingService.runAsStandalone(args)
}
