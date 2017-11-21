package org.esciencecloud.abc.processors

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_HPC_APP_EVENTS
import org.esciencecloud.abc.HPCStoreEndpoints
import org.esciencecloud.abc.SlurmEvent
import org.esciencecloud.abc.SlurmEventBegan
import org.esciencecloud.abc.SlurmEventEnded
import org.esciencecloud.abc.api.HPCAppEvent

/**
 * Processes events from slurm
 */
class SlurmProcessor(
        private val interactiveStore: HPCStoreEndpoints,
        private val mapper: ObjectMapper,
        private val producer: KafkaProducer<String, String>
) {
    fun handle(event: SlurmEvent) {
        when (event) {
            is SlurmEventBegan -> {
                val key = runBlocking { interactiveStore.querySlurmIdToInternal(event.jobId) }.orThrow()
                val appEvent = mapper.writeValueAsString(HPCAppEvent.Started(event.jobId))
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))
            }

            is SlurmEventEnded -> {
                // TODO Not sure if throwing is the right choice here, but not sure how else to handle it
                val key = runBlocking { interactiveStore.querySlurmIdToInternal(event.jobId) }.orThrow()
                val appEvent = mapper.writeValueAsString(HPCAppEvent.SuccessfullyCompleted(event.jobId))
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))

                // TODO Transfer output files
                // TODO Clean up after job
            }
        }
    }
}