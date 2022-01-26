package dk.sdu.cloud.service

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException

sealed class TokenValidationException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class Invalid : TokenValidationException("Invalid token", HttpStatusCode.Forbidden)
    class Expired : TokenValidationException("Invalid token (expired)", HttpStatusCode.Forbidden)
    class MissingScope(scopes: List<SecurityScope>) :
        TokenValidationException("Missing scopes: $scopes", HttpStatusCode.Forbidden)
}

interface TokenValidation<TokenType> {
    // val tokenType: Class<TokenType>

    fun decodeToken(token: TokenType): SecurityPrincipalToken

    fun validate(token: String, scopes: List<SecurityScope>? = null): TokenType

    fun validateOrNull(token: String, scopes: List<SecurityScope>? = null): TokenType? {
        return try {
            validate(token, scopes)
        } catch (ex: TokenValidationException) {
            null
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

fun <T> TokenValidation<T>.validateAndDecodeOrNull(token: String): SecurityPrincipalToken? {
    return validateOrNull(token)?.let { decodeToken(it) }
}

