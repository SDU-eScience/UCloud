package dk.sdu.cloud.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

private const val CERT_CHUNK_SIZE = 64
object TokenValidation {
    private val certificate by lazy {
        loadCert(
            TokenValidation::class.java.classLoader.getResourceAsStream("auth.crt")
                .bufferedReader()
                .readText()
        )
    }

    private val algorithm by lazy { Algorithm.RSA256(certificate!!.publicKey as RSAPublicKey, null) }

    private fun createVerifier(audience: List<String>? = null): JWTVerifier {
        return JWT.require(algorithm).run {
            withIssuer("cloud.sdu.dk")
            if (audience != null) {
                withAudience(*audience.toTypedArray())
            }
            build()
        }
    }

    fun validate(token: RawAuthToken, audience: List<String>? = null): DecodedJWT {
        return createVerifier(audience).verify(token)
    }

    fun validateOrNull(token: RawAuthToken, audience: List<String>? = null): DecodedJWT? = try {
        createVerifier(audience).verify(token)
    } catch (ex: JWTVerificationException) {
        null
    } catch (ex: JWTDecodeException) {
        null
    }

    @Throws(CertificateException::class)
    private fun loadCert(certString: String): X509Certificate? {
        val formattedCert = formatCert(certString, true)

        return try {
            CertificateFactory.getInstance("X.509").generateCertificate(
                ByteArrayInputStream(formattedCert.toByteArray(StandardCharsets.UTF_8))
            ) as X509Certificate
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun formatCert(cert: String, heads: Boolean): String {
        var x509cert: String = cert.replace("\\x0D", "").replace("\r", "").replace("\n", "").replace(" ", "")

        if (!x509cert.isEmpty()) {
            x509cert = x509cert.replace("-----BEGINCERTIFICATE-----", "").replace("-----ENDCERTIFICATE-----", "")

            if (heads) {
                x509cert = "-----BEGIN CERTIFICATE-----\n" +
                        chunkString(x509cert, CERT_CHUNK_SIZE) + "-----END CERTIFICATE-----"
            }
        }
        return x509cert
    }

    private fun chunkString(str: String, chunkSize: Int): String {
        @Suppress("NAME_SHADOWING")
        var chunkSize = chunkSize
        var newStr = ""
        val stringLength = str.length
        var i = 0
        while (i < stringLength) {
            if (i + chunkSize > stringLength) {
                chunkSize = stringLength - i
            }
            newStr += str.substring(i, chunkSize + i) + '\n'
            i += chunkSize
        }
        return newStr
    }
}
