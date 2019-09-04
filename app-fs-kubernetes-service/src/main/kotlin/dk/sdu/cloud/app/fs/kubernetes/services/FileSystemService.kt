package dk.sdu.cloud.app.fs.kubernetes.services

import dk.sdu.cloud.app.fs.kubernetes.api.ROOT_DIRECTORY
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class FileSystemService(
    private val mountPoint: File
) {
    private val rootDirectroy = File(mountPoint, ROOT_DIRECTORY)

    init {
        if (!mountPoint.exists()) {
            throw IllegalStateException("Mount point at ${mountPoint.absolutePath} does not exist!")
        }

        if (!rootDirectroy.exists()) {
            // Create and grant 777 on root directory. This directory will never really be mounted by a user directly.
            rootDirectroy.mkdir()
            Files.setPosixFilePermissions(rootDirectroy.toPath(), PosixFilePermission.values().toSet())
        }
    }

    fun create(id: String, ownerUid: Int) {
        val file = File(rootDirectroy, id).toPath()
        Files.createDirectory(file)
        Files.setPosixFilePermissions(
            file, setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
        )

        Chown.setOwner(file, ownerUid, ownerUid)
    }

    fun delete(id: String) {
        val file = File(rootDirectroy, id)
        if (!file.exists()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        file.deleteRecursively()
    }

    fun calculateSize(id: String): Long {
        val file = File(rootDirectroy, id)
        if (!file.exists()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return file.walkTopDown().map { if (it.isFile) it.length() else 0L }.sum()
    }
}
