package dk.sdu.cloud.debugger

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class ContextReader(private val directory: File, private val generation: String, private val idx: Int) {
    private val channel = FileChannel.open(
        File(directory, "$generation-$idx.ctx").toPath(),
        StandardOpenOption.READ,
    )

    @PublishedApi
    internal val buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 16)

    private val descriptor = DebugContextDescriptor(buf, 4096)

    var cursor = 0
        private set

    fun isValid(idx: Int = cursor): Boolean {
        return retrieve(idx) != null
    }

    fun retrieve(idx: Int = cursor): DebugContextDescriptor? {
        if (descriptor.offset + DebugContextDescriptor.size >= buf.capacity()) return null
        descriptor.offset += DebugContextDescriptor.size
        if (descriptor.id == 0) return null
        return descriptor
    }

    fun previous(): Boolean {
        if (cursor <= 0) return false
        cursor--
        return true
    }

    fun next(): Boolean {
        cursor++
        if (!isValid()) {
            cursor--
            return false
        }
        return true
    }

    fun jumpTo(idx: Int): Boolean {
        val oldCursor = cursor
        cursor = idx
        if (!isValid()) {
            cursor = oldCursor
            return false
        }
        return true
    }

    fun seekToEnd() {
        var min = 0
        var max = buf.capacity() / FRAME_SIZE
        while (min != max) {
            cursor = ((max - min) / 2) + min
            if (isValid()) {
                min = cursor + 1
            } else {
                max = cursor - 1
            }
        }
    }
}
