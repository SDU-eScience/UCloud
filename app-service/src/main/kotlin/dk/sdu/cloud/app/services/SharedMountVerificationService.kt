package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.SharedFileSystemMount
import dk.sdu.cloud.app.api.SharedFileSystemMountDescription
import dk.sdu.cloud.app.fs.api.AppFileSystems
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
            val fs = AppFileSystems.view.call(
                AppFileSystems.View.Request(mount.sharedFileSystemId, calculateSize = false),
                userClient
            ).orThrow()

            val normalizedPath = File(mount.mountedAt).normalize().takeIf { it.isAbsolute }?.path
                ?: throw RPCException(
                    "The following mount location is invalid: ${mount.mountedAt}",
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

            SharedFileSystemMount(fs.fileSystem, normalizedPath)
        }
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
