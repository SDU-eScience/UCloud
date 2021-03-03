package dk.sdu.cloud.providers

import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.Loggable
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class UCloudAuthInterceptor : HandlerInterceptor {
    @Value("\${ucloud.publicCertificate:#{null}}")
    var publicCertificate: String? = null

    @Value("\${ucloud.sharedSecret:#{null}}")
    var sharedSecret: String? = null

    private val jwtVerifier by lazy {
        val cert = publicCertificate
        val secret = sharedSecret
        if (!cert.isNullOrBlank()) {
            log.debug("Configured with public cert: $cert")
            InternalTokenValidationJWT.withPublicCertificate(cert)
        } else if (!secret.isNullOrBlank()) {
            log.debug("Configured with shared secret: $secret")
            InternalTokenValidationJWT.withSharedSecret(secret)
        } else {
            throw IllegalStateException("Did not find public certificate or shared secret in the configuration")
        }
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val isRelevant = (request.requestURI ?: "/").startsWith("/ucloud/")
        if (!isRelevant) return false

        val token = (request.getHeader("Authorization") ?: "").substringAfter("Bearer ")
        val verified = jwtVerifier.validateOrNull(token)

        return when {
            verified == null ||
                verified.subject != "_UCloud" ||
                verified.issuer != "cloud.sdu.dk" -> {
                response.sendError(401)
                false
            }

            else -> {
                true
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
