package dk.sdu.cloud.alerting

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.alerting.api.AlertingServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class Configuration(
    val limits: Limits? = null,
    val omissions: Omission? = null
)

data class Limits(
    val percentLimit500Status: Double,
    val storageInfoLimit: Double,
    val storageWarnLimit: Double,
    val storageCriticalLimit: Double,
    val alertWhenNumberOfShardsAvailableIsLessThan: Int?,
    val limitFor4xx: Int?,
    val limitFor5xx: Int?,
    val indexFor4xx: String?
)

data class Omission(
    val whiteListedIPs: List<String>?
)

object AlertingService : Service {
    override val description: ServiceDescription = AlertingServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        val config = micro.configuration.requestChunkAtOrNull("alerting") ?: Configuration()
        return Server(config, micro)
    }
}

fun main(args: Array<String>) {
    AlertingService.runAsStandalone(args)
}
