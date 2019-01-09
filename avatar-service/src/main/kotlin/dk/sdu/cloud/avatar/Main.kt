package dk.sdu.cloud.avatar

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.avatar.api.AvatarServiceDescription
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.hibernateDatabase

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AvatarServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.serverProvider,
        micro.refreshingJwtCloud,
        micro.hibernateDatabase,
        micro
    ).start()
}
