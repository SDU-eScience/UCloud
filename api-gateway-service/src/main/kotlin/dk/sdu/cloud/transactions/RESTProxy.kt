package dk.sdu.cloud.transactions

import dk.sdu.cloud.service.RequestHeader
import dk.sdu.cloud.service.listServicesWithStatus
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
import org.apache.zookeeper.ZooKeeper
import org.esciencecloud.client.HttpClient
import dk.sdu.cloud.transactions.util.stackTraceToString
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.URL
import java.util.*

sealed class RESTProxyException(message: String, val code: HttpStatusCode) : Exception(message)
class RESTNoServiceAvailable : RESTProxyException("Gateway timeout", HttpStatusCode.GatewayTimeout)

class RESTProxy(val targets: List<ServiceDefinition>, val zk: ZooKeeper) {
    private val random = Random()
    companion object {
        private val log = LoggerFactory.getLogger(RESTProxy::class.java)
    }

    fun configure(route: Route): Unit = with(route) {
        targets.forEach { service ->
            service.restDescriptions.flatMap { it.descriptions }.filter { it.shouldProxyFromGateway }.forEach {
                route(it.template) {
                    method(HttpMethod.parse(it.method.name())) {
                        handle {
                            try {
                                call.proxyJobTo(findService(service))
                            } catch (ex: RESTProxyException) {
                                when (ex) {
                                    is RESTNoServiceAvailable -> {
                                        log.warn("Unable to proxy request to target service. Unable to find " +
                                                "any running service!")
                                        log.warn("Service is: ${service.manifest}")
                                    }
                                }
                                call.respond(ex.code)
                            } catch (ex: Exception) {
                                log.warn("Caught unexpected exception while proxying")
                                log.warn(ex.stackTraceToString())
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun findService(service: ServiceDefinition): URL {
        val services = with (service.manifest) {
            zk.listServicesWithStatus(name, version).values.firstOrNull()
        }?.takeIf { it.isNotEmpty() } ?: throw RESTNoServiceAvailable()

        // TODO FIXME proxying using https
        val resolvedService = services[random.nextInt(services.size)]
        return URL("http://${resolvedService.instance.hostname}:${resolvedService.instance.port}")
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
                setHeader(HttpHeaders.Authorization, "Bearer ${header.performedFor}")
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
    val token = request.bearer() ?: return run {
        if (respond) respond(HttpStatusCode.Unauthorized)
        else response.status(HttpStatusCode.Unauthorized)

        null
    }
    return RequestHeader(jobId, token)
}

fun ApplicationRequest.bearer(): String? {
    val auth = authorization() ?: return null
    if (!auth.startsWith("Bearer ")) return null

    return auth.substringAfter("Bearer ")
}
