package dk.sdu.cloud.storage

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.service.RequestHeader
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.storage.api.ACLDescriptions
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.GroupDescriptions
import dk.sdu.cloud.storage.api.UserDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.model.StoragePath
import dk.sdu.cloud.storage.processor.tus.TusController
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.Compression
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.ApplicationSendPipeline
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey

class StorageRestServer(
        private val configuration: Configuration,
        private val storageService: StorageConnectionFactory
) {
    companion object {
        val StorageSession = AttributeKey<StorageConnection>("StorageSession")
        val JwtToken = AttributeKey<DecodedJWT>("JWT")
    }

    fun validateRequest(header: RequestHeader): StorageConnection? {
        val validated = TokenValidation.validateOrNull(header.performedFor) ?: return null
        return storageService.createForAccount(validated.subject, validated.token).capture()
    }

    private fun ApplicationRequest.bearer(): String? {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substringAfter("Bearer ")
    }

    fun create() = embeddedServer(Netty, port = configuration.service.port) {
        install(Compression)
        install(DefaultHeaders)
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        intercept(ApplicationCallPipeline.Infrastructure) {
            val token = call.request.bearer() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }

            val uuid = call.request.headers["Job-Id"] ?: run {
                call.respond(HttpStatusCode.BadRequest) // TODO
                finish()
                return@intercept
            }

            val header = RequestHeader(uuid, token)
            val connection = validateRequest(header) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
                return@intercept
            }
            call.attributes.put(StorageSession, connection)
            call.attributes.put(JwtToken, TokenValidation.validate(token))
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
                route("files") {
                    implement(FileDescriptions.listAtPath) {
                        val connection = call.attributes[StorageSession]
                        val listAt = connection.fileQuery.listAt(connection.parsePath(it.path))
                        when (listAt) {
                            is Ok -> ok(listAt.result)
                            is Error -> {
                                error(CommonErrorMessage(listAt.message), HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }

                route("acl") {
                    implement(ACLDescriptions.listAtPath) {
                        val connection = call.attributes[StorageSession]
                        val listAt = connection.accessControl.listAt(connection.parsePath(it.path))
                        when (listAt) {
                            is Ok -> ok(listAt.result)
                            is Error -> {
                                // TODO We have no idea with these error types
                                error(CommonErrorMessage(listAt.message), HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }

                route("users") {
                    implement(UserDescriptions.findByName) {
                        val principal = call.attributes[JwtToken]
                        if (principal.getClaim("role").asString() != "ADMIN") {
                            call.respond(HttpStatusCode.Unauthorized)
                            return@implement
                        }
                        val connection = call.attributes[StorageSession]
                        val admin = connection.userAdmin ?: return@implement run {
                            error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                        }

                        val findBy = admin.findByUsername(it.name)
                        when (findBy) {
                            is Ok -> ok(findBy.result)
                            is Error -> error(CommonErrorMessage(findBy.message), HttpStatusCode.InternalServerError)
                        }
                    }
                }

                route("groups") {
                    implement(GroupDescriptions.findByName) {
                        val principal = call.attributes[JwtToken]
                        if (principal.getClaim("role").asString() != "ADMIN") {
                            call.respond(HttpStatusCode.Unauthorized)
                            return@implement
                        }

                        val connection = call.attributes[StorageSession]
                        try {
                            val listAt = connection.groups.listGroupMembers(it.name)
                            when (listAt) {
                                is Ok -> ok(listAt.result)
                                is Error -> error(CommonErrorMessage(listAt.message),
                                        HttpStatusCode.InternalServerError)
                            }
                        } catch (ex: Exception) {
                            // TODO Not sure if listGroupMembers will throw exceptions
                           error(CommonErrorMessage("Internal error"), HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
        }
    }

   private fun StorageConnection.parsePath(pathFromRequest: String): StoragePath =
            paths.parseAbsolute(pathFromRequest, addHost = true)
}
