package dk.sdu.cloud.calls

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import java.time.Period

class AuditDescription<A : Any> internal constructor(
    val context: CallDescription<*, *, *>,
    val auditType: TypeReference<A>,
    val retentionPeriod: Period,
    val longRunningResponseTime: Boolean
) {
    companion object {
        val descriptionKey = AttributeKey<AuditDescription<Any>>("audit-description")
        val DEFAULT_RETENTION_PERIOD: Period = Period.ofDays(365)
    }
}

inline fun <reified A : Any> CallDescription<*, *, *>.audit(
    noinline builder: AuditDescriptionBuilder<A>.() -> Unit = {}
) {
    @Suppress("UNCHECKED_CAST")
    attributes[AuditDescription.descriptionKey] =
        AuditDescriptionBuilder<A>(this, jacksonTypeRef()).also(builder).build() as AuditDescription<Any>
}

@Suppress("UNCHECKED_CAST")
val CallDescription<*, *, *>.auditOrNull: AuditDescription<Any>?
    get() = attributes.getOrNull(AuditDescription.descriptionKey)

class AuditDescriptionBuilder<A : Any>(
    val context: CallDescription<*, *, *>,
    val auditType: TypeReference<A>
) {
    var retentionPeriod: Period = AuditDescription.DEFAULT_RETENTION_PERIOD
    var longRunningResponseTime: Boolean = false

    fun build(): AuditDescription<A> = AuditDescription(context, auditType, retentionPeriod, longRunningResponseTime)
}
