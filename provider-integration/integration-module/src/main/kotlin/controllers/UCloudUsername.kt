package dk.sdu.cloud.controllers

import dk.sdu.cloud.base64Decode
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.provider.api.IntegrationProvider
import io.ktor.server.application.*
import io.ktor.server.request.*

val CallHandler<*, *, *>.ucloudUsername: String?
    get() {
        var username: String? = null
        withContext<HttpCall> {
            username = ctx.ktor.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
                ?.let { base64Decode(it).decodeToString() }
        }

        withContext<WSCall> {
            username = ctx.session.underlyingSession.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
                ?.let { base64Decode(it).decodeToString() }
        }
        return username
    }
