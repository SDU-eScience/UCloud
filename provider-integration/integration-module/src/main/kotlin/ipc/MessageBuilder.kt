package dk.sdu.cloud.ipc

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class MessageBuilder(capacity: Int) {
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
                if (socket.read(buffer) == -1) throw IpcException("Connection lost")
                buffer.flip()

                if (buffer.remaining() + writePointer >= messageBuilder.size) throw IpcException("Received too large message")

                val messageSize = buffer.remaining()
                buffer.get(messageBuilder, writePointer, messageSize)
                writePointer += messageSize
                messageBoundary = messageBuilder.indexOf(delim)
            }

            val remaining = writePointer - messageBoundary - 1
            val decodedText = messageBuilder.decodeToString(0, messageBoundary, throwOnInvalidSequence = true)
            if (remaining > 0) {
                messageBuilder.copyInto(
                    messageBuilder,
                    destinationOffset = 0,
                    startIndex = messageBoundary + 1,
                    endIndex = messageBoundary + 1 + remaining
                )
                writePointer = remaining
            } else {
                writePointer = 0
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
