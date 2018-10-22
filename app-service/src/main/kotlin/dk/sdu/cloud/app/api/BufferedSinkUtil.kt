package dk.sdu.cloud.app.api

import okio.BufferedSink
import java.io.InputStream

fun BufferedSink.copyFrom(source: InputStream, bufferSize: Int = 1024 * 64) {
    // Closing 'out' is not something the HTTP client likes.
    val out = this

    val buffer = ByteArray(bufferSize)
    var hasMoreData = true
    while (hasMoreData) {
        var ptr = 0
        while (ptr < buffer.size && hasMoreData) {
            val read = source.read(buffer, ptr, buffer.size - ptr)
            if (read <= 0) {
                hasMoreData = false
                break
            }
            ptr += read
        }
        out.write(buffer, 0, ptr)
    }
    out.flush()
}
