package org.esciencecloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.esciencecloud.service.KafkaRequest

// Model
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = HPCAppRequest.Start::class, name = "start"),
        JsonSubTypes.Type(value = HPCAppRequest.Cancel::class, name = "cancel"))
sealed class HPCAppRequest {
    data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : HPCAppRequest()
    data class Cancel(val jobId: Long) : HPCAppRequest()
}
