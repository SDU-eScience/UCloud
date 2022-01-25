package dk.sdu.cloud.tcp

import dk.sdu.cloud.http.ByteBuffer
import dk.sdu.cloud.http.readUntilDelimiter

interface TcpConnection {
    fun read(buffer: ByteBuffer)
    fun write(buffer: ByteBuffer)
    fun close()
    fun isOpen(): Boolean
}

enum class TransportSecurity {
    TLS,
    PLAIN
}

fun tcpConnect(security: TransportSecurity, hostname: String, port: Int): TcpConnection {
    return when (security) {
        TransportSecurity.TLS -> TlsTcpConnection(hostname, port)
        TransportSecurity.PLAIN -> PlainTcpConnection(hostname, port)
    }
}

fun TcpConnection.readUntilDelimiter(delim: Byte, buffer: ByteBuffer): String {
    val inBuffer = buffer.readUntilDelimiter(delim)
    if (inBuffer != null) {
        return inBuffer
    }

    while (true) {
        val writerSpaceRemaining = buffer.writerSpaceRemaining()
        if (writerSpaceRemaining == 0) buffer.compact()

        read(buffer)

        val maybeLine = buffer.readUntilDelimiter(delim)
        if (maybeLine != null) return maybeLine
    }
}

inline fun TcpConnection.readAtLeast(minimumBytes: Int, buffer: ByteBuffer) {
    while (buffer.readerRemaining() < minimumBytes) {
        val writerSpaceRemaining = buffer.writerSpaceRemaining()
        if (writerSpaceRemaining == 0) buffer.compact()
        read(buffer)
    }
}
