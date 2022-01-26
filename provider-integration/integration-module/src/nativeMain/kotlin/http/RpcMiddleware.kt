package dk.sdu.cloud.http

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.NativeJWTValidation
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.service.Log

fun loadMiddleware(config: IMConfiguration, validation: NativeJWTValidation): Unit = with(config) {
    addMiddleware(object : Middleware {
        override fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
            when (val ctx = handler.ctx.serverContext) {
                is HttpContext -> {
                    val authHeader = ctx.headers.find { it.header.equals("Authorization", ignoreCase = true) }
                    if (authHeader != null && authHeader.value.startsWith("Bearer ")) {
                        handler.ctx.bearerOrNull = authHeader.value.removePrefix("Bearer ")
                    }
                }

                is WebSocketContext<*, *, *> -> {
                    handler.ctx.bearerOrNull = ctx.rawRequest.bearer
                }
            }
        }
    })

    addMiddleware(object : Middleware {
        val log = Log("AuthMiddleware")

        override fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
            val bearer = handler.ctx.bearerOrNull
            val token = if (bearer != null) {
                val token = validation.validateOrNull(bearer)
                handler.ctx.securityPrincipalTokenOrNull = token
                token
            } else {
                null
            }

            val principal = token?.principal

            val auth = handler.description.authDescription
            if (auth.roles != Roles.PUBLIC && principal == null) {
                log.debug("Principal was null")
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            } else if (principal != null && principal.role !in auth.roles) {
                log.debug("Role is not authorized ${principal}")
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }
        }
    })

    return@with
}
