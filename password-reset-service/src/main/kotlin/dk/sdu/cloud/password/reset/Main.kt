package dk.sdu.cloud.password.reset

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.password.reset.api.PasswordResetServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(PasswordResetServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
