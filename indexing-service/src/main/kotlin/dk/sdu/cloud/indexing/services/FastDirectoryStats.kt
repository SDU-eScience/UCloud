package dk.sdu.cloud.indexing.services

interface FastDirectoryStats {
    fun getRecursiveSize(path: String): Long
    fun getRecursiveEntryCount(path: String): Long
    fun getRecursiveFileCount(path: String): Long
    fun getRecursiveDirectoryCount(path: String): Long
    fun getRecursiveTime(path: String): String
}

object CephFsFastDirectoryStats : FastDirectoryStats {
    override fun getRecursiveSize(path: String): Long {
        return try {
            StandardCLib.getxattr(path, "ceph.dir.rbytes").toLong()
        } catch (ex: Throwable) {
            return -1
        }
    }

    override fun getRecursiveEntryCount(path: String): Long {
        return try {
            StandardCLib.getxattr(path, "ceph.dir.rentries").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    override fun getRecursiveFileCount(path: String): Long {
        return try {
            StandardCLib.getxattr(path, "ceph.dir.rfiles").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    override fun getRecursiveDirectoryCount(path: String): Long {
        return try {
            StandardCLib.getxattr(path, "ceph.dir.rsubdirs").toLong()
        } catch (ex: Throwable) {
            -1
        }
    }

    override fun getRecursiveTime(path: String): String {
        return StandardCLib.getxattr(path, "ceph.dir.rctime")
    }
}
