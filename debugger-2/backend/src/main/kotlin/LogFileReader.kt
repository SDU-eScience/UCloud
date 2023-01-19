package dk.sdu.cloud.debugger

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

class LogFileReader(val directory: File, val generation: Long, val idx: Int) {
    private val file = File(directory, "$generation-$idx.log")
    private val logChannel = RandomAccessFile(file, "r").channel
    private val buf = logChannel.map(FileChannel.MapMode.READ_ONLY, 0, logChannel.size())

    private val blobChannel = RandomAccessFile(file.absolutePath.replace(".log", ".blob"), "r").channel
    private val blobBuf = blobChannel.map(FileChannel.MapMode.READ_ONLY, 0, blobChannel.size())

    var cursor = 0
        private set

    fun retrieve(idx: Int = cursor): BinaryDebugMessage<*>? {
        val offset = idx * FRAME_SIZE
        if (offset >= buf.capacity()) return null
        val type = buf.get(offset)
        return when (type.toInt()) {
            1 -> BinaryDebugMessage.ClientRequest(buf, offset)
            2 -> BinaryDebugMessage.ClientResponse(buf, offset)
            3 -> BinaryDebugMessage.ServerRequest(buf, offset)
            4 -> BinaryDebugMessage.ServerResponse(buf, offset)
            5 -> BinaryDebugMessage.DatabaseConnection(buf, offset)
            6 -> BinaryDebugMessage.DatabaseTransaction(buf, offset)
            7 -> BinaryDebugMessage.DatabaseQuery(buf, offset)
            8 -> BinaryDebugMessage.DatabaseResponse(buf, offset)
            9 -> BinaryDebugMessage.Log(buf, offset)
            else -> null
        }
    }

    fun isValid(idx: Int = cursor): Boolean {
        val offset = idx * FRAME_SIZE
        if (offset >= buf.capacity()) return false
        val type = buf.get(offset)
        return type in 1..9
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

    fun resetCursor() {
        cursor = 0
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

    fun seekTimestamp(filterBefore: Long) {
        seekToEnd()

        var min = 0
        var max = cursor
        while (min != max) {
            cursor = ((max - min) / 2) + min
            val message = retrieve() ?: error("Message is no longer valid!")
            if (message.timestamp >= filterBefore) {
                max = cursor - 1
            } else {
                min = cursor + 1
            }
        }
    }

    fun decodeText(largeText: LargeText): String {
        val decoded = largeText.value.decodeToString()
        if (decoded.startsWith(LargeText.OVERFLOW_PREFIX)) {
            val split = decoded.removePrefix(LargeText.OVERFLOW_PREFIX).split(LargeText.OVERFLOW_SEP, limit = 2)
            if (split.size != 2) return "INVALID BLOB"
            val pos = split[0].toIntOrNull() ?: return "INVALID BLOB"
            val preview = split[1]

            val size = blobBuf.getInt(pos)
            val buffer = ByteArray(size)
            blobBuf.get(pos + 4, buffer)
            return buffer.decodeToString()
        } else {
            return decoded
        }
    }

    fun close() {
        logChannel.close()
    }

    companion object {
        fun exists(directory: File, generation: Long, idx: Int): Boolean {
            return File(directory, "$generation-$idx.log").exists()
        }
    }
}
