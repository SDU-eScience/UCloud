package dk.sdu.cloud.storage

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
import dk.sdu.cloud.storage.api.StorageServiceDescription

val SERVICE_USER = "_${StorageServiceDescription.name}"
const val SERVICE_UNIX_USER = "storage" // Note: root is also supported. Should only be done in a container

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(StorageServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.serverProvider,
        micro.hibernateDatabase,
        micro.refreshingJwtCloud,
        micro.serviceInstance,
        args
    ).start()
}
