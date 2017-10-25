package org.esciencecloud.kafka

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.StreamsMetadata
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.httpMethod
import org.jetbrains.ktor.request.uri
import org.jetbrains.ktor.response.respond
import org.slf4j.LoggerFactory

import org.esciencecloud.kafka.TableUtils.log
import org.jetbrains.ktor.response.respondText

private object TableUtils {
    val log = LoggerFactory.getLogger(TableUtils::class.java)!!
}

suspend fun PipelineContext<Unit>.queryParamOrBad(key: String): String? {
    val result = call.request.queryParameters[key]
    return if (result == null) {
        call.respond(HttpStatusCode.BadRequest)
        null
    } else {
        result
    }
}

suspend fun PipelineContext<Unit>.respondInternal(why: String) {
    log.warn("Encountered an internal server error: $why")
    call.respondText("Internal Server Error", status = HttpStatusCode.InternalServerError)
}

suspend fun <Key, Value> PipelineContext<Unit>.lookupKafkaStoreOrProxy(
        streams: KafkaStreams,
        thisHost: HostInfo,
        table: TableDescription<Key, Value>,
        key: Key,
        payload: Any? = null
) {
    val hostWithData = table.findStreamMetadata(streams, key)
    when {
        hostWithData == StreamsMetadata.NOT_AVAILABLE -> call.respond(HttpStatusCode.NotFound)

        thisHost == hostWithData.hostInfo() -> {
            val store = table.localKeyValueStore(streams)
            val value = store[key] ?: return respondInternal("Expected value to be found in local server")
            call.respond(value)
        }

        else -> {
            val rawUri = call.request.uri // This includes query params
            val uri = if (!rawUri.startsWith("/")) "/$rawUri" else rawUri

            val endpoint = "http://${hostWithData.host()}:${hostWithData.port()}$uri"
            val response = when (call.request.httpMethod) {
                HttpMethod.Get -> JsonHttpClient.get<Any>(endpoint)
                HttpMethod.Post -> JsonHttpClient.post(endpoint, payload)
                else -> TODO("Not yet implemented")
            }
            call.respond(response)
        }
    }
}