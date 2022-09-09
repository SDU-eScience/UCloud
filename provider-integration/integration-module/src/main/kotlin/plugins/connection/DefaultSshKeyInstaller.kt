package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.app.orchestrator.api.SSHKey
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.log
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.homeDirectory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.Files as NioFiles

private const val ucloudIntegrationMarker = "ucloud-integration"

suspend fun PluginContext.installSshKeyWithDefaults(
    keys: List<SSHKey>,
) {
    // NOTE(Dan): This function only works for real users. For service users, we definitely do not want to be
    // installing any keys
    if (!config.core.launchRealUserInstances) return

    val connectionPluginConfig = config.rawPluginConfig.connection
    if (connectionPluginConfig !is ConfigSchema.Plugins.WithAutoInstallSshKey) return
    if (!connectionPluginConfig.installSshKeys) return

    val homeDir = File(homeDirectory())
    if (!homeDir.exists()) return

    val sshDir = File(homeDir, ".ssh").also { dir ->
        if (dir.mkdirs()) {
            val path = dir.toPath()
            NioFiles.setPosixFilePermissions(
                path,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                )
            )
        }
    }

    if (!sshDir.exists()) {
        debugSystem?.log(
            "Unable to initialize SSH directory for user!",
            JsonObject(mapOf("homeDirectory" to JsonPrimitive(homeDir.absolutePath))),
            MessageImportance.THIS_IS_ODD
        )
        return
    }

    val authorizedKeysFile = File(sshDir, "authorized_keys")
    if (authorizedKeysFile.exists() && !authorizedKeysFile.isFile) {
        debugSystem?.log(
            "~/.ssh/authorized_keys file of a user is actually a directory!",
            JsonObject(
                mapOf(
                    "homeDirectory" to JsonPrimitive(homeDir.absolutePath),
                    "sshDir" to JsonPrimitive(sshDir.absolutePath),
                    "authorized_keys" to JsonPrimitive(authorizedKeysFile.absolutePath),
                )
            ),
            MessageImportance.THIS_IS_ODD
        )
        return
    }

    val authorizedKeys = try {
        authorizedKeysFile.takeIf { it.exists() }?.readLines()?.map { it.trim() } ?: emptyList()
    } catch (ex: Throwable) {
        debugSystem.logThrowable("Caught an exception while reading ~/.ssh/authorized_keys", ex)
        return
    }.toMutableSet()

    run {
        // Remove all keys which are marked with the UCloud integration marker
        val iterator = authorizedKeys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.endsWith(ucloudIntegrationMarker)) iterator.remove()
        }
    }

    run {
        // Add all the keys from the callback into the set
        val withMarkerComment = keys.map { it.specification.key.trim() + " " + ucloudIntegrationMarker }
        for (key in withMarkerComment) {
            authorizedKeys.add(key)
        }
    }

    // Create a new temporary file with all the new keys
    val temporaryFile = File(sshDir, "authorized_keys-${Time.now()}.in-progress")
    try {
        temporaryFile.writeText(authorizedKeys.joinToString("\n") + "\n")
    } catch (ex: Throwable) {
        debugSystem.logThrowable("User could not create temporary authorized_keys file!", ex)
        return
    }

    try {
        NioFiles.setPosixFilePermissions(
            temporaryFile.toPath(),
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
        )
    } catch (ex: Throwable) {
        temporaryFile.delete()
        debugSystem.logThrowable("Unable to set permissions of authorized_keys file", ex)
        return
    }

    try {
        // Replace the old file with the new file atomically
        @Suppress("BlockingMethodInNonBlockingContext")
        NioFiles.move(
            temporaryFile.toPath(),
            authorizedKeysFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (ex: Throwable) {
        debugSystem.logThrowable("Failed to replace authorized_keys file", ex)
    }
}
