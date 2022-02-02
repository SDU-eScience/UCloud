package dk.sdu.cloud.service

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.TokenValidationException

class TokenValidationChain(
    val chain: List<TokenValidation<*>>
) : TokenValidation<Any> {
    override fun canHandleToken(token: Any?): Boolean {
        return chain.any { it.canHandleToken(token) }
    }

    override fun decodeToken(token: Any): SecurityPrincipalToken {
        for (validator in chain) {
            @Suppress("UNCHECKED_CAST")
            validator as TokenValidation<Any>

            if (validator.canHandleToken(token)) {
                return validator.decodeToken(token)
            }
        }
        throw TokenValidationException.Invalid()
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): Any {
        for (validator in chain) {
            val validated = validator.validateOrNull(token, scopes)
            if (validated != null) {
                return validated
            }
        }
        throw TokenValidationException.Invalid()
    }
}
