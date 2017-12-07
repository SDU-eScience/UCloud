package org.esciencecloud.service

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
import io.ktor.request.header
import io.ktor.request.httpMethod
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.delay
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.StreamsMetadata
import org.esciencecloud.client.HttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

sealed class KafkaRPCException(message: String, val httpStatusCode: HttpStatusCode) : Exception(message)
class NotFoundRPCException(entity: String) : KafkaRPCException("Not found: $entity", HttpStatusCode.NotFound)
class BadMessageRPCException : KafkaRPCException("Bad message", HttpStatusCode.BadRequest)
class InternalRPCException : KafkaRPCException("Internal error", HttpStatusCode.InternalServerError)

class KafkaRPCEndpoint<Key : Any, Value : Any>(
        val httpMethod: HttpMethod,
        val endpointForServer: String,
        val keyParser: suspend (ApplicationCall) -> Key,
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
                noinline keyParser: (String) -> Key = { it as? Key ?: throw BadMessageRPCException() }
        ) = KafkaRPCEndpoint(
                httpMethod = HttpMethod.Get,
                endpointForServer = "$root/{key}",
                keyParser = {
                    val key = it.parameters["key"]
                    if (key != null) keyParser(key) else throw BadMessageRPCException()
                },
                endpointFormatter = { "$root/$it" },
                table = table,
                keySerde = keySerde,
                valueSerde = valueSerde
        )
    }

    suspend fun query(streamProcessor: KafkaStreams, thisHost: HostInfo, secretToken: String, key: Key,
                      allowRetries: Boolean = true): Value {
        var tries = 0
        while (tries < 5) {
            val hostWithData = streamProcessor.metadataForKey(table, key, keySerde.serializer())

            try {
                return when {
                    hostWithData == StreamsMetadata.NOT_AVAILABLE -> throw NotFoundRPCException(key.toString())

                    thisHost == hostWithData.hostInfo() -> {
                        val store = streamProcessor.store(table, QueryableStoreTypes.keyValueStore<Key, Value>())
                        val value = store[key] ?: run {
                            log.error("Expected value to be found in local server")
                            log.error("Table: $table, key: $key")

                            throw InternalRPCException()
                        }

                        value
                    }

                    else -> {
                        val returnedEndpoint = endpointFormatter(key)
                        val uri = if (!returnedEndpoint.startsWith("/")) "/$returnedEndpoint" else returnedEndpoint

                        val endpoint = "http://${hostWithData.host()}:${hostWithData.port()}$uri"
                        val response = HttpClient.post(endpoint) {
                            setMethod(httpMethod.value)
                            addHeader(KafkaRPCServer.APP_TOKEN_HEADER, secretToken)
                        }

                        return try {
                            valueSerde.deserializer().deserialize("", response.responseBodyAsBytes)
                        } catch (ex: Exception) {
                            log.warn("Unable to deserialize response from other Kafka store.")
                            log.warn("Endpoint was: $endpoint")
                            log.warn("Table: $table, key: $key")
                            log.warn("Raw response: ${response.responseBody} (${response.statusCode})")
                            log.warn("Exception: ${ex.stackTraceToString()}")

                            throw InternalRPCException()
                        }
                    }
                }
            } catch (ex: KafkaRPCException) {
                when (ex) {
                // Continue on not found exceptions if we allow retries
                    is NotFoundRPCException -> if (!allowRetries) throw ex
                    else -> throw ex
                }
            }

            log.debug("Retrying: $allowRetries")
            tries++
            delay(tries.toLong(), TimeUnit.SECONDS)
        }

        throw NotFoundRPCException(key.toString())
    }
}

class KafkaRPCServer(
        private val hostname: String,
        private val port: Int,
        private val endpoints: List<KafkaRPCEndpoint<Any, Any>>,
        private val streams: KafkaStreams,
        private val secretToken: String
) {
    private var server: ApplicationEngine? = null
    private val thisHost = HostInfo(hostname, port)

    companion object {
        const val APP_TOKEN_HEADER = "App-Token"
        private val log = LoggerFactory.getLogger(KafkaRPCServer::class.java)
    }

    fun start(wait: Boolean = false) {
        if (this.server != null) throw IllegalStateException("RPC Server already started!")

        val server = embeddedServer(CIO, port = port) {
            install(CallLogging)
            install(DefaultHeaders)
            install(ContentNegotiation) {
                jackson { registerKotlinModule() }
            }

            routing { configureExisting(this) }
        }

        server.start(wait = wait)

        this.server = server
    }

    fun configureExisting(routing: Route) = with(routing) {
        endpoints.forEach { endpoint ->
            route(endpoint.endpointForServer, endpoint.httpMethod) {
                handle {
                    if (call.request.httpMethod != HttpMethod.Get) {
                        return@handle call.respond(HttpStatusCode.MethodNotAllowed)
                    }

                    // TODO Validate certificates instead of using a simple shared secret
                    val appToken = call.request.header(APP_TOKEN_HEADER)
                    if (appToken != secretToken) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } else {
                        val key = try {
                            endpoint.keyParser(call)
                        } catch (ex: Exception) {
                            // TODO Exceptions
                            return@handle call.respond(HttpStatusCode.InternalServerError)
                        }

                        val message = try {
                            endpoint.query(streams, thisHost, secretToken, key)
                        } catch (ex: Exception) {
                            return@handle when (ex) {
                                is NotFoundRPCException -> call.respond(HttpStatusCode.NotFound)
                                is BadMessageRPCException -> call.respond(HttpStatusCode.BadRequest)
                                is InternalRPCException -> call.respond(HttpStatusCode.InternalServerError)
                                else -> {
                                    log.info("Unhandled exception type in RPC query: ${ex.javaClass.simpleName}")
                                    if (ex is KafkaRPCException) {
                                        log.warn("Unhandled exception was of type KafkaRPCException!")
                                    }
                                    call.respond(HttpStatusCode.InternalServerError)
                                }
                            }
                        }

                        call.respond(message)
                    }
                }
            }
        }
    }

    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        this.server!!.stop(gracePeriod, timeout, timeUnit)
    }
}
