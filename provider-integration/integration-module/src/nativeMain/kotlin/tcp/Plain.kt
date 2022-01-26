package dk.sdu.cloud.tcp

import dk.sdu.cloud.http.ByteBuffer
import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.read as posixRead
import platform.posix.write as posixWrite
import platform.posix.close as posixClose

class TcpException(message: String, error: Int) : RuntimeException("tcp error: $message ($error)")

class PlainTcpConnection(
    private val host: String,
    private val port: Int
) : TcpConnection {
    private val socketFd: Int
    private var isOpen = false

    init {
        socketFd = memScoped {
            // First we specify the hints about our connection. The only real requirement we have is that it uses TCP,
            // we don't really care about how it gets there, so we set everything else to be unspecified.
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_STREAM // TCP, please.
            hints.ai_flags = 0
            hints.ai_protocol = 0

            // Next, we use these hints to retrieve information about our requested host at the specified port
            val result = allocPointerTo<addrinfo>()
            if (getaddrinfo(host, port.toString(), hints.ptr, result.ptr) != 0) {
                throw TcpException("getaddrinfo failed: $host:$port", errno)
            }

            // This returns a list of things we can try. We iterate them until we find one which succeeds.
            var socketFd: Int = -1
            var currentInfo: CPointer<addrinfo>? = result.value
            var success = false
            while (currentInfo != null) {
                val info = currentInfo.pointed
                currentInfo = info.ai_next

                socketFd = socket(info.ai_family, info.ai_socktype, info.ai_protocol)
                if (socketFd == -1) continue

                if (connect(socketFd, info.ai_addr, info.ai_addrlen) != -1) {
                    success = true
                    break
                }
                posixClose(socketFd)
            }

            // If we didn't manage to successfully connect, notify the user
            freeaddrinfo(result.value)
            if (!success) throw TcpException("Could not connect to $host:$port", -1)

            // Otherwise, return the open file descriptor
            socketFd
        }

        isOpen = true
    }

    override fun read(buffer: ByteBuffer) {
        if (!isOpen) throw TcpException("Connection is closed (isOpen() = false)", -1)

        if (buffer.writerSpaceRemaining() == 0) {
            throw TcpException("No more space in buffer! Cannot read more data.", -1)
        }

        val ret = posixRead(socketFd, buffer.rawMemoryPinned.addressOf(buffer.writerIndex),
            buffer.writerSpaceRemaining().toULong())

        if (ret <= 0) {
            close()
        } else {
            buffer.writerIndex += ret.toInt()
        }
    }

    override fun write(buffer: ByteBuffer) {
        if (!isOpen) throw TcpException("Connection is closed (isOpen() = false)", -1)
        if (buffer.readerRemaining() == 0) return

        val ret = posixWrite(socketFd, buffer.rawMemoryPinned.addressOf(buffer.readerIndex),
            buffer.readerRemaining().toULong())

        if (ret <= 0) {
            close()
        } else {
            buffer.readerIndex += ret.toInt()
        }
    }

    override fun close() {
        isOpen = false
        posixClose(socketFd)
    }

    override fun isOpen(): Boolean = isOpen
}
