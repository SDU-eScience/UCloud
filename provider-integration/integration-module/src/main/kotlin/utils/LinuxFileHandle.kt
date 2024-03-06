package dk.sdu.cloud.utils

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import libc.SEEK_SET
import libc.clib
import java.util.concurrent.atomic.AtomicBoolean

class LinuxFileHandle private constructor(private val rawFileDescriptor: Int) {
    private val closed = AtomicBoolean(false)
    val fd: Int
        get() {
            if (closed.compareAndSet(false, false)) {
                return rawFileDescriptor
            } else {
                log.warn("use after close on linux file handle $rawFileDescriptor")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
        }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            clib.close(rawFileDescriptor)
        } else {
            log.warn("close was called twice on a linux file handle!")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    fun seek(offset: Long)  {
        clib.lseek(fd, offset, SEEK_SET)
    }

    override fun toString(): String = "LinuxFileHandle(fd = $rawFileDescriptor)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinuxFileHandle

        if (rawFileDescriptor != other.rawFileDescriptor) return false

        return true
    }

    override fun hashCode(): Int {
        return rawFileDescriptor
    }

    companion object : Loggable {
        override val log: Logger = logger()

        fun createOrThrow(handle: Int, orThrow: () -> Nothing): LinuxFileHandle {
            if (handle < 0) orThrow()
            else return LinuxFileHandle(handle)
        }

        fun createOrNull(handle: Int): LinuxFileHandle? {
            if (handle < 0) return null
            return LinuxFileHandle(handle)
        }
    }
}
