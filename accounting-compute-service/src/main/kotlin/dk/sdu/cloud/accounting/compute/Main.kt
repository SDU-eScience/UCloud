package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.api.AccountingComputeServiceDescription
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
        initWithDefaultFeatures(AccountingComputeServiceDescription, args)
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
