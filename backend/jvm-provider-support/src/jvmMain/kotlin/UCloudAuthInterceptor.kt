package dk.sdu.cloud.providers

import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.Loggable
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class UCloudJwtVerifier {
    @Value("\${ucloud.certificate:#{null}}")
    var publicCertificate: String? = null

    @Value("\${ucloud.sharedSecret:#{null}}")
    var sharedSecret: String? = null

    private val jwtVerifier by lazy {
        val cert = publicCertificate
        val secret = sharedSecret
        if (!cert.isNullOrBlank()) {
            UCloudAuthInterceptor.log.debug("Configured with public cert: $cert")
            InternalTokenValidationJWT.withPublicCertificate(cert)
        } else if (!secret.isNullOrBlank()) {
            UCloudAuthInterceptor.log.debug("Configured with shared secret: $secret")
            InternalTokenValidationJWT.withSharedSecret(secret)
        } else {
            throw IllegalStateException("Did not find public certificate or shared secret in the configuration")
        }
    }

    fun isValid(token: String): Boolean {
        val verified = jwtVerifier.validateOrNull(token)

        return when {
            verified == null ||
                verified.subject != "_UCloud" ||
                verified.issuer != "cloud.sdu.dk" -> {
                false
            }

            else -> {
                true
            }
        }
    }
}

@Component
class UCloudAuthInterceptor(
    private val verifier: UCloudJwtVerifier
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val isRelevant = (request.requestURI ?: "/").startsWith("/ucloud/")
        if (!isRelevant) return false

        if (request.getHeader("Upgrade").equals("websocket", ignoreCase = true) &&
            request.getHeader("Connection").equals("upgrade", ignoreCase = true)) {
            return true
        }

        val token = (request.getHeader("Authorization") ?: "").substringAfter("Bearer ")
        return if (!verifier.isValid(token)) {
            response.sendError(401)
            false
        } else {
            true
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
