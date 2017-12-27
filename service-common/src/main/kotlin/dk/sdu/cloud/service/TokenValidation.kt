package dk.sdu.cloud.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

object TokenValidation {
    private val verifier by lazy {
        val certificate = loadCert(TokenValidation::class.java.classLoader.getResourceAsStream("auth.crt")
                .bufferedReader()
                .readText())

        JWT.require(Algorithm.RSA256(certificate!!.publicKey as RSAPublicKey))
                .withIssuer("cloud.sdu.dk")
                .build()
    }

    fun validate(token: RawAuthToken): DecodedJWT =
        verifier.verify(token)

    fun validateOrNull(token: RawAuthToken): DecodedJWT? = try {
        verifier.verify(token)
    } catch (ex: JWTVerificationException) {
        null
    }

    @Throws(CertificateException::class)
    private fun loadCert(certString: String): X509Certificate? {
        val formattedCert = formatCert(certString, true)

        return try {
            CertificateFactory.getInstance("X.509").generateCertificate(
                    ByteArrayInputStream(formattedCert.toByteArray(StandardCharsets.UTF_8))) as X509Certificate
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun formatCert(cert: String, heads: Boolean): String {
        var x509cert: String = cert.replace("\\x0D", "").replace("\r", "").replace("\n", "").replace(" ", "")

        if (!x509cert.isEmpty()) {
            x509cert = x509cert.replace("-----BEGINCERTIFICATE-----", "").replace("-----ENDCERTIFICATE-----", "")

            if (heads) {
                x509cert = "-----BEGIN CERTIFICATE-----\n" + chunkString(x509cert, 64) + "-----END CERTIFICATE-----"
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