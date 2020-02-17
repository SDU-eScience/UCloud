package dk.sdu.cloud.mail

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.mail.api.MailServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

data class MailConfiguration(
    val whitelist: List<String> = emptyList(),
    val fromAddress: String = "support@escience.sdu.dk"
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(MailServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAtOrNull("mail") ?: MailConfiguration()

    Server(
        config = configuration,
        micro = micro
    ).start()
}
