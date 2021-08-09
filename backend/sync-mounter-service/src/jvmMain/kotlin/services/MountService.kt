package dk.sdu.cloud.sync.mounter.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.sync.mounter.SyncMounterConfiguration
import dk.sdu.cloud.sync.mounter.api.*
import io.ktor.http.*
import java.io.File

class MountService(
    val config: SyncMounterConfiguration
) {
    private val MS_BIND: Long = 4096
    private val O_NOFOLLOW = 0x20000

    fun mount(request: MountRequest): MountResponse {
        request.items.forEach { item ->
            val source = File(joinPath(config.cephfsBaseMount ?: "/mnt/cephfs", item.path))
            if (!source.exists() || !source.isDirectory) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Invalid source")
            }

            val target = File(joinPath(config.syncBaseMount ?: "/mnt/sync", item.id))
            if (target.exists()) {
                unmount(UnmountRequest(listOf(MountFolderId(item.id))))
            }

            if (!target.mkdir()) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to create target")
            }

            val components = source.path.removePrefix("/").removeSuffix("/").split("/")
            val fileDescriptors = IntArray(components.size) { -1 }

            try {
                fileDescriptors[0] = CLibrary.INSTANCE.open("/${components[0]}", O_NOFOLLOW, 0)

                for (i in 1 until fileDescriptors.size) {
                    val previousFd = fileDescriptors[i - 1]
                    if (previousFd < 0) {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Invalid source")
                    }

                    fileDescriptors[i] = CLibrary.INSTANCE.openat(fileDescriptors[i - 1], components[i], O_NOFOLLOW, 0)

                    if (fileDescriptors[i] < 0) {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                    }

                    CLibrary.INSTANCE.close(previousFd)
                }
            } catch (ex: Throwable) {
                fileDescriptors.closeAll()
                throw ex
            }
            CLibrary.INSTANCE.close(fileDescriptors.last())

            CLibrary.INSTANCE.mount(source.absolutePath, target.absolutePath, null, MS_BIND, null)
        }

        return MountResponse
    }

    fun unmount(request: UnmountRequest): UnmountResponse {
        request.items.forEach { item ->
            val target = File(joinPath(config.syncBaseMount ?: "/mnt/sync", item.id))
            if (!target.exists()) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Target does not exist")
            }

            CLibrary.INSTANCE.umount(target.absolutePath)

            if (!target.delete()) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to delete target")
            }
        }

        return UnmountResponse
    }

    fun ready(): ReadyResponse {
        if (File(joinPath(config.syncBaseMount ?: "/mnt/sync", "ready")).exists()) {
            return ReadyResponse(true)
        }
        return ReadyResponse(false)
    }

    private fun joinPath(vararg components: String): String {
        val basePath = components.map {
            it.removeSuffix("/")
        }.joinToString("/") + "/"
        return if (basePath.startsWith("/")) basePath
        else "/$basePath"
    }

    private fun IntArray.closeAll() {
        for (descriptor in this) {
            if (descriptor > 0) {
                CLibrary.INSTANCE.close(descriptor)
            }
        }
    }
}