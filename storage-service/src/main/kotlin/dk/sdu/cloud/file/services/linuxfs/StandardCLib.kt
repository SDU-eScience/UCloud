package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import kotlinx.io.pool.useInstance

object StandardCLib {
    fun realPath(path: String): String? {
        return CLibrary.INSTANCE.realpath(path, null)
    }

    fun getxattr(path: String, name: String, maxSize: Int = 1024 * 64): String {
        return if (Platform.isMac()) {
            DefaultByteArrayPool.useInstance {
                val read = XAttrOSX.INSTANCE.getxattr(path, name, it, maxSize, 0, 0)
                if (read < 0) throw NativeException(Native.getLastError())
                String(it, 0, read, Charsets.UTF_8)
            }
        } else {
            DefaultByteArrayPool.useInstance {
                val read = CLibrary.INSTANCE.getxattr(path, name, it, maxSize)
                if (read < 0) throw NativeException(Native.getLastError())
                String(it, 0, read, Charsets.UTF_8)
            }
        }
    }

    fun setxattr(path: String, name: String, value: String, allowOverwrite: Boolean): Int {
        // Why should this be stable across platforms? Let's just make up values for different platforms.
        val opts = if (!allowOverwrite) {
            if (Platform.isLinux()) 1
            else if (Platform.isMac()) 2
            else 0
        } else {
            0
        }

        val bytes = value.toByteArray()
        return if (Platform.isMac()) {
            XAttrOSX.INSTANCE.setxattr(path, name, bytes, bytes.size, 0, opts)
        } else {
            CLibrary.INSTANCE.setxattr(path, name, bytes, bytes.size, opts)
        }
    }

    fun listxattr(path: String): List<String> {
        return DefaultByteArrayPool.useInstance { destination ->
            val listSize = if (Platform.isMac()) {
                XAttrOSX.INSTANCE.listxattr(path, destination, destination.size, 0)
            } else {
                CLibrary.INSTANCE.listxattr(path, destination, destination.size)
            }

            val result = ArrayList<String>()
            var start = 0
            for (i in destination.indices) {
                if (i == listSize) break
                if (destination[i] == 0.toByte()) {
                    result.add(String(destination, start, i - start, Charsets.UTF_8))
                    start = i + 1
                }
            }
            result
        }
    }

    fun removexattr(path: String, name: String): Int {
        return if (Platform.isMac()) {
            XAttrOSX.INSTANCE.removexattr(path, name, 0)
        } else {
            CLibrary.INSTANCE.removexattr(path, name)
        }
    }

    fun setfsuid(uid: Int): Int {
        return if (!Platform.isLinux()) {
            0
        } else {
            CLibrary.INSTANCE.setfsuid(uid)
        }
    }

    fun setfsgid(uid: Int): Int {
        return if (!Platform.isLinux()) {
            0
        } else {
            CLibrary.INSTANCE.setfsgid(uid)
        }
    }
}

class NativeException(val statusCode: Int) : RuntimeException("Native exception, code: $statusCode")
