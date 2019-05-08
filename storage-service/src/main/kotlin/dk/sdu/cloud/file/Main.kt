package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.StandardCLib
import dk.sdu.cloud.micro.KafkaTopicFeatureConfiguration
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.installDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.storage.api.StorageServiceDescription
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.UserPrincipal
import kotlin.system.exitProcess

val SERVICE_USER = "_${StorageServiceDescription.name}"
@Deprecated("No longer in use")
const val SERVICE_UNIX_USER = "storage"

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
