package dk.sdu.cloud.mail

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.mail.api.MailServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(MailServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
