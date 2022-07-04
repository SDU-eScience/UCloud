package dk.sdu.cloud.ipc

import libc.clib
import java.nio.ByteBuffer

class IpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
const val ipcSocketName = "ucloud.sock"