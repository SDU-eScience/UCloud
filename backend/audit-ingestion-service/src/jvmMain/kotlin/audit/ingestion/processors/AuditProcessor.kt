package dk.sdu.cloud.audit.ingestion.processors

import com.fasterxml.jackson.databind.node.ObjectNode
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object HttpLogsStream : EventStream<String> {
    override val desiredPartitions: Int? = null
    override val desiredReplicas: Short? = null
    override val keySelector: (String) -> String = { "notused" }
    override val name: String = "http.logs"
    override fun deserialize(value: String): String = value
    override fun serialize(event: String): String = event
}

class AuditProcessor(
    private val events: EventStreamService,
    private val client: RestHighLevelClient
) {
    fun init() {
        events.subscribe(HttpLogsStream, EventConsumer.Batched() { rawBatch ->
            if (rawBatch.isNotEmpty()) log.trace("Accepting batch of size ${rawBatch.size}")

            rawBatch
                .asSequence()
                .mapNotNull { document ->
                    runCatching {
                        val tree = defaultMapper.decodeFromString<JsonObject>(document)
                        val requestName = (tree["requestName"] as JsonPrimitive).content
                        if (requestName == "healthcheck.status") {
                            return@runCatching null
                        }
                        val newTree = buildJsonObject {
                            for (e in tree) {
                                put(e.key, e.value)
                            }

                            put("@timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                        }

                        Pair(requestName, defaultMapper.encodeToJsonElement(newTree))
                    }.getOrNull()
                }
                .groupBy { (requestName, _) -> requestName }
                .flatMap { (requestName, batch) ->
                    val dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    val indexName = "http_logs_$requestName-$dateSuffix".toLowerCase()

                    log.trace("Inserting ${batch.size} elements into $indexName")

                    batch
                        .map { (_, doc) ->
                            IndexRequest(indexName).apply {
                                source(defaultMapper.encodeToString(doc).encodeToByteArray(), XContentType.JSON)
                            }
                        }
                }
                .chunked(1000)
                .forEach { chunk ->
                    client.bulk(BulkRequest().also { it.add(chunk) }, RequestOptions.DEFAULT)
                }
        })
    }

    companion object : Loggable {
        override val log = logger()
    }
}
