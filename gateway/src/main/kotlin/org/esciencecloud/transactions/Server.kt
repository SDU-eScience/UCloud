package org.esciencecloud.transactions

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth
import org.esciencecloud.storage.model.*
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException
import java.net.URL
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

typealias GatewayProducer = KafkaProducer<String, String>
class Server(private val configuration: Configuration) {
    private val log = LoggerFactory.getLogger("GateWayServer")

    fun start() {
        val producer = GatewayProducer(mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to configuration.kafka.servers.joinToString(","),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
        ))

        // TODO This will eventually be replaced by a more robust solution
        val storageService = with(configuration.storage) { URL("http://$host:$port") }
        val hpcService = with(configuration.hpc) { URL("http://$host:$port") }

        embeddedServer(CIO, port = configuration.gateway.port) {
            install(ContentNegotiation) {
                jackson { registerKotlinModule() }
            }

            routing {
                route("api") {
                    get("files") { call.proxyJobTo(storageService) }
                    get("acl") { call.proxyJobTo(storageService) }
                    get("users") { call.proxyJobTo(storageService) }
                    get("groups") { call.proxyJobTo(storageService) }
                    get("hpc/{...}") { call.proxyJobTo(hpcService) }
                    post("temp-auth") { call.proxyJobTo(storageService) }


                    route("users") {
                        post {
                            call.respond(call.produceKafkaRequestFromREST(producer, UserProcessor.UserEvents)
                            { _, request: UserEvent.Create -> Pair(request.username, request) })
                        }

                        put {
                            call.respond(call.produceKafkaRequestFromREST(producer, UserProcessor.UserEvents)
                            { _, request: UserEvent.Modify -> Pair(request.currentUsername, request) })
                        }
                    }
                }
            }
        }.start(wait = true)

        producer.close()
    }

    private inline suspend fun
            <reified RestType : Any, K : Any, R : Any> ApplicationCall.produceKafkaRequestFromREST(
            producer: GatewayProducer,
            stream: RequestResponseStream<K, R>,
            mapper: (RequestHeader, RestType) -> Pair<K, R>
    ): GatewayJobResponse {
        val header = validateRequestAndPrepareJobHeader(respond = false) ?:
                return GatewayJobResponse.error()

        val request = try {
            receive<RestType>() // tryReceive does not work well enough for this
        } catch (ex: Exception) {
            log.info("Caught exception while trying to deserialize user-input. Assuming that user input was malformed:")
            log.info(ex.printStacktraceToString())

            response.status(HttpStatusCode.BadRequest)
            return GatewayJobResponse.error()
        }

        val kafkaRequest = try {
            mapper(header, request)
        } catch (ex: Exception) {
            log.warn("Exception when mapping REST request to Kafka request!")
            log.warn(ex.printStacktraceToString())

            response.status(HttpStatusCode.InternalServerError)
            return GatewayJobResponse.error()
        }

        val record = try {
            producer.sendRequest(stream, kafkaRequest.first, Request(header, kafkaRequest.second))
        } catch (ex: Exception) {
            log.warn("Kafka producer threw an exception while sending request!")
            log.warn(ex.printStacktraceToString())

            response.status(HttpStatusCode.InternalServerError)
            return GatewayJobResponse.error()
        }

        return GatewayJobResponse.started(header.uuid, record)
    }

    private fun Exception.printStacktraceToString() = StringWriter().apply {
        printStackTrace(PrintWriter(this))
    }.toString()

    private suspend fun <K : Any, R : Any> GatewayProducer.sendRequest(
            stream: RequestResponseStream<K, R>,
            key: K,
            payload: Request<R>
    ) = suspendCoroutine<RecordMetadata> { cont ->
        stream.produceRequest(this, key, payload) { metadata, exception ->
            if (exception != null) {
                cont.resumeWithException(exception)
            } else {
                cont.resume(metadata)
            }
        }
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

    private suspend fun ApplicationCall.validateRequestAndPrepareJobHeader(respond: Boolean = true): RequestHeader? {
        // TODO This probably shouldn't do a response for us
        val jobId = UUID.randomUUID().toString()
        val (username, password) = request.basicAuth() ?: return run {
            if (respond) respond(HttpStatusCode.Unauthorized)
            else response.status(HttpStatusCode.Unauthorized)

            null
        }
        return RequestHeader(jobId, ProxyClient(username, password))
    }

    private fun ApplicationRequest.basicAuth(): Pair<String, String>? {
        val auth = authorization() ?: return null
        if (!auth.startsWith("Basic ")) return null
        val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
        if (decoded.indexOf(':') == -1) return null

        return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
    }
}