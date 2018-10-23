package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.AppServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.serviceInstance

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.refreshingJwtCloud,
        micro.serverProvider,
        micro.hibernateDatabase,
        micro.serviceInstance
    ).start()
}
