package dk.sdu.cloud.audit.ingestion.processors

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.JsonData
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.server.ElasticAudit
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.ElasticServiceDefinition
import dk.sdu.cloud.service.ElasticServiceInstance
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.ConnectException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

object HttpLogsStream : EventStream<String> {
    override val desiredPartitions: Int? = null
    override val desiredReplicas: Short? = null
    override val keySelector: (String) -> String = { "notused" }
    override val name: String = "http.logs"
    override fun deserialize(value: String): String = value
    override fun serialize(event: String): String = event
}

@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticAuditIn(
    @JsonProperty("@timestamp")
    val timestamp: String?,
    @JsonProperty("jobId")
    val jobId: String?,
    @JsonProperty("handledBy")
    val handledBy: ElasticServiceInstance?,
    @JsonProperty("causedBy")
    val causedBy: String?,
    @JsonProperty("requestName")
    val requestName: String?,
    @JsonProperty("requestJson")
    val requestJson: JsonObject,
    @JsonProperty("userAgent")
    val userAgent: String?,
    @JsonProperty("remoteOrigin")
    val remoteOrigin: String?,
    @JsonProperty("token")
    val token: ElasticSecurityPrincipalToken?,
    @JsonProperty("requestSize")
    val requestSize: Int?,
    @JsonProperty("responseCode")
    val responseCode: Int?,
    @JsonProperty("responseTime")
    val responseTime: Long?,
    @JsonProperty("expiry")
    val expiry: Long?,
    @JsonProperty("project")
    val project: String?
)
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticSecurityPrincipalToken(
    @JsonProperty("project")
    val principal: ElasticSecurityPrincipal,
    @JsonProperty("issuedAt")
    val issuedAt: Long,
    @JsonProperty("expiresAt")
    val expiresAt: Long,
    @JsonProperty("publicSessionReference")
    val publicSessionReference: String?,
    @JsonProperty("extendedBy")
    val extendedBy: String? = null,
    @JsonProperty("extendedByChain")
    val extendedByChain: List<String> = emptyList()

    // NOTE: DO NOT ADD SENSITIVE DATA TO THIS CLASS (INCLUDING JWT)
    // IT IS USED IN THE AUDIT SYSTEM
)
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticSecurityPrincipal(
    @JsonProperty("username")
    val username: String,
    @JsonProperty("role")
    val role: String,
    @JsonProperty("firstName")
    val firstName: String,
    @JsonProperty("lastName")
    val lastName: String,
    @JsonProperty("email")
    val email: String? = null,
    @JsonProperty("twoFactorAuthentication")
    val twoFactorAuthentication: Boolean = true,
    @JsonProperty("principalType")
    val principalType: String? = null,
    @JsonProperty("serviceAgreementAccepted")
    val serviceAgreementAccepted: Boolean = false,
    @JsonProperty("organization")
    val organization: String? = null
)
class AuditProcessor(
    private val events: EventStreamService,
    private val client: ElasticsearchClient,
    private val isDevMode: Boolean = false,
) {
    private val didWarnAboutDevMode = AtomicBoolean(false)

    fun init() {
        events.subscribe(HttpLogsStream, EventConsumer.Batched() { rawBatch ->
            if (didWarnAboutDevMode.get()) return@Batched
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
                        val handledBy = defaultMapper.decodeFromJsonElement<JsonObject>(tree["handledBy"]!!)
                        val handledByDef = defaultMapper.decodeFromJsonElement<JsonObject>(handledBy["definition"]!!)
                        val token = defaultMapper.decodeFromJsonElement<JsonObject>(tree["token"]!!)
                        val principal = defaultMapper.decodeFromJsonElement<JsonObject?>(token["principal"] ?: JsonObject(emptyMap()))
                        val elasticAudit = ElasticAuditIn(
                            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                            jobId = (tree["jobId"] as JsonPrimitive).content,
                            handledBy = ElasticServiceInstance(
                                definition = ElasticServiceDefinition(
                                    name = (handledByDef["name"] as JsonPrimitive).content,
                                    version = (handledByDef["version"] as JsonPrimitive).content
                                ),
                                hostname = (handledBy["hostname"] as JsonPrimitive).content,
                                port = (handledBy["port"] as JsonPrimitive).int,
                                ipAddress = (handledBy["ipAddress"] as JsonPrimitive).content
                            ),
                            causedBy = (tree["causedBy"] as JsonPrimitive).content,
                            requestName = requestName,
                            userAgent = (tree["userAgent"] as JsonPrimitive).content,
                            remoteOrigin = (tree["remoteOrigin"] as JsonPrimitive).content,
                            token = ElasticSecurityPrincipalToken(
                                principal = ElasticSecurityPrincipal(
                                    username = (principal?.get("username") as JsonPrimitive).content,
                                    role = (principal?.get("role") as JsonPrimitive).content,
                                    firstName = (principal?.get("firstName") as JsonPrimitive).content,
                                    lastName = (principal?.get("lastName") as JsonPrimitive).content,
                                    email = (principal?.get("email") as JsonPrimitive).content,
                                    twoFactorAuthentication = (principal?.get("twoFactorAuthentication") as JsonPrimitive).boolean,
                                    principalType = (principal?.get("principalType") as JsonPrimitive).content,
                                    serviceAgreementAccepted = (principal?.get("serviceAgreementAccepted") as JsonPrimitive).boolean,
                                    organization = (principal?.get("organization") as JsonPrimitive).content
                                ),
                                //scopes = emptyList(),
                                issuedAt = (token["issuedAt"] as JsonPrimitive).long,
                                expiresAt = (token["expiresAt"] as JsonPrimitive).long,
                                publicSessionReference = (token["publicSessionReference"] as JsonPrimitive).content,
                                extendedBy = (token["extendedBy"] as JsonPrimitive).content,
                                extendedByChain = emptyList()
                            ),
                            requestJson = (tree["requestJson"] as JsonObject),
                            requestSize = (tree["requestSize"] as JsonPrimitive).int ,
                            responseCode = (tree["responseCode"] as JsonPrimitive).int,
                            responseTime = (tree["responseTime"] as JsonPrimitive).long,
                            expiry = (tree["expiry"] as JsonPrimitive).long,
                            project = (tree["project"] as JsonPrimitive).content

                        )
                        Pair(requestName, elasticAudit)
                    }.getOrNull()
                }
                .groupBy { (requestName, _) -> requestName }
                .flatMap { (requestName, batch) ->
                    val dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    val indexName = "http_logs_$requestName-$dateSuffix".lowercase()
                    log.trace("Inserting ${batch.size} elements into $indexName")
                    batch
                        .map { (_, doc) ->
                            BulkOperation.Builder()
                                .index(
                                    IndexOperation.Builder<ElasticAuditIn>()
                                        .index(indexName)
                                        .document(doc)
                                        .build()
                                )
                                .build()
                        }
                }
                .chunked(1000)
                .forEach { chunk ->
                    try {
                        val reponse = client.bulk(BulkRequest.Builder().operations(chunk).build())
                    } catch (ex: Throwable) {
                        if (ex is ExecutionException || ex is ConnectException || ex.cause is ExecutionException || ex.cause is ConnectException) {
                            if (isDevMode) {
                                if (didWarnAboutDevMode.compareAndSet(false, true)) {
                                    log.info("Could not contact ElasticSearch. We are assuming that this is not needed in" +
                                        "dev mode - No activity will be produced!")
                                    return@forEach
                                }
                            } else {
                                log.warn(ex.stackTraceToString())
                                return@forEach
                            }
                        }
                        log.warn(ex.stackTraceToString())
                    }
                }
        })
    }

    companion object : Loggable {
        override val log = logger()
    }
}
