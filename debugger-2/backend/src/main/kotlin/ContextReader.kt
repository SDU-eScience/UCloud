package dk.sdu.cloud.debugger

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class ContextReader(directory: File, val generation: Long, val idx: Int) {
    private val channel = FileChannel.open(
        File(directory, buildContextFilePath(generation, idx)).toPath(),
        StandardOpenOption.READ,
    )

    @PublishedApi
    internal val buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())

    private val descriptor = DebugContextDescriptor(buf, 4096)

    var cursor = 0
        private set

    fun isValid(idx: Int = cursor): Boolean {
        return retrieve(idx) != null
    }   

    fun retrieve(idx: Int = cursor): DebugContextDescriptor? {
        descriptor.offset = idx * DebugContextDescriptor.size + 4096
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

    fun resetCursor() {
        cursor = 0
    }

    fun close() {
        channel.close()
    }

    fun logAllEntries() {
        val currentCursor = this.cursor
        this.resetCursor()
        while (this.next()) {
            val entry = this.retrieve() ?: continue
            println("id: ${entry.id}, name: ${entry.name}, ts: ${entry.timestamp}")
        }
        this.cursor = currentCursor
    }

    companion object {
        fun exists(directory: File, generation: Long, idx: Int): Boolean {
            return File(directory, buildContextFilePath(generation, idx)).exists()
        }
    }
}
