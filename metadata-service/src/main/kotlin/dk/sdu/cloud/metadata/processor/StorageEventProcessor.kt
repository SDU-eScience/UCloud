package dk.sdu.cloud.metadata.processor

import dk.sdu.cloud.storage.api.StorageEvent
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory

typealias StorageEventStream = KStream<String, StorageEvent>

class StorageEventProcessor(
    private val stream: StorageEventStream
) {
    fun init() {
        log.info("Initializing storage event processor")
        stream.foreach { _, event -> handleEvent(event) }
        log.info("Done")
    }

    private fun handleEvent(event: StorageEvent) {

    }

    companion object {
        private val log = LoggerFactory.getLogger(StorageEventProcessor::class.java)
    }
}