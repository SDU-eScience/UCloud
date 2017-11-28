package org.esciencecloud.transactions

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.kafka.JsonSerde
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.TableDescription
import org.esciencecloud.storage.model.Request
import org.esciencecloud.storage.model.RequestResponseStream

// TODO This should be exported by the apps service
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = HPCAppRequest.Start::class, name = "start"),
        JsonSubTypes.Type(value = HPCAppRequest.Cancel::class, name = "cancel"))
sealed class HPCAppRequest {
    data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : HPCAppRequest()
    data class Cancel(val jobId: Long) : HPCAppRequest()
}

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}


object HPCStreams {
    // TODO This needs to be cleaned up
    val AppRequests = RequestResponseStream<String, HPCAppRequest>("hpcApp",
            Serdes.String(), jsonSerde(), jsonSerde())
}