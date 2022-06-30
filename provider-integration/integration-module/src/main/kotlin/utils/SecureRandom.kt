package dk.sdu.cloud.utils

import dk.sdu.cloud.base64Encode
import java.security.SecureRandom

private val secureRandomInstance = SecureRandom()

fun secureRandomLong(): Long {
    return secureRandomInstance.nextLong()
}

fun secureRandomInt(): Int {
    return secureRandomInstance.nextInt()
}

fun secureToken(size: Int): String {
    val buf = ByteArray(size)
    secureRandomInstance.nextBytes(buf)
    return base64Encode(buf)
}