package org.esciencecloud.storage

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.queryParamOrBad
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.ApplicationRequest
import org.jetbrains.ktor.request.authorization
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.routing
import java.util.*
import java.util.concurrent.TimeUnit

/*
 * This file starts the Storage server. This will start up both the REST service and the Kafka consumer.
 * It might make sense to split these, but currently it seems that it actually makes quite a bit of sense to keep
 * these together. It will also be simpler, so we should do this for now.
 */

// These artifacts should be shared with others, such that they may be used for types

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
        val userType: UserType
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

const val REQUEST = "request"
const val RESPONSE = "response"
const val POLICY = "policy"

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

    fun map(
            builder: KStreamBuilder,
            mapper: (String, RequestType) -> Pair<String, StorageResponse<RequestType>>
    ) {
        requestStream.stream(builder).map { key, value ->
            val (a, b) = mapper(key, value)
            KeyValue.pair(a, b)
        }.to(responseStream.keySerde, responseStream.valueSerde, responseStream.name)
    }

    fun mapResult(builder: KStreamBuilder, mapper: (RequestType) -> (Result<*>)) {
        return map(builder) { key, request ->
            Pair(key, mapper(request).toResponse(request))
        }
    }

    companion object {
        inline fun <reified RequestType : Any> create(topicName: String): RequestResponseStream<RequestType> {
            return RequestResponseStream(topicName, jsonSerde(), jsonSerde())
        }
    }
}

fun <Req : Any> KStream<String, StorageResponse<Req>>.toDescription(description: RequestResponseStream<Req>) {
    this.toDescription(description.responseStream)
}

fun <Key, Value> KStream<Key, Value>.toDescription(streamDescription: StreamDescription<Key, Value>) {
    this.to(streamDescription.keySerde, streamDescription.valueSerde, streamDescription.name)
}

object StorageProcessor {
    val PREFIX = "storage"
}

object UserGroupsProcessor {
    val PREFIX = "${StorageProcessor.PREFIX}.ugs"

    val CreateUser = RequestResponseStream.create<CreateUserRequest>("$PREFIX.create_user")
    val ModifyUser = RequestResponseStream.create<ModifyUserRequest>("$PREFIX.modify_user")
    val Bomb = RequestResponseStream.create<Unit>("$PREFIX.BOMB9392") // TODO FIXME REMOVE THIS LATER
}

fun retrieveConnectionFactory(): ConnectionFactory = TODO()

fun createPersistentAdminConnection(factory: ConnectionFactory) = factory.createForAccount("rods", "rods")

/**
 * Should validate the StorageRequest and provide us with an appropriate StorageConnection. For internal services this
 * should simply match an appropriate AdminConnection.
 *
 * For operations performed by an end-user this should match their internal storage user.
 *
 * If the supplied credentials are incorrect we should return an [Error]
 *
 * If any of our collaborators are unavailable we should throw an exception (after perhaps internally re-trying). This
 * will cause Kafka to _not_ commit this message as having been consumed by this sub-system. Which is good, because
 * we want to retry at a later point.
 */
fun validateRequest(request: StorageRequest): Result<Connection> = Ok(TODO())

fun <T : Any, InputType : Any> Result<T>.toResponse(input: InputType) =
        StorageResponse(this is Ok, (this as? Error)?.message, input)

fun createUser(request: CreateUserRequest): Result<Unit> {
    val connection = validateRequest(request).capture() ?: return Result.lastError()
    val userAdmin = connection.userAdmin ?: return Error(123, "Not allowed")
    return userAdmin.createUser(request.username, request.password)
}

fun modifyUser(request: ModifyUserRequest): Result<Unit> {
    val connection = validateRequest(request).capture() ?: return Result.lastError()
    return when {
        connection.userAdmin != null -> {
            val userAdmin = connection.userAdmin!!
            // We can do whatever
            if (request.newPassword != null) {
                userAdmin.modifyPassword(request.currentUsername, request.newPassword)
            }

            if (request.newUserType != null) {
                TODO("Not provided by the API")
            }

            Ok.empty()
        }

        request.currentUsername == connection.connectedUser.name -> {
            // Otherwise we need to modifying ourselves
            // And even then, we only allow certain things

            if (request.newUserType != null) return Error.permissionDenied()
            if (request.newPassword != null) {
                val currentPassword = request.header.performedFor.password // TODO This shouldn't be available directly
                connection.users.modifyMyPassword(currentPassword, request.newPassword)
            }
            Ok.empty()
        }

        else -> Error.permissionDenied()
    }
}

fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

fun main(args: Array<String>) {
    val storageFactory = retrieveConnectionFactory()
    val adminConnection = createPersistentAdminConnection(storageFactory)

    val properties = Properties()
    properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
    properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
    properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events

    val builder = KStreamBuilder()

    UserGroupsProcessor.CreateUser.mapResult(builder) { createUser(it) }
    UserGroupsProcessor.ModifyUser.mapResult(builder) { modifyUser(it) }

    // TODO FIXME THIS SHOULD BE REMOVED LATER
    // TODO FIXME THIS SHOULD BE REMOVED LATER
    UserGroupsProcessor.Bomb.mapResult(builder) {
        // The idea is that we use this to test handling of jobs that are causing consistent crashes with the
        // system. We should be able to handle these without killing the entire system.
        throw RuntimeException("Boom!")
    }
    // TODO FIXME THIS SHOULD BE REMOVED LATER
    // TODO FIXME THIS SHOULD BE REMOVED LATER

    // TODO How will we have other internal systems do work here? They won't have a performed by when the task
    // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
    // who is performing the request.

    val streams = KafkaStreams(builder, properties)
    streams.start()

    val restService = createRestService(port = 42100)
    restService.start()

    streams.setStateListener { newState, _ ->
        when (newState) {
            KafkaStreams.State.PENDING_SHUTDOWN, KafkaStreams.State.NOT_RUNNING, KafkaStreams.State.ERROR -> {
                restService.stop(0, 30, TimeUnit.SECONDS)
            }
            else -> {
            }
        }
    }

    // From this we can't even extract which job was the problem. We probably need to handle this some other way.
    // We do not want a single bad job to take down the entire thing.
    streams.setUncaughtExceptionHandler { t, e ->
        // We should probably still log what happened...
        // mapResult could help with exception handling though.
    }

    streams.addShutdownHook()
}

data class RestRequest(override val header: RequestHeader) : StorageRequest

fun createRestService(port: Int) = embeddedServer(Netty, port = port) {
    install(GsonSupport)
    install(Compression)
    install(DefaultHeaders)

    routing {
        get("/api/files/") {
            val (_, connection) = parseStorageRequestAndValidate() ?: return@get
            val path = queryParamOrBad("path") ?: return@get

            try {
                val result = connection.fileQuery.listAt(connection.paths.parseAbsolute(path))
                call.respond(result)
            } catch (ex: Exception) {
                ex.printStackTrace()
                TODO("Change interface such that we don't throw exceptions we need to handle here")
            }
        }
    }
}

suspend fun PipelineContext<Unit>.parseStorageRequestAndValidate(): Pair<StorageRequest, Connection>? {
    val (username, password) = call.request.basicAuth() ?: return run {
        call.respond(HttpStatusCode.Unauthorized)
        null
    }

    val uuid = call.request.headers["Job-Id"] ?: return run {
        call.respond(HttpStatusCode.BadRequest) // TODO
        null
    }

    val request = RestRequest(RequestHeader(uuid, ProxyClient(username, password)))

    val connection = validateRequest(request).capture() ?: return run {
        call.respond(HttpStatusCode.Unauthorized)
        null
    }

    return Pair(request, connection)
}

fun ApplicationRequest.basicAuth(): Pair<String, String>? {
    val auth = authorization() ?: return null
    if (!auth.startsWith("Basic ")) return null
    val decoded = String(Base64.getDecoder().decode(auth.substringAfter("Basic ")))
    if (decoded.indexOf(':') == -1) return null

    return Pair(decoded.substringBefore(':'), decoded.substringAfter(':'))
}