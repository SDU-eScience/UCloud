package dk.sdu.cloud.http

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.NativeJWTValidation
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.service.Log
import h2o.H2O_TOKEN_AUTHORIZATION
import h2o.h2o_find_header
import io.ktor.http.*
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import platform.posix.SIZE_MAX

fun loadMiddleware(config: IMConfiguration, validation: NativeJWTValidation): Unit = with(config) {
    addMiddleware(object : Middleware {
        override fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
            when (val ctx = handler.ctx.serverContext) {
                is HttpContext -> {
                    val req = ctx.reqPtr.pointed
                    val res = h2o_find_header(req.headers.ptr, H2O_TOKEN_AUTHORIZATION, SIZE_MAX.toLong())
                    if (res != SIZE_MAX.toLong()) {
                        val header = req.headers.entries?.get(res)?.value
                        val authorizationHeader = header?.base?.readBytes(header.len.toInt())?.decodeToString()
                        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return
                        handler.ctx.bearerOrNull = authorizationHeader.removePrefix("Bearer ")
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
