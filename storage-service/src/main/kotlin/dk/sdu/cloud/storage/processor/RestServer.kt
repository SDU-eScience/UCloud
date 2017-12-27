package dk.sdu.cloud.storage.processor

import com.fasterxml.jackson.module.kotlin.KotlinModule
import dk.sdu.cloud.service.RequestHeader
import dk.sdu.cloud.service.implement
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.response.ApplicationSendPipeline
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.util.AttributeKey
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.model.StoragePath
import dk.sdu.cloud.storage.processor.tus.TusController
import io.ktor.http.HttpHeaders
import io.ktor.request.header

class StorageRestServer(private val configuration: Configuration, private val storageService: StorageService) {
    companion object {
        val StorageSession = AttributeKey<StorageConnection>("StorageSession")
    }

    private fun ApplicationRequest.bearer(): String? {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (header.startsWith("Bearer ")) return null
        return header.substringAfter("Bearer ")
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
            val token = call.request.bearer() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@intercept
            }

            val uuid = call.request.headers["Job-Id"] ?: run {
                call.respond(HttpStatusCode.BadRequest) // TODO
                return@intercept
            }

            val header = RequestHeader(uuid, token)
            val connection = storageService.validateRequest(header).capture() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
            call.attributes.put(StorageSession, connection)
        }

        // TODO Not sure if this is the correct phase to use
        sendPipeline.intercept(ApplicationSendPipeline.After) {
            call.attributes.getOrNull(StorageSession)?.close()
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
                route("files") {
                    implement(FileDescriptions.listAtPath) {
                        val connection = call.attributes[StorageSession]
                        val result = connection.fileQuery.listAt(connection.parsePath(it.path)).capture()
                        val err = Result.lastError<Any>()
                        if (result != null) {
                            ok(result)
                        } else {
                            error("status" to err.message, HttpStatusCode.InternalServerError)
                        }
                    }
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

}
