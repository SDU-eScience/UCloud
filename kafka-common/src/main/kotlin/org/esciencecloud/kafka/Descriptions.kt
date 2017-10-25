package org.esciencecloud.kafka

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.StreamsMetadata

class StreamDescription<Key, Value>(val name: String, val keySerde: Serde<Key>, val valueSerde: Serde<Value>) {
    fun stream(builder: KStreamBuilder): KStream<Key, Value> =
            builder.stream(keySerde, valueSerde, name)

    fun groupByKey(builder: KStreamBuilder): KGroupedStream<Key, Value> =
            stream(builder).groupByKey(keySerde, valueSerde)
}

class TableDescription<Key, Value>(val name: String, val keySerde: Serde<Key>, val valueSerde: Serde<Value>) {
    fun findStreamMetadata(streams: KafkaStreams, key: Key): StreamsMetadata {
        return streams.metadataForKey(name, key, keySerde.serializer())
    }

    fun localKeyValueStore(streams: KafkaStreams): ReadOnlyKeyValueStore<Key, Value> =
            streams.store(name, QueryableStoreTypes.keyValueStore<Key, Value>())
}