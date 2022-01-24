package dk.sdu.cloud.tcp

import dk.sdu.cloud.http.ByteBuffer

interface TcpConnection {
    fun read(buffer: ByteBuffer)
    fun write(buffer: ByteBuffer)
    fun close()
    fun isOpen(): Boolean
}

enum class TransportSecurity {
    TLS,
    NONE
}

fun tcpConnect(security: TransportSecurity, hostname: String, port: Int): TcpConnection {
    return when (security) {
        TransportSecurity.TLS -> TlsTcpConnection(hostname, port)
        TransportSecurity.NONE -> TODO()
    }
}
