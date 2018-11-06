package dk.sdu.cloud.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

sealed class TokenValidationException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class Invalid : TokenValidationException("Invalid token", HttpStatusCode.Forbidden)
    class Expired : TokenValidationException("Invalid token (expired)", HttpStatusCode.Forbidden)
    class MissingScope(scopes: List<SecurityScope>) :
        TokenValidationException("Missing scopes: $scopes", HttpStatusCode.Forbidden)
}

interface TokenValidation<TokenType> {
    fun decodeToken(token: TokenType): SecurityPrincipalToken

    fun validate(token: String, scopes: List<SecurityScope>? = null): TokenType

    fun validateOrNull(token: String, scopes: List<SecurityScope>? = null): TokenType? {
        return try {
            validate(token, scopes)
        } catch (ex: TokenValidationException) {
            null
        }
    }
}

private const val CERT_CHUNK_SIZE = 64

class TokenValidationJWT(private val algorithm: Algorithm) : TokenValidation<DecodedJWT> {
    private fun createVerifier(audience: List<String>? = null): JWTVerifier {
        return JWT.require(algorithm).run {
            withIssuer("cloud.sdu.dk")
            if (audience != null) {
                withAudience(*audience.toTypedArray())
            }
            build()
        }
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): DecodedJWT {
        return try {
            createVerifier(scopes?.map { it.toString() }).verify(token)
        } catch (ex: JWTVerificationException) {
            throw TokenValidationException.Invalid()
        } catch (ex: JWTDecodeException) {
            throw TokenValidationException.Invalid()
        }
    }

    override fun decodeToken(token: DecodedJWT): SecurityPrincipalToken {
        return token.toSecurityToken()
    }

    companion object {
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

        fun withPublicCertificate(publicCertificate: String): TokenValidationJWT {
            return TokenValidationJWT(
                Algorithm.RSA256(loadCert(publicCertificate)!!.publicKey as RSAPublicKey, null)
            )
        }

        fun withSharedSecret(sharedSecret: String): TokenValidationJWT {
            return TokenValidationJWT(Algorithm.HMAC512(sharedSecret))
        }
    }
}
