package dk.sdu.cloud.ipc

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.defaultMapper
import jdk.net.ExtendedSocketOptions
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import libc.clib
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

fun main() {
    val socket = File("./ucloud.sock")
    if (socket.exists()) socket.delete()

    val channel = ServerSocketChannel
        .open(StandardProtocolFamily.UNIX)
        .bind(UnixDomainSocketAddress.of("./ucloud.sock"))

    clib.chmod(socket.absolutePath, "777".toInt(8))

    while (true) {
        val client = channel.accept()
        ProcessingScope.launch {
            try {
                processIpcClient(client)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                throw ex
            }
        }
    }
}

class MessageBuilderNio(capacity: Int) {
    private val messageBuilder = ByteArray(capacity)

    /**
     * Index of the next known delimiter (inclusive) or -1 if none is exist.
     */
    private var nextMessageBoundary = -1

    /**
     * Index immediately following the last valid byte. It is safe to write data at this position.
     */
    private var writePointer = 0

    private val delim = '\n'.code.toByte()

    fun readNextMessage(socket: SocketChannel, buffer: ByteBuffer): String {
        val decoded = if (nextMessageBoundary == -1) {
            var messageBoundary = -1
            while (messageBoundary == -1) {
                buffer.clear()
                socket.read(buffer)
                buffer.flip()

                if (buffer.remaining() + writePointer >= messageBuilder.size) throw IpcException("Received too large message")

                buffer.get(messageBuilder, writePointer, buffer.remaining())
                writePointer += buffer.remaining()
                messageBoundary = messageBuilder.indexOf(delim)
            }

            val remaining = writePointer - messageBoundary
            val decodedText = messageBuilder.decodeToString(0, messageBoundary, throwOnInvalidSequence = true)
            if (remaining > 0) {
                messageBuilder.copyInto(
                    messageBuilder,
                    destinationOffset = 0,
                    startIndex = messageBoundary + 1,
                    endIndex = messageBoundary + 1 + remaining
                )
            }
            decodedText
        } else {
            val decodedText = messageBuilder.decodeToString(0, nextMessageBoundary, throwOnInvalidSequence = true)
            if (writePointer > nextMessageBoundary + 1) {
                messageBuilder.copyInto(
                    messageBuilder,
                    destinationOffset = 0,
                    startIndex = nextMessageBoundary + 1,
                    endIndex = writePointer
                )
            }
            writePointer = writePointer - nextMessageBoundary - 1
            decodedText
        }

        nextMessageBoundary = messageBuilder.indexOf(delim)
        if (nextMessageBoundary >= writePointer) nextMessageBoundary = -1
        return decoded
    }
}
