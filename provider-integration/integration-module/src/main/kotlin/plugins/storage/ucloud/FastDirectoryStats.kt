package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.service.Loggable
import kotlin.math.absoluteValue
import kotlin.random.Random

const val disabled = true
class CephFsFastDirectoryStats(private val nativeFs: NativeFS) : Loggable {
    override val log = logger()

    fun getRecursiveSize(file: InternalFile): Long? {
        if (disabled) return null
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rbytes")?.toLongOrNull()
    }

    fun getRecursiveEntryCount(file: InternalFile): Long {
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rentries")?.toLongOrNull() ?: -1
    }

    fun getRecursiveFileCount(file: InternalFile): Long {
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rfiles")?.toLongOrNull() ?: -1
    }

    fun getRecursiveDirectoryCount(file: InternalFile): Long {
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rsubdirs")?.toLongOrNull() ?: -1
    }

    fun getRecursiveTime(file: InternalFile): String? {
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rctime")
    }
}
