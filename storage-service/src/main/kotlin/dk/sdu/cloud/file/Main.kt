package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.KafkaTopicFeatureConfiguration
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.installDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.storage.api.StorageServiceDescription

val SERVICE_USER = "_${StorageServiceDescription.name}"
const val SERVICE_UNIX_USER = "storage" // Note: root is also supported. Should only be done in a container

data class StorageConfiguration(
    val filePermissionAcl: Set<String> = emptySet()
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        init(StorageServiceDescription, args)
        installDefaultFeatures(
            kafkaTopicConfig = KafkaTopicFeatureConfiguration(
                discoverDefaults = true,
                basePackages = listOf("dk.sdu.cloud.file.api")
            )
        )
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration()

    Server(
        config,
        micro
    ).start()
}
