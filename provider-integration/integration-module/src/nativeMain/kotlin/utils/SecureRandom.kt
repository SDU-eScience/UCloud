package dk.sdu.cloud.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

fun secureRandomLong(): Long {
    val urandomFile = NativeFile.open("/dev/urandom", readOnly = true)
    val buf = ByteArray(8)
    buf.usePinned { pinnedBuf ->
        platform.posix.read(urandomFile.fd, pinnedBuf.addressOf(0), 8)
    }
    urandomFile.close()
    return buf[0].toLong() or
        (buf[1].toLong() shl 1) or
        (buf[2].toLong() shl 2) or
        (buf[3].toLong() shl 3) or
        (buf[4].toLong() shl 4) or
        (buf[5].toLong() shl 5) or
        (buf[6].toLong() shl 6) or
        (buf[7].toLong() shl 7)
}
