package dk.sdu.cloud.support

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

import dk.sdu.cloud.support.api.SupportServiceDescription

data class Configuration(
    val notifiers: Notifiers = Notifiers()
)

data class Notifiers(
    val slack: SlackNotifierConfig? = null
)

data class SlackNotifierConfig(
    val hook: String
)

object SupportService : Service {
    override val description = SupportServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        val config = micro.configuration.requestChunkAtOrNull("ticket") ?: Configuration()
        return Server(
            micro,
            config
        )
    }
}

fun main(args: Array<String>) {
    SupportService.runAsStandalone(args)
}
