package org.esciencecloud.transactions

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth
import org.esciencecloud.storage.model.*
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.*
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
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

        embeddedServer(Netty, port = configuration.gateway.port) {
            install(JacksonSupport)

            routing {
                route("api") {
                    get("files") { proxyJobTo(storageService) }
                    get("acl") { proxyJobTo(storageService) }
                    get("users") { proxyJobTo(storageService) }
                    get("groups") { proxyJobTo(storageService) }
                    post("temp-auth") { proxyJobTo(storageService) }


                    route("users") {
                        post {
                            call.respond(produceKafkaRequestFromREST(producer, UserProcessor.UserEvents)
                            { _, request: UserEvent.Create -> Pair(request.username, request) })
                        }

                        put {
                            call.respond(produceKafkaRequestFromREST(producer, UserProcessor.UserEvents)
                            { _, request: UserEvent.Modify -> Pair(request.currentUsername, request) })
                        }
                    }
                }
            }
        }.start(wait = true)

        producer.close()
    }

    private inline suspend fun <reified RestType : Any, K : Any, R : Any> PipelineContext<Unit>.produceKafkaRequestFromREST(
            producer: GatewayProducer,
            stream: RequestResponseStream<K, R>,
            mapper: (RequestHeader, RestType) -> Pair<K, R>
    ): GatewayJobResponse {
        val header = validateRequestAndPrepareJobHeader(respond = false) ?:
                return GatewayJobResponse.error()

        val request = try {
            call.receive<RestType>() // tryReceive does not work well enough for this
        } catch (ex: Exception) {
            log.info("Caught exception while trying to deserialize user-input. Assuming that user input was malformed:")
            log.info(ex.printStacktraceToString())

            call.response.status(HttpStatusCode.BadRequest)
            return GatewayJobResponse.error()
        }

        val kafkaRequest = try {
            mapper(header, request)
        } catch (ex: Exception) {
            log.warn("Exception when mapping REST request to Kafka request!")
            log.warn(ex.printStacktraceToString())

            call.response.status(HttpStatusCode.InternalServerError)
            return GatewayJobResponse.error()
        }

        val record = try {
            producer.sendRequest(stream, kafkaRequest.first, Request(header, kafkaRequest.second))
        } catch (ex: Exception) {
            log.warn("Kafka producer threw an exception while sending request!")
            log.warn(ex.printStacktraceToString())

            call.response.status(HttpStatusCode.InternalServerError)
            return GatewayJobResponse.error()
        }

        return GatewayJobResponse.started(header.uuid, record)
    }

    private fun Exception.printStacktraceToString() = StringWriter().apply { printStackTrace(PrintWriter(this)) }.toString()

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

    private suspend fun PipelineContext<Unit>.proxyJobTo(
            host: URL,
            endpoint: String = call.request.path(),
            includeQueryString: Boolean = true,
            proxyMethod: HttpMethod = call.request.httpMethod
    ) {
        val queryString = if (includeQueryString) '?' + call.request.queryString() else ""
        val endpointUrl = URL(host, endpoint + queryString)

        val header = validateRequestAndPrepareJobHeader() ?: return

        // TODO This will always collect the entire thing in memory
        // TODO We also currently assume this to be text
        val resp = HttpClient.get(endpointUrl.toString()) {
            addBasicAuth(header.performedFor.username, header.performedFor.password)
            setHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setHeader("Job-Id", header.uuid)

            setMethod(proxyMethod.value)
        }

        call.respondText(resp.responseBody, ContentType.parse(resp.contentType),
                HttpStatusCode.fromValue(resp.statusCode))
    }

    private suspend fun PipelineContext<Unit>.validateRequestAndPrepareJobHeader(respond: Boolean = true): RequestHeader? {
        // TODO This probably shouldn't do a response for us
        val jobId = UUID.randomUUID().toString()
        val (username, password) = call.request.basicAuth() ?: return run {
            if (respond) call.respond(HttpStatusCode.Unauthorized)
            else call.response.status(HttpStatusCode.Unauthorized)

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