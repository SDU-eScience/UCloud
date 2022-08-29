package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.service.Loggable
import kotlin.math.absoluteValue
import kotlin.random.Random

internal var useTestingSizes = false

class CephFsFastDirectoryStats(private val nativeFs: NativeFS) : Loggable {
    override val log = logger()

    fun getRecursiveSize(file: InternalFile): Long? {
        return try {
            nativeFs.getExtendedAttribute(file, "ceph.dir.rbytes").toLong()
        } catch (ex: Throwable) {
            return if (useTestingSizes) {
                Random.nextInt().absoluteValue.toLong() // up to 2GB
            } else {
                null
            }
        }
    }

    fun getRecursiveEntryCount(file: InternalFile): Long {
        return try {
            nativeFs.getExtendedAttribute(file, "ceph.dir.rentries").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveFileCount(file: InternalFile): Long {
        return try {
            nativeFs.getExtendedAttribute(file, "ceph.dir.rfiles").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveDirectoryCount(file: InternalFile): Long {
        return try {
            nativeFs.getExtendedAttribute(file, "ceph.dir.rsubdirs").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveTime(file: InternalFile): String {
        return nativeFs.getExtendedAttribute(file, "ceph.dir.rctime")
    }
}
