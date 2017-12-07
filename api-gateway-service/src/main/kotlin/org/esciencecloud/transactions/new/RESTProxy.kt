package org.esciencecloud.transactions.new

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.routing.route
import org.esciencecloud.client.HttpClient
import org.esciencecloud.client.addBasicAuth
import org.esciencecloud.service.ProxyClient
import org.esciencecloud.service.RequestHeader
import java.net.ConnectException
import java.net.URL
import java.util.*

class RESTProxy(val targets: List<ServiceDefinition>) {
    fun configure(route: Route): Unit = with(route) {
        targets.forEach { service ->
            service.restDescriptions.flatMap { it.descriptions }.filter { !it.shouldProxyFromGateway }.forEach {
                route(it.template) {
                    method(HttpMethod.parse(it.method.name())) {
                        handle {
                            call.proxyJobTo(findService(service))
                        }
                    }
                }
            }
        }
    }

    private fun findService(service: ServiceDefinition): URL {
        TODO()
    }

    private suspend fun ApplicationCall.proxyJobTo(
            host: URL,
            endpoint: String = request.path(),
            includeQueryString: Boolean = true,
            proxyMethod: HttpMethod = request.httpMethod
    ) {
        val queryString = if (includeQueryString) '?' + request.queryString() else ""
        val endpointUrl = URL(host, endpoint + queryString)

        val header = validateRequestAndPrepareJobHeader() ?: return

        // TODO This will always collect the entire thing in memory
        // TODO We also currently assume this to be text
        val resp = try {
            HttpClient.get(endpointUrl.toString()) {
                addBasicAuth(header.performedFor.username, header.performedFor.password)
                setHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setHeader("Job-Id", header.uuid)

                setMethod(proxyMethod.value)
            }
        } catch (ex: ConnectException) {
            respond(HttpStatusCode.GatewayTimeout)
            return
        }

        val contentType = resp.contentType?.let { ContentType.parse(it) } ?: ContentType.Text.Plain
        respondText(resp.responseBody, contentType, HttpStatusCode.fromValue(resp.statusCode))
    }
}

suspend fun ApplicationCall.validateRequestAndPrepareJobHeader(respond: Boolean = true): RequestHeader? {
    // TODO This probably shouldn't do a response for us
    val jobId = UUID.randomUUID().toString()
    val (username, password) = request.basicAuth() ?: return run {
        if (respond) respond(HttpStatusCode.Unauthorized)
        else response.status(HttpStatusCode.Unauthorized)

        null
    }
    return RequestHeader(jobId, ProxyClient(username, password))
}

fun ApplicationRequest.basicAuth(): Pair<String, String>? {
    val auth = authorization() ?: return null
    if (!auth.startsWith("Basic ")) return null
    val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
    if (decoded.indexOf(':') == -1) return null

    return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
}
