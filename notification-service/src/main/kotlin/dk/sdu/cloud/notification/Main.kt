package dk.sdu.cloud.notification

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.notification.api.NotificationServiceDescription
import dk.sdu.cloud.service.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(NotificationServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.hibernateDatabase,
        micro.kafka,
        micro.serverProvider,
        micro.serviceInstance,
        micro.refreshingJwtCloud
    ).start()
}
