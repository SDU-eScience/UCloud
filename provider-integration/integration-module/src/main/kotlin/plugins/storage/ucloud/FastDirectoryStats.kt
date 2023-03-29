package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.child
import dk.sdu.cloud.service.Loggable
import kotlin.math.absoluteValue
import kotlin.random.Random

interface FastDirectoryStatsInterface {
    suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean = false): Long?
}

class FastDirectoryStats(
    private val locator: DriveLocator,
    private val fs: NativeFS
) : FastDirectoryStatsInterface {
    private val ceph = CephFsFastDirectoryStats(fs)
    private val fallback = DefaultDirectoryStats(fs)

    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        return when (locator.resolveDriveByInternalFile(file).system.type) {
            FsType.CephFS -> ceph.getRecursiveSize(file, allowSlowPath)
            else -> fallback.getRecursiveSize(file, allowSlowPath)
        }
    }
}

class CephFsFastDirectoryStats(private val nativeFs: NativeFS) : Loggable, FastDirectoryStatsInterface {
    override val log = logger()

    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        return try {
            nativeFs.getExtendedAttribute(file, "ceph.dir.rbytes")?.toLongOrNull()
        } catch (ex: Throwable) {
            null
        }
    }
}

class DefaultDirectoryStats(private val fs: NativeFS) : FastDirectoryStatsInterface {
    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        if (!allowSlowPath) return null

        var size = 0L
        val stack = ArrayDeque<InternalFile>().also { it.add(file) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val stat = runCatching { fs.stat(current) }.getOrNull() ?: continue
            size += stat.size
            if (stat.fileType == FileType.DIRECTORY) {
                val files = runCatching { fs.listFiles(current) }.getOrNull() ?: continue
                stack.addAll(files.map { current.child(it) })
            }
        }

        return size
    }
}
