package dk.sdu.cloud.calls.server

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.call
import io.ktor.http.HttpStatusCode

class AuthInterceptor(private val tokenValidator: TokenValidation<Any>) {
    fun register(server: RpcServer) {
        server.attachFilter(object : IngoingCallFilter.BeforeParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true

            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
                val auth = call.authDescription

                val tokenMustValidate = Role.GUEST !in auth.roles
                val bearer = readAuthenticationToken(context)

                if (bearer == null && tokenMustValidate) {
                    log.debug("Missing bearer token (required)")
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }

                if (bearer != null) {
                    val validatedToken = tokenValidator.validateOrNull(bearer)

                    if (validatedToken == null && tokenMustValidate) {
                        log.debug("Invalid bearer token (required)")
                        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                    } else if (validatedToken != null) {
                        val token = tokenValidator.decodeToken(validatedToken)
                        context.securityToken = token
                        context.bearer = bearer

                        if (context.securityPrincipal.role !in auth.roles) {
                            log.debug("Security principal is not authorized for this call")
                            log.debug("Principal is: ${context.securityPrincipal}")
                            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                        }

                        token.requireScope(call.requiredAuthScope)
                    }
                }
            }
        })
    }

    private fun readAuthenticationToken(context: IngoingCall): String? {
        return when (context) {
            is HttpCall -> context.call.request.bearer
            is WSCall -> context.frameNode["bearer"].takeIf { !it.isNull && it.isTextual }?.textValue()
            else -> {
                log.warn("Unable to perform authentication check in call context: $context")
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        internal val tokenKey = AttributeKey<SecurityPrincipalToken>("security-principal-token")
        internal val bearerKey = AttributeKey<String>("bearer-token")
    }
}

var IngoingCall.securityToken: SecurityPrincipalToken
    get() = attributes[AuthInterceptor.tokenKey]
    internal set(value) {
        attributes[AuthInterceptor.tokenKey] = value
    }

val IngoingCall.securityPrincipal: SecurityPrincipal
    get() = securityToken.principal

var IngoingCall.bearer: String?
    get() = attributes.getOrNull(AuthInterceptor.bearerKey)
    internal set(value) {
        if (value != null) attributes[AuthInterceptor.bearerKey] = value
        else attributes.remove(AuthInterceptor.bearerKey)
    }

val CallDescription<*, *, *>.requiredAuthScope: SecurityScope
    get() = authDescription.requiredScope
