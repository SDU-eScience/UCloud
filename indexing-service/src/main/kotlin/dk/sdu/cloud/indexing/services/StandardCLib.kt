package dk.sdu.cloud.indexing.services

import com.sun.jna.Native
import com.sun.jna.Platform
import kotlinx.io.pool.useInstance

object StandardCLib {
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
}

class NativeException(val statusCode: Int) : RuntimeException("Native exception, code: $statusCode")
