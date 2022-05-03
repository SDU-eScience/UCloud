package dk.sdu.cloud

import dk.sdu.cloud.service.Loggable
import kotlinx.cinterop.*
import libjwt.*
import platform.posix.time

class NativeJWTValidation(private val certificate: String) {
    private val arena = Arena()
    private val length = certificate.encodeToByteArray().size
    private val frozenCertificate = certificate.cstr.placeTo(arena)

    @OptIn(ExperimentalUnsignedTypes::class)
    fun validateOrNull(token: String): SecurityPrincipalToken? = memScoped {
        val jwt = jwt_decode_kt_fix(token, frozenCertificate.reinterpret(), length) ?: return null
        defer { jwt_free(jwt) }

        val validation = jwt_valid_new_kt_fix(JWT_ALG_RS256) ?: error("Unable to create JWT validation")
        defer { jwt_valid_free(validation) }

        jwt_valid_set_headers(validation, 1)
        jwt_valid_set_now(validation, time(null))

        if (jwt_validate(jwt, validation) != 0U) {
            return null
        }

        return SecurityPrincipalToken(
            SecurityPrincipal(
                jwt_get_grant(jwt, "sub")?.toKStringFromUtf8() ?: return null,
                jwt_get_grant(jwt, "role")
                    ?.toKStringFromUtf8()
                    ?.let { role -> Role.values().find { it.name == role } }
                    ?: return null,
                "",
                "",
                0L
            ),
            listOf(SecurityScope.ALL_WRITE),
            jwt_get_grant_int(jwt, "iat"),
            jwt_get_grant_int(jwt, "exp"),
            null,
        )
    }

    fun <R> validateOrNull(token: String, block: (jwt: CPointer<jwt_t>) -> R): R? = memScoped {
        val jwt = jwt_decode_kt_fix(token, frozenCertificate.reinterpret(), length) ?: return null
        defer { jwt_free(jwt) }

        val validation = jwt_valid_new_kt_fix(JWT_ALG_RS256) ?: error("Unable to create JWT validation")
        defer { jwt_valid_free(validation) }

        jwt_valid_set_headers(validation, 1)
        jwt_valid_set_now(validation, time(null))

        if (jwt_validate(jwt, validation) != 0U) {
            return null
        }

        return block(jwt)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
