package dk.sdu.cloud.indexing.processor

import dk.sdu.cloud.indexing.services.IndexingService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.api.StorageEvent
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.Logger

class StorageEventProcessor(
    private val stream: KStream<String, StorageEvent>,
    private val indexingService: IndexingService
) {
    fun init() {
        stream.foreach { _, event ->
            indexingService.handleEvent(event)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}