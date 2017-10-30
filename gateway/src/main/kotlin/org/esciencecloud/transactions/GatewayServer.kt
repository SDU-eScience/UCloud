package org.esciencecloud.transactions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.client.DefaultHttpClient
import org.jetbrains.ktor.client.readText
import org.jetbrains.ktor.gson.GsonSupport
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
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.post
import org.jetbrains.ktor.routing.route
import org.jetbrains.ktor.routing.routing
import java.net.URL
import java.util.*

fun main(args: Array<String>) {
    val producer = KafkaProducer<String, JsonNode>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.connect.json.JsonSerializer"
    ))

    val mapper = jacksonObjectMapper()

    embeddedServer(Netty, port = 8080) {
        install(GsonSupport)

        routing {
            route("api") {
                route("files") {
                    get { proxyJobTo(resolveStorageService()) }
                }

                route("users") {
                    post {
                        val header = validateRequestAndPrepareJobHeader() ?: return@post
                        val request = call.receive<RestCreateUserRequest>()

                        val kafkaRequest = CreateUserRequest(
                                header,
                                request.username,
                                request.password,
                                request.userType
                        )

                        // Last part should be possible to refactor out. As long as we're given a correct request.
                        // However, this would also require a common request scheme across all services.
                        producer.send(ProducerRecord(
                                UserGroupsProcessor.CreateUser.requestStream.name,
                                header.uuid,
                                mapper.valueToTree(kafkaRequest)
                        ))

                        call.respond(GatewayJobResponse(header.uuid, JobStatus.STARTED))
                    }
                }
            }
        }
    }.start(wait = true)

    producer.close()
}

private suspend fun PipelineContext<Unit>.proxyJobTo(
        host: URL,
        endpoint: String = call.request.path(),
        includeQueryString: Boolean = true,
        proxyMethod: HttpMethod = call.request.httpMethod
) {

    val queryString = if (includeQueryString) call.request.queryString() else ""
    val endpointUrl = URL(host, "$endpoint?$queryString")

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

private suspend fun PipelineContext<Unit>.validateRequestAndPrepareJobHeader(): RequestHeader? {
    val jobId = UUID.randomUUID().toString()
    val (username, password) = call.request.basicAuth() ?: return run {
        call.respond(HttpStatusCode.Unauthorized)
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

data class GatewayJobResponse(val jobId: String, val status: JobStatus)

data class RestCreateUserRequest(
        val username: String,
        val password: String?,
        val userType: UserType
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

