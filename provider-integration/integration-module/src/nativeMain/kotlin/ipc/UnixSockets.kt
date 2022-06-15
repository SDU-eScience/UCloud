package dk.sdu.cloud.ipc

import kotlinx.cinterop.*
import platform.linux.sockaddr_un
import platform.posix.*
import kotlin.math.min

object UnixSockets {
    fun buildAddress(
        scope: AutofreeScope,
        socketPath: String,
    ): sockaddr_un {
        val address = scope.alloc<sockaddr_un>()
        memset(address.ptr, 0, sizeOf<sockaddr_un>().toULong())
        address.sun_family = AF_UNIX.toUShort()
        val maxSize = 108 // according to unix(7) on linux. Kotlin/Native cannot read this directly.
        val encoded = socketPath.encodeToByteArray()
        if (encoded.size >= maxSize - 1) throw IpcException("Invalid socket name. Name is too long!")
        encoded.usePinned { buf ->
            memcpy(address.sun_path, buf.addressOf(0), encoded.size.toULong())
            address.sun_path[encoded.size] = 0
        }
        return address
    }
}

class UnixSocketPipe(
    val ioVector: iovec,
    val messageHeader: msghdr,
    val buffer: Pinned<ByteArray>,
    val controlBuffer: CPointer<out CPointed>?,
) {
    data class ReadResult(
        /**
         * Index of the delimiter
         */
        val validBytes: Int,

        /**
         * Number of bytes following the delimiter
         */
        val remainingValidBytes: Int,
    )

    fun readUntil(socket: Int, destination: ByteArray, delimiter: Byte, alreadyRead: Int): ReadResult {
        var offset = alreadyRead
        while (true) {
            val receivedInIteration = recvmsg(socket, messageHeader.ptr, 0)
            if (receivedInIteration <= 0L) {
                throw IpcException("Error while reading IPC message result = $receivedInIteration errno = $errno")
            }

            if (receivedInIteration + offset >= destination.size) {
                throw IpcException("Received too large IPC response")
            }

            val readBuffer = buffer.get()
            readBuffer.copyInto(destination, offset, 0, receivedInIteration.toInt())

            var delimFoundAt = -1
            for (i in 0 until receivedInIteration.toInt()) {
                if (readBuffer[i] == delimiter) {
                    delimFoundAt = i
                    break
                }
            }

            if (delimFoundAt != -1) {
                return ReadResult(
                    // Take into account the delimiter
                    offset + delimFoundAt,
                    receivedInIteration.toInt() - delimFoundAt - 1
                )
            } else {
                offset += receivedInIteration.toInt()
            }
        }
    }

    fun sendFully(socket: Int, encoded: ByteArray) {
        val writeBuffer = buffer.get()
        for (i in encoded.indices.step(writeBuffer.size)) {
            val endIndex = min(i + writeBuffer.size, encoded.size)
            val size = endIndex - i

            encoded.copyInto(writeBuffer, 0, i, endIndex)
            ioVector.iov_base = buffer.addressOf(0)
            ioVector.iov_len = size.toULong()

            var written = 0
            while (written < size) {
                val writtenInIteration = sendmsg(socket, messageHeader.ptr, 0)
                if (writtenInIteration == -1L) {
                    throw IpcException("Error while sending IPC message $errno")
                }

                written += writtenInIteration.toInt()
                if (written < size) {
                    ioVector.iov_base = buffer.addressOf(written)
                    ioVector.iov_len = (size - written).toULong()
                }
            }
        }
    }

    companion object {
        fun create(
            scope: AutofreeScope,
            size: Int,
            controlBufferSize: Int,
        ): UnixSocketPipe {
            val buffer = ByteArray(size).pin()
            val ioVector = scope.alloc<iovec>().apply {
                iov_base = buffer.addressOf(0)
                iov_len = size.toULong()
            }

            val controlBuffer =
                if (controlBufferSize > 0) malloc(controlBufferSize.toULong())
                else null

            scope.defer {
                if (controlBuffer != null) free(controlBuffer)
            }

            val messageHeader = scope.alloc<msghdr>().apply {
                msg_name = null
                msg_namelen = 0u
                msg_iov = ioVector.ptr
                msg_iovlen = 1u

                if (controlBufferSize > 0) {
                    msg_control = controlBuffer
                    msg_controllen = controlBufferSize.toULong()
                } else {
                    msg_control = null
                    msg_controllen = 0UL
                }
            }

            return UnixSocketPipe(ioVector, messageHeader, buffer, controlBuffer)
        }
    }
}
