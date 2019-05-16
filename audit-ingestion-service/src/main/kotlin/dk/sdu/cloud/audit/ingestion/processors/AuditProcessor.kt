package dk.sdu.cloud.audit.ingestion.processors

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        events.subscribe(HttpLogsStream, EventConsumer.Batched { rawBatch ->
            if (rawBatch.isNotEmpty()) log.info("Accepting batch of size ${rawBatch.size}")

            rawBatch
                .asSequence()
                .mapNotNull { document ->
                    runCatching {
                        val tree = defaultMapper.readTree(document)
                        val requestName = tree["requestName"].textValue()!!

                        Pair(requestName, document)
                    }.getOrNull()
                }
                .groupBy { (requestName, _) -> requestName }
                .flatMap { (requestName, batch) ->
                    val dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY.MM.dd"))
                    val indexName = "http_logs_$requestName-$dateSuffix".toLowerCase()

                    log.debug("Inserting ${batch.size} elements into $indexName")

                    batch
                        .map { (_, doc) ->
                            IndexRequest(indexName, "doc").apply {
                                source(doc.toByteArray(Charsets.UTF_8), XContentType.JSON)
                            }
                        }
                }
                .chunked(1000)
                .forEach { chunk ->
                    client.bulk(BulkRequest().also { it.add(chunk) })
                }
        })
    }

    companion object : Loggable {
        override val log = logger()
    }
}
