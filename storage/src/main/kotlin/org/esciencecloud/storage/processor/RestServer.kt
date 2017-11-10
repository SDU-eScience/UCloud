package org.esciencecloud.storage.processor

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.authorization
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.model.ProxyClient
import org.esciencecloud.storage.model.StoragePath
import org.esciencecloud.storage.model.RequestHeader
import org.esciencecloud.storage.processor.tus.TusController
import org.esciencecloud.storage.Result
import java.util.*


class StorageRestServer(val port: Int, private val storageService: StorageService) {
    fun create() = embeddedServer(CIO, port = port) {
        //install(GsonSupport)
        install(Compression)
        install(DefaultHeaders)
        install(CallLogging)

        routing {
            // TODO We need a common way of handling results here
            // TODO We need a format for errors. Should this always be included in the message? Would simplify
            // client code.
            route("/api/") {
                val tusController = TusController()
                tusController.registerTusEndpoint(this, "tus")

                route("files") {
                    get {
                        val (_, connection) = parseStorageRequestAndValidate() ?: return@get
                        val path = queryParamOrBad("path") ?: return@get

                        try {
                            val message = connection.fileQuery.listAt(
                                    path = connection.parsePath(path)
                            ).capture() ?: run {
                                val error = Result.lastError<Any>()
                                call.respondText(error.message, status = HttpStatusCode.BadRequest)
                                return@get
                            }

                            call.respond(message)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            //TODO("Change interface such that we don't throw exceptions we need to handle here")
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }

                route("acl") {
                    get {
                        val (_, connection) = parseStorageRequestAndValidate() ?: return@get
                        val path = queryParamOrBad("path") ?: return@get
                        val message = connection.accessControl.listAt(connection.parsePath(path)).capture() ?: run {
                            val error = Result.lastError<Any>()
                            call.respondText(error.message, status = HttpStatusCode.BadRequest)
                            return@get
                        }

                        call.respond(message)
                    }
                }

                route("users") {
                    get {
                        val (_, connection) = parseStorageRequestAndValidate() ?: return@get
                        val userAdmin = connection.userAdmin ?: run {
                            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                            return@get
                        }

                        val username = queryParamOrBad("username") ?: return@get
                        val result = userAdmin.findByUsername(username).capture() ?: run {
                            val error = Result.lastError<Any>()
                            call.respondText(error.message, status = HttpStatusCode.BadRequest)
                            return@get
                        }

                        call.respond(result)
                    }
                }

                route("groups") {
                    get {
                        // TODO This might not be exception free
                        val (_, connection) = parseStorageRequestAndValidate() ?: return@get
                        val groupName = queryParamOrBad("groupname") ?: return@get
                        val members = connection.groups.listGroupMembers(groupName).capture() ?: run {
                            val error = Result.lastError<Any>()
                            call.respondText(error.message, status = HttpStatusCode.BadRequest)
                            return@get
                        }

                        call.respond(members)
                    }
                }
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

    // This will almost certainly need to be applied on all routes
    // Look into how ktor allows for this (because it almost certainly does)
    private suspend fun PipelineContext<Unit, ApplicationCall>.parseStorageRequestAndValidate():
            Pair<RequestHeader, StorageConnection>? {
        val (username, password) = call.request.basicAuth() ?: return run {
            call.respond(HttpStatusCode.Unauthorized)
            null
        }

        val uuid = call.request.headers["Job-Id"] ?: return run {
            call.respond(HttpStatusCode.BadRequest) // TODO
            null
        }

        val header = RequestHeader(uuid, ProxyClient(username, password))

        val connection = storageService.validateRequest(header).capture() ?: return run {
            call.respond(HttpStatusCode.Unauthorized)
            null
        }

        return Pair(header, connection)
    }

    private fun ApplicationRequest.basicAuth(): Pair<String, String>? {
        val auth = authorization() ?: return null
        if (!auth.startsWith("Basic ")) return null
        val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
        if (decoded.indexOf(':') == -1) return null

        return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
    }
}
