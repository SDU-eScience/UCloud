package dk.sdu.cloud.file.services

import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.services.background.BackgroundScope
import kotlinx.coroutines.launch

class StorageEventProducer(
    private val delegate: EventProducer<StorageEvent>,
    private val exceptionHandler: (Throwable) -> Unit
) : EventProducer<StorageEvent> {
    override val stream: EventStream<StorageEvent> get() = delegate.stream

    override suspend fun produce(event: StorageEvent) {
        produce(listOf(event))
    }

    override suspend fun produce(events: List<StorageEvent>) {
        try {
            delegate.produce(events)
        } catch (ex: Throwable) {
            exceptionHandler(ex)
        }
    }

    fun produceInBackground(event: StorageEvent) {
        produceInBackground(listOf(event))
    }

    fun produceInBackground(events: List<StorageEvent>) {
        BackgroundScope.launch {
            produce(events)
        }
    }
}
