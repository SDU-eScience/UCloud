package dk.sdu.cloud.utils

fun normalizeCertificate(certificate: String): String {
    return certificate.replace("\n", "")
        .replace("\r", "")
        .removePrefix("-----BEGIN PUBLIC KEY-----")
        .removeSuffix("-----END PUBLIC KEY-----")
        .chunked(64)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .let { "-----BEGIN PUBLIC KEY-----\n" + it + "\n-----END PUBLIC KEY-----" }
}
