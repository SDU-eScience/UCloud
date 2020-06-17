package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.services.linuxfs.NativeFS
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import java.io.File

object CephFsFastDirectoryStats : Loggable {
    override val log = logger()

    fun getRecursiveSize(path: File): Long {
        return try {
            NativeFS.getExtendedAttribute(path, "ceph.dir.rbytes").toLong()
        } catch (ex: Throwable) {
            log.info(ex.stackTraceToString())
            return -1
        }
    }

    fun getRecursiveEntryCount(path: File): Long {
        return try {
            NativeFS.getExtendedAttribute(path, "ceph.dir.rentries").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveFileCount(path: File): Long {
        return try {
            NativeFS.getExtendedAttribute(path, "ceph.dir.rfiles").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveDirectoryCount(path: File): Long {
        return try {
            NativeFS.getExtendedAttribute(path, "ceph.dir.rsubdirs").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    fun getRecursiveTime(path: File): String {
        return NativeFS.getExtendedAttribute(path, "ceph.dir.rctime")
    }
}
