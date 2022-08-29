package dk.sdu.cloud.auth.api

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

// Contains a few utility methods extracted from com.onelogin.saml2.util.Util

internal fun loadCert(certString: String): X509Certificate? {
    val formattedCert = formatCert(certString, true)
    return try {
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(formattedCert.toByteArray(StandardCharsets.UTF_8))) as X509Certificate
    } catch (e: IllegalArgumentException) {
        null
    }
}


internal fun formatCert(cert: String?, heads: Boolean): String {
    var x509cert = ""
    if (cert != null) {
        x509cert = cert.replace("\\x0D", "").replace("\r", "").replace("\n", "").replace(" ", "")
        if (!x509cert.isEmpty()) {
            x509cert = x509cert.replace("-----BEGINCERTIFICATE-----", "").replace("-----ENDCERTIFICATE-----", "")
            if (heads) {
                x509cert = """
                    -----BEGIN CERTIFICATE-----
                    ${chunkString(x509cert, 64)}-----END CERTIFICATE-----
                    """.trimIndent()
            }
        }
    }
    return x509cert
}

private fun chunkString(str: String, chunkSize: Int): String {
    var currentChunkSize = chunkSize
    var newStr = ""
    val stringLength = str.length
    var i = 0
    while (i < stringLength) {
        if (i + currentChunkSize > stringLength) {
            currentChunkSize = stringLength - i
        }
        newStr += str.substring(i, currentChunkSize + i) + '\n'
        i += currentChunkSize
    }
    return newStr
}

internal fun loadPrivateKey(keyString: String): PrivateKey? {
    var extractedKey: String = formatPrivateKey(keyString, false)
    extractedKey = chunkString(extractedKey, 64)
    val kf = KeyFactory.getInstance("RSA")

    return try {
        val encoded = Base64.getDecoder().decode(extractedKey)
        val keySpec = PKCS8EncodedKeySpec(encoded)
        kf.generatePrivate(keySpec)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun formatPrivateKey(key: String?, heads: Boolean): String {
    var xKey: String = ""
    if (key != null) {
        xKey = key.replace("\\x0D", "").replace("\r", "").replace("\n", "").replace(" ", "")
        if (!xKey.isEmpty()) {
            if (xKey.startsWith("-----BEGINPRIVATEKEY-----")) {
                xKey = xKey.replace("-----BEGINPRIVATEKEY-----", "").replace("-----ENDPRIVATEKEY-----", "")
                if (heads) {
                    xKey = """
                        -----BEGIN PRIVATE KEY-----
                        ${chunkString(xKey, 64)}-----END PRIVATE KEY-----
                        """.trimIndent()
                }
            } else {
                xKey = xKey.replace("-----BEGINRSAPRIVATEKEY-----", "").replace("-----ENDRSAPRIVATEKEY-----", "")
                if (heads) {
                    xKey = """
                        -----BEGIN RSA PRIVATE KEY-----
                        ${chunkString(xKey, 64)}-----END RSA PRIVATE KEY-----
                        """.trimIndent()
                }
            }
        }
    }
    return xKey
}
