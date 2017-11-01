package org.esciencecloud.transactions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

fun main(args: Array<String>) {
    val producer = KafkaProducer<String, JsonNode>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.connect.json.JsonSerializer"
    ))

    embeddedServer(Netty, port = 8080) {
        install(JacksonSupport)

        routing {
            route("api") {
                route("files") {
                    get { proxyJobTo(resolveStorageService()) }
                }

                route("users") {
                    post {
                        call.respond(produceKafkaRequestFromREST(producer, UserGroupsProcessor.CreateUser)
                        { header, request: RestCreateUserRequest ->
                            CreateUserRequest(header, request.username, request.password, request.userType)
                        })
                    }

                    put {
                        call.respond(produceKafkaRequestFromREST(producer, UserGroupsProcessor.ModifyUser)
                        { header, request: RestModifyUser ->
                            ModifyUserRequest(header, request.currentUsername, request.newPassword, request.newUserType)
                        })
                    }
                }
            }
        }
    }.start(wait = true)

    producer.close()
}

val log = LoggerFactory.getLogger("GateWayServer")

inline suspend fun <reified RestType : Any, KafkaRequestType : Any> PipelineContext<Unit>.produceKafkaRequestFromREST(
        producer: KafkaProducer<String, JsonNode>,
        stream: RequestResponseStream<KafkaRequestType>,
        mapper: (RequestHeader, RestType) -> KafkaRequestType
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
        producer.sendJob(stream, header, kafkaRequest)
    } catch (ex: Exception) {
        log.warn("Kafka producer threw an exception while sending request!")
        log.warn(ex.printStacktraceToString())

        call.response.status(HttpStatusCode.InternalServerError)
        return GatewayJobResponse(header.uuid, JobStatus.ERROR, null)
    }

    return GatewayJobResponse(header.uuid, JobStatus.STARTED, record)
}

fun Exception.printStacktraceToString() = StringWriter().apply { printStackTrace(PrintWriter(this)) }.toString()

suspend fun <T : Any> KafkaProducer<String, JsonNode>.sendJob(
        stream: RequestResponseStream<T>,
        header: RequestHeader,
        request: T,
        mapper: ObjectMapper = defaultJsonMapper
) = sendRequest(stream, header.uuid, request, mapper)

val defaultJsonMapper = jacksonObjectMapper()
suspend fun <T : Any> KafkaProducer<String, JsonNode>.sendRequest(
        stream: RequestResponseStream<T>,
        key: String,
        payload: T,
        mapper: ObjectMapper = defaultJsonMapper
) = suspendCoroutine<RecordMetadata> { cont ->
    send(ProducerRecord(stream.requestStream.name, key, mapper.valueToTree(payload))) { metadata, exception ->
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

data class RestCreateUserRequest(
        val username: String,
        val password: String?,
        val userType: UserType
)

data class RestModifyUser(
        val currentUsername: String,
        val newPassword: String?,
        val newUserType: UserType?
)

// -------------------------------------------
// Currently copied from processor directly
// This should be changed later
// -------------------------------------------

interface StorageRequest {
    val header: RequestHeader
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

// This will chnage over time. Should use a token instead of a straight password. We won't need the username at that
// point, since we could retrieve this from the auth service instead.
data class ProxyClient(val username: String, val password: String)


data class CreateUserRequest(
        override val header: RequestHeader,

        val username: String,
        val password: String?,
        val userType: UserType // <-- Shared type that lives inside storage interface
) : StorageRequest

data class ModifyUserRequest(
        override val header: RequestHeader,

        val currentUsername: String,
        val newPassword: String?,
        val newUserType: UserType?
) : StorageRequest

class StorageResponse<out InputType : Any>(
        val successful: Boolean,
        val errorMessage: String?,
        val input: InputType
)

object StorageProcessor {
    val PREFIX = "storage"
}

object UserGroupsProcessor {
    val PREFIX = "${StorageProcessor.PREFIX}.ugs"

    val CreateUser = RequestResponseStream.create<CreateUserRequest>("$PREFIX.create_user")
    val ModifyUser = RequestResponseStream.create<ModifyUserRequest>("$PREFIX.modify_user")
    val Bomb = RequestResponseStream.create<Unit>("$PREFIX.BOMB9392") // TODO FIXME REMOVE THIS LATER
}

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}

// Potential candidates for inclusion in kafka-common if they prove useful
const val REQUEST = "request"
const val RESPONSE = "response"
const val POLICY = "policy"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<RequestType : Any>(
        topicName: String,
        requestSerde: Serde<RequestType>,
        responseSerde: Serde<StorageResponse<RequestType>>
) {
    val requestStream = StreamDescription(
            "$REQUEST.$topicName",
            Serdes.String(),
            requestSerde
    )

    val responseStream = StreamDescription(
            "$RESPONSE.$topicName",
            Serdes.String(),
            responseSerde
    )

    companion object {
        inline fun <reified RequestType : Any> create(topicName: String): RequestResponseStream<RequestType> {
            return RequestResponseStream(topicName, jsonSerde(), jsonSerde())
        }
    }
}

