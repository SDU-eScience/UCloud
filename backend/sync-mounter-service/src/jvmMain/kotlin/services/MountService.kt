package dk.sdu.cloud.sync.mounter.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.sync.mounter.SyncMounterConfiguration
import dk.sdu.cloud.sync.mounter.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString

class MountService(
    val config: SyncMounterConfiguration,
    val ready: AtomicBoolean
) {
    private val MS_BIND: Long = 4096
    private val O_NOFOLLOW = 0x20000

    @OptIn(ExperimentalPathApi::class)
    fun mount(request: MountRequest) {
        request.items.forEach { item ->
            val source = File(joinPath(config.cephfsBaseMount, item.path))
            if (!source.exists() || !source.isDirectory) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Invalid source")
            }

            val target = File(joinPath(config.syncBaseMount, item.id.toString()))
            if (!target.canonicalPath.startsWith(config.syncBaseMount) || target.canonicalPath == config.syncBaseMount) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Invalid target")
            }

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

                val pid = ProcessHandle.current().pid()
                val realSourcePath = Paths.get(joinPath("/proc", pid.toString(), "fd", fileDescriptors.last().toString()))

                val mountValue = CLibrary.INSTANCE.mount(
                    realSourcePath.pathString,
                    target.canonicalPath,
                    null,
                    MS_BIND,
                    null
                )

                if (mountValue < 0) {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to mount folder")
                }


            } catch (ex: Throwable) {
                throw ex
            } finally {
                fileDescriptors.closeAll()
            }
        }

        return MountResponse
    }

    fun unmount(request: UnmountRequest) {
        request.items.forEach { item ->
            val target = File(joinPath(config.syncBaseMount, item.id.toString()))
            if (!target.canonicalPath.startsWith(config.syncBaseMount) || target.canonicalPath == config.syncBaseMount) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Invalid target")
            }

            if (!target.exists()) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Target does not exist")
            }

            if (CLibrary.INSTANCE.umount(target.canonicalPath) < 0) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to unmount target")
            }

            if (!target.delete()) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to delete target")
            }
        }

        return UnmountResponse
    }

    fun ready(): ReadyResponse {
        return ReadyResponse(ready.get())
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
