package dk.sdu.cloud.debugger

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

const val DESCRIPTOR_START_OFFSET = 4096

class ContextReader(directory: File, val generation: Long, val idx: Int) {
    private val channel = FileChannel.open(
        File(directory, buildContextFilePath(generation, idx)).toPath(),
        StandardOpenOption.READ,
    )

    @PublishedApi
    internal val buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())

    private val descriptor = DebugContextDescriptor(buf, DESCRIPTOR_START_OFFSET)

    var cursor = 0
        private set

    fun isValid(idx: Int = cursor): Boolean {
        return retrieve(idx) != null
    }

    fun retrieve(idx: Int = cursor): DebugContextDescriptor? {
        descriptor.offset = idx * DebugContextDescriptor.size + DESCRIPTOR_START_OFFSET
        if (descriptor.offset > buf.capacity()) return null
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

    private fun seekToEnd() {
        var min = 0
        var max = (buf.capacity() - DESCRIPTOR_START_OFFSET) / (DebugContextDescriptor.size)
        while (min <= max) {
            this.cursor = ((max - min) / 2) + min
            if (isValid(cursor)) {
                min = this.cursor + 1
            } else {
                max = this.cursor - 1
            }
        }
        this.cursor = max
    }

    fun seekLastTimestamp(): Long? {
        val cursorPosition = this.cursor
        seekToEnd()
        val ts = this.retrieve()?.timestamp
        this.cursor = cursorPosition
        return ts
    }

    private fun resetCursor() {
        cursor = 0
    }

    fun close() {
        channel.close()
    }

    fun logAllEntries() {
        val currentCursor = this.cursor
        this.resetCursor()
        while (this.next()) {
            val entry = this.retrieve() ?: break
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
