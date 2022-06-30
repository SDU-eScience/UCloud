package dk.sdu.cloud.ipc

import libc.clib
import java.nio.ByteBuffer

class IpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
const val ipcSocketName = "ucloud.sock"

fun ipcSendFully(socket: Int, data: ByteBuffer) {
    while (data.hasRemaining()) {
        val size = clib.sendMessage(socket, data)
        if (size == -1) throw IpcException("Error while sending IPC message")
        data.position(data.position() + size)
    }
}
