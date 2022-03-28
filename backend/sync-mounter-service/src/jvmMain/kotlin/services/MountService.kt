package dk.sdu.cloud.sync.mounter.services

import com.sun.jna.Native
import dk.sdu.cloud.sync.mounter.SyncMounterConfiguration
import dk.sdu.cloud.sync.mounter.api.*
import dk.sdu.cloud.service.Loggable
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString

class MountService(
    private val config: SyncMounterConfiguration,
    private val ready: AtomicBoolean
) {
    private val configurationId = UUID.randomUUID().toString()

    @OptIn(ExperimentalPathApi::class)
    fun mount(request: MountRequest) {
        item@for (item in request.items) {
            if (!item.path.startsWith(config.cephfsBaseMount)) {
                log.info("Invalid source of: ${item.path}")
                continue
            }

            val source = File(item.path)

            if (!source.exists() || !source.isDirectory) {
                log.info("Invalid source of: ${item.path}")
                continue
            }

            val target = File(joinPath(config.syncBaseMount, item.id.toString()))
            if (!target.canonicalPath.startsWith(config.syncBaseMount) || target.canonicalPath == config.syncBaseMount) {
                log.info("Invalid target of: ${item.path}")
                continue
            }

            if (target.exists()) {
                unmount(UnmountRequest(listOf(MountFolderId(item.id))))
            }

            if (!target.mkdir()) {
                log.info("Failed to create target of: ${item.path}")
                continue
            }

            val components = source.path.removePrefix("/").removeSuffix("/").split("/")
            val fileDescriptors = IntArray(components.size) { -1 }

            try {
                fileDescriptors[0] = CLibrary.INSTANCE.open("/${components[0]}", O_NOFOLLOW, 0)

                for (i in 1 until fileDescriptors.size) {
                    val previousFd = fileDescriptors[i - 1]
                    if (previousFd < 0) {
                        log.info("Unable to open: ${item.path}")
                        continue@item
                    }

                    fileDescriptors[i] = CLibrary.INSTANCE.openat(fileDescriptors[i - 1], components[i], O_NOFOLLOW, 0)

                    if (fileDescriptors[i] < 0) {
                        log.info("Unable to open: ${item.path}")
                        continue@item
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
                    log.info("Unable to mount: ${item.path}")
                    continue@item
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
        for (item in request.items ) {
            val target = File(joinPath(config.syncBaseMount, item.id.toString()))
            if (!target.canonicalPath.startsWith(config.syncBaseMount) || target.canonicalPath == config.syncBaseMount) {
                log.info("Umount: Invalid target of: ${target}")
                continue
            }

            if (!target.exists()) {
                log.info("Umount: Target does not exist: ${target}")
                continue
            }

            if (CLibrary.INSTANCE.umount(target.canonicalPath) < 0) {
                if (Native.getLastError() != EINVAL) {
                    // We assume EINVAL to mean that it is not a valid mount-point, which can happen at start up.
                    log.info("Umount: Unable to unmount target: ${target}")
                    continue
                }
            }

            if (target.exists() && !target.delete()) {
                log.info("Umount: Failed to delete: ${target}")
                continue
            }
        }

        return UnmountResponse
    }

    fun ready(req: ReadyRequest): ReadyResponse {
        // NOTE(Dan): Right now, we just don't differentiate between the two. But we reserve the rights to change this
        // in the future.
        val isReady = ready.get() && req.configurationId == configurationId
        return ReadyResponse(isReady, configurationId)
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

    companion object : Loggable {
        override val log = logger()
        private const val EINVAL = 22
        private const val MS_BIND = 4096L
        private const val O_NOFOLLOW = 0x20000
    }
}

