package dk.sdu.cloud.calls.server

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.service.ServiceInstance
import java.time.Period

data class HttpCallLogEntry(
    val jobId: String,
    val handledBy: ServiceInstance,
    val causedBy: String?,

    val requestName: String,
    val userAgent: String?,
    val remoteOrigin: String,

    val token: SecurityPrincipalToken?,

    val requestSize: Long,
    val requestJson: Any?,

    val responseCode: Int,
    val responseTime: Long,

    val expiry: Long
)

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

    companion object {
        val auditDataKey = AttributeKey<AuditData>("audit-data")
    }
}

var IngoingCall.audit: AuditData
    get() = attributes[AuditData.auditDataKey]
    internal set(value) {
        attributes[AuditData.auditDataKey] = value
    }

// Backwards compatible handler
fun <A> CallHandler<*, *, *>.audit(payload: A) {
    ctx.audit.requestToAudit = payload
}
