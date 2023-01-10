package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.service.ElasticServiceInstance
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.Time
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Period
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticAudit(
    @JsonProperty("@timestamp")
    val timestamp: String?,
    @JsonProperty("jobId")
    val jobId: String?,
    @JsonIgnore
    @JsonProperty("handledBy")
    val handledBy: ElasticServiceInstance?,
    @JsonProperty("causedBy")
    val causedBy: String?,
    @JsonProperty("requestName")
    val requestName: String?,
    @JsonProperty("userAgent")
    val userAgent: String?,
    @JsonProperty("remoteOrigin")
    val remoteOrigin: String?,
    @JsonProperty("requestSize")
    val requestSize: Int?,
    @JsonProperty("responseCode")
    val responseCode: Int?,
    @JsonProperty("responseTime")
    val responseTime: Long?,
    @JsonProperty("_id")
    val id: String?,
    @JsonProperty("_index")
    val index: String?,
    @JsonProperty("_score")
    val score: String?
)

@Serializable
data class HttpCallLogEntry(
    val jobId: String,
    val handledBy: ServiceInstance,
    val causedBy: String?,

    val requestName: String,
    val userAgent: String?,
    val remoteOrigin: String,

    val token: SecurityPrincipalToken?,

    val requestSize: Long,
    val requestJson: JsonElement?,

    val responseCode: Int,
    val responseTime: Long,

    val expiry: Long,

    val project: String?
)

@Serializable
data class AuditEvent<A>(
    val http: HttpCallLogEntry,
    val request: A
)

data class AuditData(
    val requestStart: Long
) {
    var retentionPeriod: Period? = null
    var requestToAudit: Any? = null
    var securityPrincipalTokenToAudit: SecurityPrincipalToken? = null

    /**
     * We allow a request to skip auditing by setting [skipAuditing] to `true`
     *
     * This will completely skip any and all auditing of the request. Use with care.
     */
    var skipAuditing: Boolean = false

    companion object {
        val auditDataKey = AttributeKey<AuditData>("audit-data")
    }
}

var IngoingCall.audit: AuditData
    get() = attributes[AuditData.auditDataKey]
    internal set(value) {
        attributes[AuditData.auditDataKey] = value
    }

val IngoingCall.auditOrNull: AuditData?
    get() = attributes.getOrNull(AuditData.auditDataKey)

// Backwards compatible handler
fun <A> CallHandler<*, *, *>.audit(payload: A) {
    if (ctx.auditOrNull == null) ctx.audit = AuditData(Time.now())
    ctx.audit.requestToAudit = payload
}
