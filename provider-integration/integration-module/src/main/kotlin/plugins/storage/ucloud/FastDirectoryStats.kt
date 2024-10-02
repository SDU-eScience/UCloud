package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.readString

interface FastDirectoryStatsInterface {
    // Should return size in Bytes
    suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean = false): Long?
}

class FastDirectoryStats(
    private val locator: DriveLocator,
    private val fs: NativeFS,
    private val fallbackStorageScanMethod: ConfigSchema.Core.FallbackStorageScanMethod?
) : FastDirectoryStatsInterface {
    private val ceph = CephFsFastDirectoryStats(fs)
    private val fallback = DefaultDirectoryStats(fs, fallbackStorageScanMethod)
    private val mockStats = MockStats(fs)

    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        return when (locator.resolveDriveByInternalFile(file).system.type) {
            FsType.CephFS -> ceph.getRecursiveSize(file, allowSlowPath)
            FsType.Generic -> fallback.getRecursiveSize(file, allowSlowPath)
            FsType.GenericWithMockUsage -> mockStats.getRecursiveSize(file, allowSlowPath)
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

class DefaultDirectoryStats(
    private val fs: NativeFS,
    private val fallbackStorageScanMethod: ConfigSchema.Core.FallbackStorageScanMethod?
) : FastDirectoryStatsInterface {
    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        if (!allowSlowPath) {
            return null
        }

        suspend fun useDuMethod(): Long? {
            return try {
                val (_, stdout, _) = executeCommandToText("/usr/bin/du") {
                    addArg("-sb")
                    addArg(file.path)
                }
                val regex = "^\\d+".toRegex()
                val match = regex.find(stdout) ?: return null
                val sizeInBytes = stdout.substring(match.range).toLong()
                sizeInBytes
            } catch (ex: Throwable) {
                null
            }
        }

        return when (fallbackStorageScanMethod) {
            ConfigSchema.Core.FallbackStorageScanMethod.DU -> {
                useDuMethod()
            }
            ConfigSchema.Core.FallbackStorageScanMethod.GDU -> {
                try {
                    val (_, stdout, _) = executeCommandToText("/usr/bin/gdu") {
                        addArg("-n") //Non-interactive
                        addArg("-s") //Summarized
                        addArg("-p") //No progress shown
                        addArg("--no-prefix") //get size in bytes
                        addArg(file.path)
                    }
                    val regex = "^\\d+".toRegex()
                    val match = regex.find(stdout) ?: return null
                    val sizeInBytes = stdout.substring(match.range).toLong()
                    sizeInBytes
                } catch (ex: Throwable) {
                    null
                }
            }
            else -> {
                useDuMethod()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

}

class MockStats(private val fs: NativeFS) : FastDirectoryStatsInterface {
    override suspend fun getRecursiveSize(file: InternalFile, allowSlowPath: Boolean): Long? {
        if (!allowSlowPath) return null

        val mockFile = InternalFile(joinPath(file.path, "usage.txt"))
        return try {
            fs.openForReading(mockFile).readString().trim().toLong()
        } catch (ex: Throwable) {
            0L
        }
    }
}

