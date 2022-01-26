package dk.sdu.cloud

actual fun base64Encode(value: ByteArray): String {
    return base64EncodeCommon(value)
}

actual fun base64Decode(value: String): ByteArray {
    return base64DecodeCommon(value)
}
