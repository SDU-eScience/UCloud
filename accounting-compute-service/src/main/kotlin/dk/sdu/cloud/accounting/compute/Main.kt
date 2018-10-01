package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.service.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        //initWithDefaultFeatures() TODO BAD NAME IN GENERATED DESCRIPTION FILE
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