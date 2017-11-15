package org.esciencecloud.storage.processor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.authorization
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.util.AttributeKey
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.model.ProxyClient
import org.esciencecloud.storage.model.RequestHeader
import org.esciencecloud.storage.model.StoragePath
import org.esciencecloud.storage.processor.tus.TusController
import java.util.*

class StorageRestServer(private val configuration: Configuration, private val storageService: StorageService) {
    companion object {
        val StorageSession = AttributeKey<StorageConnection>("StorageSession")
    }

    fun create() = embeddedServer(CIO, port = configuration.service.port) {
        install(Compression)
        install(DefaultHeaders)
        install(CallLogging)
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        intercept(ApplicationCallPipeline.Infrastructure) {
            val (username, password) = call.request.basicAuth() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@intercept
            }

            val uuid = call.request.headers["Job-Id"] ?: run {
                call.respond(HttpStatusCode.BadRequest) // TODO
                return@intercept
            }

            val header = RequestHeader(uuid, ProxyClient(username, password))
            val connection = storageService.validateRequest(header).capture() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
            call.attributes.put(StorageSession, connection)
        }

        routing {
            val tusConfig = configuration.tus
            if (tusConfig != null) {
                val tusController = TusController(tusConfig)
                tusController.registerTusEndpoint(this, "/api/tus")
            }

            route("api") {
                get("files") {
                    val connection = call.attributes[StorageSession]
                    val path = queryParamOrBad("path") ?: return@get
                    produceResult(call, connection.fileQuery.listAt(connection.parsePath(path)))
                }

                get("acl") {
                    val connection = call.attributes[StorageSession]
                    val path = queryParamOrBad("path") ?: return@get
                    produceResult(call, connection.accessControl.listAt(connection.parsePath(path)))
                }

                get("users") {
                    val connection = call.attributes[StorageSession]
                    val username = queryParamOrBad("username") ?: return@get
                    val admin = connection.userAdmin ?: run {
                        call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                        return@get
                    }
                    produceResult(call, admin.findByUsername(username))
                }

                get("groups") {
                    // TODO This might not be exception free
                    val connection = call.attributes[StorageSession]
                    val groupName = queryParamOrBad("groupname") ?: return@get
                    produceResult(call, connection.groups.listGroupMembers(groupName))
                }

                post("temp-auth") {
                    call.attributes[StorageSession]
                    call.respondText("OK")
                }
            }
        }
    }

    suspend fun <T : Any> produceResult(call: ApplicationCall, result: Result<T>) {
        when (result) {
            is Ok<T> -> {
                call.respond(result.result)
            }

            is Error<T> -> {
                call.respondText(result.message, status = HttpStatusCode.BadRequest)
            }
        }
    }

    private fun StorageConnection.parsePath(pathFromRequest: String): StoragePath =
            paths.parseAbsolute(pathFromRequest, addHost = true)

    private suspend fun PipelineContext<Unit, ApplicationCall>.queryParamOrBad(key: String): String? {
        return call.request.queryParameters[key] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            null
        }
    }

    private fun PipelineContext<Unit, ApplicationCall>.queryParamAsBoolean(key: String): Boolean? {
        val param = call.request.queryParameters[key] ?: return null
        if (param.isEmpty()) return true
        return param.toBoolean()
    }

    private fun ApplicationRequest.basicAuth(): Pair<String, String>? {
        val auth = authorization() ?: return null
        if (!auth.startsWith("Basic ")) return null
        val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
        if (decoded.indexOf(':') == -1) return null

        return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
    }
}
