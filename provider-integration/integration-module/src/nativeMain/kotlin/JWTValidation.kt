package dk.sdu.cloud

import dk.sdu.cloud.service.Loggable
import kotlinx.cinterop.*
import libjwt.*
import platform.posix.time
import kotlin.native.concurrent.freeze

class NativeJWTValidation(certificate: String) {
    private val arena = Arena()
    private val length = certificate.encodeToByteArray().size
    private val frozenCertificate = certificate.cstr.placeTo(arena)

    @OptIn(ExperimentalUnsignedTypes::class)
    fun validateOrNull(token: String): SecurityPrincipalToken? = memScoped {
        // TODO(Dan): Valgrind is reporting that we are definitely leaking in this function
        val jwtRef = allocArrayOfPointersTo<jwt_t>(null)
        if (jwt_new(jwtRef) != 0) error("Unable to create JWT")
        defer { jwt_free(jwtRef[0]) }

        val valid = allocArrayOfPointersTo<jwt_valid_t>(null)
        if (jwt_valid_new(valid, JWT_ALG_RS256) != 0) error("Unable to create JWT validation")
        defer { jwt_valid_free(valid[0]) }

        jwt_valid_set_headers(valid[0], 1)
        jwt_valid_set_now(valid[0], time(null))
        jwt_decode(jwtRef, token, frozenCertificate.reinterpret(), length)
        if (jwt_validate(jwtRef[0], valid[0]) != 0U) {
            return null
        }

        return SecurityPrincipalToken(
            SecurityPrincipal(
                jwt_get_grant(jwtRef[0], "sub")?.toKStringFromUtf8() ?: return null,
                jwt_get_grant(jwtRef[0], "role")
                    ?.toKStringFromUtf8()
                    ?.let { role -> Role.values().find { it.name == role } }
                    ?: return null,
                "",
                "",
                0L
            ),
            listOf(SecurityScope.ALL_WRITE),
            jwt_get_grant_int(jwtRef[0], "iat"),
            jwt_get_grant_int(jwtRef[0], "exp"),
            null,
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
