package org.esciencecloud.abc

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.StreamsMetadata
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

// TODO This could probably be a shared component
class KafkaRPCEndpoint<Key : Any, Value : Any>(
        val httpMethod: HttpMethod,
        val endpointForServer: String,
        val keyParser: suspend (ApplicationCall) -> Result<Key>,
        val endpointFormatter: (Key) -> String,
        val table: String,
        val keySerde: Serde<Key>,
        val valueSerde: Serde<Value>
) {
    companion object {
        private val log = LoggerFactory.getLogger(KafkaRPCEndpoint::class.java)

        inline fun <reified Key : Any, reified Value : Any> simpleEndpoint(
                root: String,
                table: String,
                keySerde: Serde<Key> = defaultSerdeOrJson(),
                valueSerde: Serde<Value> = defaultSerdeOrJson(),
                noinline keyParser: (String) -> Result<Key> = { resultFromNullable(it as? Key) }
        ) = KafkaRPCEndpoint(
                httpMethod = HttpMethod.Get,
                endpointForServer = "$root/{key}",
                keyParser = {
                    val key = it.parameters["key"]
                    if (key != null) keyParser(key) else Error.invalidMessage()
                },
                endpointFormatter = { "$root/$it" },
                table = table,
                keySerde = keySerde,
                valueSerde = valueSerde
        )

        fun <T : Any> resultFromNullable(t: T?): Result<T> = if (t != null) Ok(t) else Error.invalidMessage()
    }

    suspend fun query(streamProcessor: KafkaStreams, thisHost: HostInfo, key: Key): Result<Value> {
        val hostWithData = streamProcessor.metadataForKey(table, key, keySerde.serializer())

        return when {
            hostWithData == StreamsMetadata.NOT_AVAILABLE -> Error.notFound()

            thisHost == hostWithData.hostInfo() -> {
                val store = streamProcessor.store(table, QueryableStoreTypes.keyValueStore<Key, Value>())
                val value = store[key] ?: return run {
                    log.error("Expected value to be found in local server")
                    log.error("Table: $table, key: $key")

                    Error.internalError()
                }

                Ok(value)
            }

            else -> {
                val returnedEndpoint = endpointFormatter(key)
                val uri = if (!returnedEndpoint.startsWith("/")) "/$returnedEndpoint" else returnedEndpoint

                val endpoint = "http://${hostWithData.host()}:${hostWithData.port()}$uri"
                val response = HttpClient.post(endpoint) {
                    setMethod(httpMethod.value)
                }

                return try {
                    Ok(valueSerde.deserializer().deserialize("", response.responseBodyAsBytes))
                } catch (ex: Exception) {
                    log.warn("Unable to deserialize response from other Kafka store.")
                    log.warn("Endpoint was: $endpoint")
                    log.warn("Table: $table, key: $key")
                    log.warn("Raw response: ${response.responseBody} (${response.statusCode})")
                    log.warn("Exception: ${ex.stackTraceToString()}")

                    Error.internalError()
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified Type : Any> defaultSerdeOrJson(): Serde<Type> = when (Type::class) {
    String::class -> Serdes.String() as Serde<Type>
    Double::class -> Serdes.Double() as Serde<Type>
    Int::class -> Serdes.Integer() as Serde<Type>
    Float::class -> Serdes.Float() as Serde<Type>
    Short::class -> Serdes.Short() as Serde<Type>
    ByteArray::class -> Serdes.ByteArray() as Serde<Type>
    ByteBuffer::class -> Serdes.ByteBuffer() as Serde<Type>
    Bytes::class -> Serdes.Bytes() as Serde<Type>

    else -> jsonSerde()
}

class KafkaRPCServer(
        private val hostname: String,
        private val port: Int,
        private val endpoints: List<KafkaRPCEndpoint<Any, Any>>,
        private val streams: KafkaStreams
) {
    private var server: ApplicationEngine? = null
    private val thisHost = HostInfo(hostname, port)

    fun start(wait: Boolean = false) {
        if (this.server != null) throw IllegalStateException("RPC Server already started!")

        val server = embeddedServer(CIO, port = port) {
            // TODO Validate certificates
            install(CallLogging)
            install(DefaultHeaders)
            install(ContentNegotiation) {
                jackson { registerKotlinModule() }
            }

            routing {
                endpoints.forEach { endpoint ->
                    route(endpoint.endpointForServer, endpoint.httpMethod) {
                        handle {
                            val key = endpoint.keyParser(call)
                            when (key) {
                                is Ok -> call.respond(endpoint.query(streams, thisHost, key.result))
                                is Error -> call.respond(HttpStatusCode.fromValue(key.errorCode), key.message)
                            }
                        }
                    }
                }
            }
        }

        server.start(wait = wait)

        this.server = server
    }

    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        this.server!!.stop(gracePeriod, timeout, timeUnit)
    }
}
