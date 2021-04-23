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
                notificationLimit =  5000000,
                licenses = listOf("AAU-MATLAB", "STATA16-CBS-TEST", "tek-ansys", "tek-comsol")
            )
        return Server(micro, config)
    }
}

data class Configuration(
    val notificationLimit: Long,
    val licenses: List<String>
)

fun main(args: Array<String>) {
    AccountingService.runAsStandalone(args)
}
