package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.api.StorageServiceDescription

val SERVICE_USER = "_${StorageServiceDescription.name}"
@Deprecated("No longer in use") const val SERVICE_UNIX_USER = "storage"

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
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.configuration.requestChunkAtOrNull("storage") ?: StorageConfiguration(),
        micro
    ).start()
}
