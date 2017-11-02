package org.esciencecloud.transactions

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.client.DefaultHttpClient
import org.jetbrains.ktor.client.readText
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.header
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

fun main(args: Array<String>) {
    val producer = GatewayProducer(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
    ))

    embeddedServer(Netty, port = 8080) {
        install(JacksonSupport)

        routing {
            route("api") {
                get("files") { proxyJobTo(resolveStorageService()) }
                get("acl") { proxyJobTo(resolveStorageService()) }
                get("users") { proxyJobTo(resolveStorageService()) }
                get("groups") { proxyJobTo(resolveStorageService()) }


                route("users") {
                    val userEventWriter = UserEvent.buildRequestWriter(defaultJsonMapper)
                    val keyWriter = defaultJsonMapper.writer()

                    post {
                        call.respond(produceKafkaRequestFromREST(producer, UserProcessor.UserEvents, keyWriter, userEventWriter)
                        { _, request: UserEvent.Create -> Pair(request.username, request) })
                    }

                    put {
                        call.respond(produceKafkaRequestFromREST(producer, UserProcessor.UserEvents, keyWriter, userEventWriter)
                        { _, request: UserEvent.Modify -> Pair(request.currentUsername, request) })
                    }
                }
            }
        }
    }.start(wait = true)

    producer.close()
}

val log = LoggerFactory.getLogger("GateWayServer")

inline suspend fun <reified RestType : Any, K : Any, R : Any> PipelineContext<Unit>.produceKafkaRequestFromREST(
        producer: GatewayProducer,
        stream: RequestResponseStream<K, R>,
        keyWriter: ObjectWriter,
        payloadWriter: ObjectWriter,
        mapper: (RequestHeader, RestType) -> Pair<K, R>
): GatewayJobResponse {
    val header = validateRequestAndPrepareJobHeader(respond = false) ?:
            return GatewayJobResponse("null", JobStatus.ERROR, null)

    val request = try {
        call.receive<RestType>() // tryReceive does not work well enough for this
    } catch (ex: Exception) {
        log.info("Caught exception while trying to deserialize user-input. Assuming that user input was malformed:")
        log.info(ex.printStacktraceToString())

        call.response.status(HttpStatusCode.BadRequest)
        return GatewayJobResponse(header.uuid, JobStatus.ERROR, null)
    }

    val kafkaRequest = try {
        mapper(header, request)
    } catch (ex: Exception) {
        log.warn("Exception when mapping REST request to Kafka request!")
        log.warn(ex.printStacktraceToString())

        call.response.status(HttpStatusCode.InternalServerError)
        return GatewayJobResponse(header.uuid, JobStatus.ERROR, null)
    }

    val record = try {
        producer.sendJob(stream, header, kafkaRequest.first, kafkaRequest.second, keyWriter, payloadWriter)
    } catch (ex: Exception) {
        log.warn("Kafka producer threw an exception while sending request!")
        log.warn(ex.printStacktraceToString())

        call.response.status(HttpStatusCode.InternalServerError)
        return GatewayJobResponse(header.uuid, JobStatus.ERROR, null)
    }

    return GatewayJobResponse(header.uuid, JobStatus.STARTED, record)
}

fun Exception.printStacktraceToString() = StringWriter().apply { printStackTrace(PrintWriter(this)) }.toString()

suspend fun <K : Any, R : Any> GatewayProducer.sendJob(
        stream: RequestResponseStream<K, R>,
        header: RequestHeader,
        key: K,
        request: R,
        keyWriter: ObjectWriter = defaultJsonMapper.writer(),
        payloadWriter: ObjectWriter = defaultJsonMapper.writer()
) = sendRequest(stream, key, Request(header, request), keyWriter, payloadWriter)

val defaultJsonMapper = jacksonObjectMapper()
suspend fun <K : Any, R : Any> GatewayProducer.sendRequest(
        stream: RequestResponseStream<K, R>,
        key: K,
        payload: Request<R>,
        keyWriter: ObjectWriter = defaultJsonMapper.writer(),
        payloadWriter: ObjectWriter = defaultJsonMapper.writer()
) = suspendCoroutine<RecordMetadata> { cont ->
    val record = ProducerRecord(
            stream.requestStream.name,
            keyWriter.writeValueAsString(key),
            payloadWriter.writeValueAsString(payload)
    )

    send(record) { metadata, exception ->
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
    val auth = with(header.performedFor) {
        Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }

    val proxyResponse = DefaultHttpClient.request(endpointUrl) {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        header(HttpHeaders.Authorization, "Basic $auth")
        header("Job-Id", header.uuid)
        method = proxyMethod
    }

    // TODO We need more general proxying here
    val proxyType = proxyResponse.headers[HttpHeaders.ContentType]
    if (proxyType != null) {
        call.response.header(HttpHeaders.ContentType, proxyType)
    }

    call.response.status(proxyResponse.status)
    call.respondText(proxyResponse.readText())
}

private fun resolveStorageService() = URL("http://localhost:42100") // TODO Do something slightly better ;-)

suspend fun PipelineContext<Unit>.validateRequestAndPrepareJobHeader(respond: Boolean = true): RequestHeader? {
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

// -------------------------------------------
// These should be exported to clients
// -------------------------------------------

enum class JobStatus {
    STARTED,
    COMPLETE,
    ERROR
}

class GatewayJobResponse(val jobId: String, val status: JobStatus, metadata: RecordMetadata?) {
    val offset = metadata?.offset()
    val partition = metadata?.partition()
    val timestamp = metadata?.timestamp()
}

// -------------------------------------------
// Currently copied from processor directly
// This should be changed later
// -------------------------------------------

data class Request<out EventType>(val header: RequestHeader, val event: EventType) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

// This will change over time. Should use a token instead of a straight password. We won't need the username at that
// point, since we could retrieve this from the auth service instead.
data class ProxyClient(val username: String, val password: String)

class Response<out InputType : Any>(
        val successful: Boolean,
        val errorMessage: String?,
        val input: Request<InputType>
)

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = UserEvent.Create::class, name = "create"),
        JsonSubTypes.Type(value = UserEvent.Modify::class, name = "modify"),
        JsonSubTypes.Type(value = UserEvent.Delete::class, name = "delete"))
sealed class UserEvent {
    data class Create(
            val username: String,
            val password: String?,
            val userType: UserType // <-- Shared type that lives inside storage interface
    ) : UserEvent()

    data class Modify(
            val currentUsername: String,
            val newPassword: String?,
            val newUserType: UserType?
    ) : UserEvent()

    data class Delete(val username: String) : UserEvent()

    companion object {
        fun buildRequestWriter(objectMapper: ObjectMapper) =
                objectMapper.writerFor(jacksonTypeRef<Request<UserEvent>>())
    }
}

object UserProcessor {
    // TODO Having auth be validated in the processor probably makes it quite a bit harder to use event-sourcing.
    // The tokens would have validate during a replay. Food for thought...
    // Maybe not, we might be able to just use the response topic? We really do need to look into this though.
    //
    // It would also make a lot of sense if these events were sent on the same topic, but with different payloads.
    // This also appears to be how most people describe event-sourcing. Using the primary key (i.e. user) would also
    // significantly help the ordering of things.
    val UserEvents = RequestResponseStream.create<String, UserEvent>("users")
}

// Potential candidates for inclusion in kafka-common if they prove useful
const val REQUEST = "request"
const val RESPONSE = "response"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<KeyType : Any, RequestType : Any>(
        topicName: String,
        keySerde: Serde<KeyType>,
        requestSerde: Serde<Request<RequestType>>,
        responseSerde: Serde<Response<RequestType>>
) {
    val requestStream = StreamDescription(
            "$REQUEST.$topicName",
            keySerde,
            requestSerde
    )

    val responseStream = StreamDescription(
            "$RESPONSE.$topicName",
            Serdes.String(),
            responseSerde
    )

    companion object {
        inline fun <reified KeyType : Any, reified RequestType : Any> create(
                topicName: String
        ): RequestResponseStream<KeyType, RequestType> {
            return RequestResponseStream(topicName, jsonSerde(), jsonSerde(), jsonSerde())
        }
    }
}

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}
