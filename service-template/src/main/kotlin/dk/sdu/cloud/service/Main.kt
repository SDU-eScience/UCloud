package dk.sdu.cloud.{{ serviceName }}

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.{{ serviceName }}.api.TestServiceDescription
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.HibernateFeature

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures({{ serviceNameTitle }}ServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.serverProvider,
        micro.refreshingJwtCloud,
        micro
    ).start()
}
