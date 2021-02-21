package dk.sdu.cloud.calls.server

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.call
import io.ktor.http.HttpStatusCode

class AuthInterceptor(
    private val tokenValidator: TokenValidation<Any>,
    private val developmentModeEnabled: Boolean,
) {
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
                            val allowedInDevMode =
                                // Allow privileged roles to act as a provider in dev mode
                                (auth.roles == Roles.PROVIDER &&
                                    context.securityPrincipal.role in Roles.PRIVILEGED)

                            if (!developmentModeEnabled || !allowedInDevMode) {
                                log.debug("Security principal is not authorized for this call: $call")
                                log.debug("Principal is: ${context.securityPrincipal}")
                                log.debug("Principal should be in ${auth.roles} but has role" +
                                    " ${context.securityPrincipal.role}")
                                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                            }
                        }

                        token.requireScope(call.requiredAuthScope)
                    }
                }
            }
        })
    }

    private fun readAuthenticationToken(context: IngoingCall): String? {
        return when (context) {
            is HttpCall -> @Suppress("DEPRECATION") context.call.request.bearer
            is WSCall -> context.frameNode["bearer"]?.takeIf { !it.isNull && it.isTextual }?.textValue()
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

val IngoingCall.securityTokenOrNull: SecurityPrincipalToken?
    get() = attributes.getOrNull(AuthInterceptor.tokenKey)

var IngoingCall.securityToken: SecurityPrincipalToken
    get() = attributes.getOrNull(AuthInterceptor.tokenKey) ?: error("User is not logged in")
    internal set(value) {
        attributes[AuthInterceptor.tokenKey] = value
    }

val IngoingCall.securityPrincipalOrNull: SecurityPrincipal?
    get() = securityTokenOrNull?.principal

val IngoingCall.securityPrincipal: SecurityPrincipal
    get() = securityToken.principal

var IngoingCall.bearer: String?
    get() = attributes.getOrNull(AuthInterceptor.bearerKey)
    internal set(value) {
        if (value != null) {
            attributes[AuthInterceptor.bearerKey] = value
        } else {
            attributes.remove(AuthInterceptor.bearerKey)
        }
    }

val CallDescription<*, *, *>.requiredAuthScope: SecurityScope
    get() = authDescription.requiredScope
