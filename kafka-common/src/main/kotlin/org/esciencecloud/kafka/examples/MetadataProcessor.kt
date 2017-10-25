package org.esciencecloud.kafka.examples

import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.TableDescription

// Commonly shared stuff (i.e. types) for the meta-data processor

data class MetadataKey(val path: String, val metaKey: String)

object MetadataProcessor {
    val UpdateStream = StreamDescription("requests.metadata.update", jsonSerde<MetadataKey>(), Serdes.String())
    val MaterializedTable = TableDescription("aggregated-metadata", jsonSerde<MetadataKey>(), Serdes.String())
}
