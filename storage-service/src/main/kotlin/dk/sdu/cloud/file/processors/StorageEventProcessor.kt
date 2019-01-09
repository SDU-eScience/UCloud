package dk.sdu.cloud.file.processors

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface StorageEventListener<Ctx : FSUserContext> {
    suspend fun isInterested(batch: List<StorageEvent>): Boolean
    suspend fun handleBatch(ctx: Ctx, batch: List<StorageEvent>)
}

class StorageEventProcessor<Ctx : FSUserContext>(
    private val listeners: List<StorageEventListener<Ctx>>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val eventConsumerFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(StorageEvents.events).configure { root ->
            root
                .batched(
                    batchTimeout = 500,
                    maxBatchSize = 1000
                )
                .consumeBatchAndCommit { batch ->
                    if (batch.isEmpty()) return@consumeBatchAndCommit
                    log.debug("Handling batch: $batch")

                    runBlocking {
                        batch
                            .asSequence()
                            .map { it.second }
                            .groupBy { it.creator }
                            .entries
                            .mapNotNull { (creator, events) ->
                                val interestedListeners = listeners.filter { it.isInterested(events) }
                                log.info("$interestedListeners")
                                if (interestedListeners.isEmpty()) return@mapNotNull null

                                BackgroundScope.async {
                                    commandRunnerFactory.withContext(creator) { ctx ->
                                        interestedListeners.forEach { it.handleBatch(ctx, events) }
                                    }

                                    Unit
                                }
                            }
                            .awaitAll()
                    }
                }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
