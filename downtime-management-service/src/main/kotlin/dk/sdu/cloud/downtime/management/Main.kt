package dk.sdu.cloud.downtime.management

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.downtime.management.api.DowntimeManagementServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(DowntimeManagementServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
