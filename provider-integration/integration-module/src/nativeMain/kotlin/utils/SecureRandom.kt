package dk.sdu.cloud.utils

import dk.sdu.cloud.base64Encode
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

fun secureRandomInt(): Int {
    val urandomFile = NativeFile.open("/dev/urandom", readOnly = true)
    val buf = ByteArray(4)
    buf.usePinned { pinnedBuf ->
        platform.posix.read(urandomFile.fd, pinnedBuf.addressOf(0), 4)
    }
    urandomFile.close()
    return buf[0].toInt() or
        (buf[1].toInt() shl 1) or
        (buf[2].toInt() shl 2) or
        (buf[3].toInt() shl 3)
}

fun secureToken(size: Int): String {
    val urandomFile = NativeFile.open("/dev/urandom", readOnly = true)
    val buf = ByteArray(size)
    buf.usePinned { pinnedBuf ->
        var written = 0L
        while (written < size) {
            val read = platform.posix.read(urandomFile.fd, pinnedBuf.addressOf(0), (size - written).toULong())
            written += read
            if (read == 0L) error("unexpected")
        }

        return base64Encode(buf)
    }
}