package dk.sdu.cloud.accounting.storage

import dk.sdu.cloud.accounting.storage.api.AccountingStorageServiceDescription
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.configuration

data class Configuration(
    val pricePerByte: String = "0"
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AccountingStorageServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAtOrNull("accounting", "storage") ?: Configuration()

    Server(
        micro.kafka,
        micro.serverProvider,
        micro.refreshingJwtCloud,
        micro,
        config
    ).start()
}
