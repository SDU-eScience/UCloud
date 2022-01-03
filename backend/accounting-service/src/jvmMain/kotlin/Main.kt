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
                computeCreditsNotificationLimit = 50_000_000,
                computeUnitsNotificationLimit = 50 ,
                storageCreditsNotificationLimit = 50_000_000 ,
                storageQuotaNotificationLimitInGB = 100,
                storageUnitsNotificationLimitInGB = 100
            )
        return Server(micro, config)
    }
}

data class Configuration(
    val computeCreditsNotificationLimit: Long,
    val computeUnitsNotificationLimit: Long,
    val storageCreditsNotificationLimit: Long,
    val storageQuotaNotificationLimitInGB: Long,
    val storageUnitsNotificationLimitInGB: Long,

    val defaultTemplate: String = "Please describe the reason for applying for resources"
)

fun main(args: Array<String>) {
    AccountingService.runAsStandalone(args)
}
