package dk.sdu.cloud

import java.util.*

actual fun base64Encode(value: ByteArray): String {
    return Base64.getEncoder().encodeToString(value)
}

actual fun base64Decode(value: String): ByteArray {
    return Base64.getDecoder().decode(value)
}
