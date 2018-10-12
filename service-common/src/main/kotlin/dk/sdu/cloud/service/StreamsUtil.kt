package dk.sdu.cloud.service

import java.io.InputStream
import java.io.OutputStream

private const val DEFAULT_BUFFER_SIZE = 1024 * 64

fun InputStream.transferTo(output: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE) {
    output.use { out ->
        this.use { ins ->
            val buffer = ByteArray(bufferSize)
            var hasMoreData = true
            while (hasMoreData) {
                var ptr = 0
                while (ptr < buffer.size && hasMoreData) {
                    val read = ins.read(buffer, ptr, buffer.size - ptr)
                    if (read <= 0) {
                        hasMoreData = false
                        break
                    }
                    ptr += read
                }
                out.write(buffer, 0, ptr)
            }
        }
    }
}
