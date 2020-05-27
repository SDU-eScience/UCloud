package dk.sdu.cloud.mail

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.mail.api.MailServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class MailConfiguration(
    val whitelist: List<String> = emptyList(),
    val fromAddress: String = "support@escience.sdu.dk"
)
object MailService : Service {
    override val description = MailServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        val configuration = micro.configuration.requestChunkAtOrNull("mail") ?: MailConfiguration()

        return Server(
            config = configuration,
            micro = micro
        )
    }
}

fun main(args: Array<String>) {
    MailService.runAsStandalone(args)
}
