package dk.sdu.cloud

import dk.sdu.cloud.debug.everythingD
import dk.sdu.cloud.service.Loggable
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import libjwt.*
import platform.posix.time

class NativeJWTValidation(val certificate: String) {
    private val arena = Arena()
    private val length = certificate.encodeToByteArray().size
    private val frozenCertificate = certificate.cstr.placeTo(arena)

    fun validateOrNull(token: String): SecurityPrincipalToken? {
        return runBlocking {
            validateOrNull(token) { jwt ->
                SecurityPrincipalToken(
                    SecurityPrincipal(
                        jwt_get_grant(jwt, "sub")?.toKStringFromUtf8() ?: return@validateOrNull null,
                        jwt_get_grant(jwt, "role")
                            ?.toKStringFromUtf8()
                            ?.let { role -> Role.values().find { it.name == role } }
                            ?: return@validateOrNull null,
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
        }
    }

    private fun debugInfo(token: String): JsonObject = JsonObject(
        mapOf(
            "token" to JsonPrimitive(token),
            "certificate" to JsonPrimitive(certificate)
        )
    )

    suspend fun <R> validateOrNull(token: String, block: (jwt: CPointer<jwt_t>) -> R?): R? = memScoped {
        val jwt = jwt_decode_kt_fix(token, frozenCertificate.reinterpret(), length) ?: run {
            debugSystem.everythingD("JWT not valid because decoding or signature verification failed", debugInfo(token))
            return null
        }
        defer { jwt_free(jwt) }

        val validation = jwt_valid_new_kt_fix(JWT_ALG_RS256) ?: error("Unable to create JWT validation")
        defer { jwt_valid_free(validation) }

        jwt_valid_set_headers(validation, 1)
        jwt_valid_set_now(validation, time(null))

        if (jwt_validate(jwt, validation) != 0U) {
            debugSystem.everythingD("JWT not valid because jwt_validate failed", debugInfo(token))
            return null
        }

        return block(jwt)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
