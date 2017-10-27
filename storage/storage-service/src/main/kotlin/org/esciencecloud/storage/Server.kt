package org.esciencecloud.storage

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import java.util.*

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

class GenericResponse<out InputType : Any>(
        val successful: Boolean,
        val errorMessage: String?,
        val input: InputType
)

const val REQUEST = "request"
const val RESPONSE = "response"
const val POLICY = "policy"

object StorageProcessor {
    val PREFIX = "storage"

    object UserGroups {
        val PREFIX = "${StorageProcessor.PREFIX}.ugs"

        val CreateUserRequestStream = StreamDescription(
                "$REQUEST.$PREFIX.create_user",
                Serdes.String(),
                jsonSerde<CreateUserRequest>()
        )

        val CreateUserResponseStream = StreamDescription(
                "$RESPONSE.$PREFIX.create_user",
                Serdes.String(),
                jsonSerde<GenericResponse<CreateUserRequest>>()
        )

        val ModifyUserRequset = StreamDescription(
                "$REQUEST.$PREFIX.modify_user",
                Serdes.String(),
                jsonSerde<ModifyUserRequest>()
        )

        val ModifyUserResponse = StreamDescription(
                "$RESPONSE.$PREFIX.modify_user",
                Serdes.String(),
                jsonSerde<GenericResponse<ModifyUserRequest>>()
        )
    }
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

data class ResponseEvent(
        val statusCode: Int
)

//
// Doing some experimentation here. It seems, to me at least, that exceptions are very meaningless in a distributed
// system. Here I try to build a few simple Kotlin types that "Results" inspired by those you would find in many
// functional languages. These can either be successful, i.e., Ok(T) or they can be an Error(code, message).
// These seem like more appropriate types to send around in a system. I also try to build them in such a way that
// we still retain much of the convenience we have from exceptions.
//
// We also have to make sure that these error types are easily traceable throughout a system. We need to be able to
// pinpoint why certain errors occur in the system. This becomes, much harder, when we have a distributed system.
//

sealed class Result<out T : Any> {
    companion object {
        private val lastError = object : ThreadLocal<Error<*>>() {
            override fun initialValue(): Error<*> = Error<Any>(-1, "No error set")
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> lastError(): Error<T> = lastError.get() as Error<T>
    }

    fun capture(): T? {
        return when (this) {
            is Ok<T> -> this.result

            is Error<T> -> {
                lastError.set(this)
                null
            }
        }
    }
}

// It is always safe to cast an error regardless of the generic
class Error<out T : Any>(
        val errorCode: Int,
        val message: String
) : Result<T>()

class Ok<out T : Any>(
        val result: T
) : Result<T>() {
    companion object {
        fun empty(): Ok<Unit> = Ok(Unit)
    }
}

fun createUser(request: CreateUserRequest): Result<Unit> {
    val connection = validateRequest(request).capture() ?: return Result.lastError()
    val userAdmin = connection.userAdmin ?: return Error(123, "Not allowed")
    userAdmin.createUser(request.username, request.password)
    return Ok.empty()
}

fun main(args: Array<String>) {
    val storageFactory = retrieveConnectionFactory()
    val adminConnection = createPersistentAdminConnection(storageFactory)

    val properties = Properties()
    properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
    properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
    properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events

    val builder = KStreamBuilder()

    //
    // According to this[1] post, you should just call your external systems in a sync manner from within the map
    // call. I am, however, concerned about how this will scale. But maybe if we just run more instances of this
    // application we will be fine? I don't know. We will need to check out if this is a non-issue.
    //
    // [1]: https://stackoverflow.com/questions/42064430/external-system-queries-during-kafka-stream-processing
    //
    run {
        val reqs = StorageProcessor.UserGroups.CreateUserRequestStream
        val resps = StorageProcessor.UserGroups.CreateUserResponseStream

        reqs.stream(builder).map { key, request ->
            val result = createUser(request)
            KeyValue(key, GenericResponse(result is Ok, (result as? Error)?.message, request))
        }.to(resps.keySerde, resps.valueSerde, resps.name)
    }

    // TODO How will we have other internal systems do work here? They won't have a performed by when the task
    // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
    // who is performing the request.

    run {
        // Most of this process should be fully automatic. Actually this is essentially just mapping into the correct
        // call. Ideally this is so simple that we only need one test for the entire mapping. The rest become
        // unit tests of the individual calls.
        val reqs = StorageProcessor.UserGroups.ModifyUserRequset
        val resps = StorageProcessor.UserGroups.ModifyUserResponse

        // TODO Map
    }

    val streams = KafkaStreams(builder, properties)
    streams.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        streams.close()
    })
}