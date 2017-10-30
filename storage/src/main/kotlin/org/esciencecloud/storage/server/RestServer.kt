package org.esciencecloud.storage.server

import org.esciencecloud.kafka.queryParamOrBad
import org.esciencecloud.storage.StorageConnection
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.ApplicationRequest
import org.jetbrains.ktor.request.authorization
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.route
import org.jetbrains.ktor.routing.routing
import java.util.*

data class RestRequest(override val header: RequestHeader) : StorageRequest

class StorageRestServer(val port: Int, private val storageService: StorageService) {
    fun create() = embeddedServer(Netty, port = port) {
        install(GsonSupport)
        install(Compression)
        install(DefaultHeaders)

        routing {
            route("/api/") {
                get("/files/") {
                    val (_, connection) = parseStorageRequestAndValidate() ?: return@get
                    val path = queryParamOrBad("path") ?: return@get
                    val acls = queryParamAsBoolean("acl") ?: true
                    val metadata = queryParamAsBoolean("metadata") ?: false

                    try {
                        call.respond(connection.fileQuery.listAt(
                                path = connection.paths.parseAbsolute(path, addHost = true),
                                preloadACLs = acls,
                                preloadMetadata = metadata
                        ))
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        //TODO("Change interface such that we don't throw exceptions we need to handle here")
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }

    private fun PipelineContext<Unit>.queryParamAsBoolean(key: String): Boolean? {
        val param = call.request.queryParameters[key] ?: return null
        if (param.isEmpty()) return true
        return param.toBoolean()
    }

    private suspend fun PipelineContext<Unit>.parseStorageRequestAndValidate():
            Pair<StorageRequest, StorageConnection>? {
        val (username, password) = call.request.basicAuth() ?: return run {
            call.respond(HttpStatusCode.Unauthorized)
            null
        }

        val uuid = call.request.headers["Job-Id"] ?: return run {
            call.respond(HttpStatusCode.BadRequest) // TODO
            null
        }

        val request = RestRequest(RequestHeader(uuid, ProxyClient(username, password)))

        val connection = storageService.validateRequest(request).capture() ?: return run {
            call.respond(HttpStatusCode.Unauthorized)
            null
        }

        return Pair(request, connection)
    }

    private fun ApplicationRequest.basicAuth(): Pair<String, String>? {
        val auth = authorization() ?: return null
        if (!auth.startsWith("Basic ")) return null
        val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
        if (decoded.indexOf(':') == -1) return null

        return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
    }
}
