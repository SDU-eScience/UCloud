package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMount
import dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMountDescription
import dk.sdu.cloud.app.orchestrator.api.VerifiedJobInput
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.SharedFileSystemType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import io.ktor.http.HttpStatusCode
import java.io.File

class SharedMountVerificationService {
    suspend fun verifyMounts(
        mountDescriptions: List<SharedFileSystemMountDescription>,
        userClient: AuthenticatedClient
    ): List<SharedFileSystemMount> {
        return mountDescriptions.map { mount ->
            verify(mount.sharedFileSystemId, mount.mountedAt, userClient, null, false)
        }
    }

    suspend fun verifyMounts(
        parameters: List<ApplicationParameter<*>>,
        jobInput: VerifiedJobInput,
        userClient: AuthenticatedClient
    ): List<SharedFileSystemMount> {
        return parameters
            .filterIsInstance<ApplicationParameter.SharedFileSystem>()
            .mapNotNull { parameter ->
                val value = jobInput[parameter] ?: return@mapNotNull null // Should have been verified already
                verify(
                    fileSystemId = value.fileSystemId,

                    mountedAt = parameter.mountLocation
                    ?: throw JobException.VerificationError("${parameter.name} is missing a mount location"),

                    userClient = userClient,

                    fsType = parameter.fsType,
                    exportToPeers = parameter.exportToPeers
                )
            }
    }

    private suspend fun verify(
        fileSystemId: String,
        mountedAt: String,
        userClient: AuthenticatedClient,
        fsType: SharedFileSystemType?,
        exportToPeers: Boolean
    ): SharedFileSystemMount {
        val fs = AppFileSystems.view.call(
            AppFileSystems.View.Request(fileSystemId, calculateSize = false),
            userClient
        ).orThrow()

        val normalizedPath = File(mountedAt).normalize().takeIf { it.isAbsolute }?.path
            ?: throw RPCException(
                "The following mount location is invalid: ${mountedAt}",
                HttpStatusCode.BadRequest
            )

        if (normalizedPath in DISALLOWED_LOCATIONS) {
            throw RPCException("Not allowed to mount directory at $normalizedPath", HttpStatusCode.BadRequest)
        }

        if (fs.fileSystem.id.contains(".") || fs.fileSystem.id.contains("/")) {
            throw RPCException(
                "Rejecting shared file system due to its suspicious ID: ${fs.fileSystem.id}",
                HttpStatusCode.InternalServerError
            )
        }

        return SharedFileSystemMount(fs.fileSystem, normalizedPath, fsType, exportToPeers)
    }

    companion object {
        val DISALLOWED_LOCATIONS = setOf(
            "/",
            "/bin",
            "/boot",
            "/cdrom",
            "/dev",
            "/etc",
            "/home",
            "/lib",
            "/lost+found",
            "/media",
            "/mnt",
            "/opt",
            "/proc",
            "/root",
            "/run",
            "/sbin",
            "/selinux",
            "/srv",
            "/sys",
            "/tmp",
            "/usr",
            "/var"
        )
    }
}
