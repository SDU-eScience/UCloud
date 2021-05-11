package dk.sdu.cloud.ipc

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

    fun readNextMessage(socket: Int, pipe: UnixSocketPipe): String {
        val decoded = if (nextMessageBoundary == -1) {
            val (validBytes, remaining) = pipe.readUntil(socket, messageBuilder, delim, writePointer)
            val decodedText = messageBuilder.decodeToString(0, validBytes, throwOnInvalidSequence = true)
            if (remaining > 0) {
                messageBuilder.copyInto(
                    messageBuilder,
                    destinationOffset = 0,
                    startIndex = validBytes + 1,
                    endIndex = validBytes + 1 + remaining
                )
            }
            writePointer = remaining
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

        nextMessageBoundary = -1
        for (i in 0 until writePointer) {
            if (messageBuilder[i] == delim) {
                nextMessageBoundary = i
                break
            }
        }
        return decoded
    }
}
