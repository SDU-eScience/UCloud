package org.esciencecloud.abc.processors

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.esciencecloud.abc.*
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_HPC_APP_EVENTS
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.ssh.SSHConnection
import org.esciencecloud.abc.ssh.SimpleSSHConfig
import org.esciencecloud.abc.ssh.scpDownload
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.model.StoragePath

/**
 * Processes events from slurm
 */
class SlurmProcessor(
        private val interactiveStore: HPCStoreEndpoints,
        private val mapper: ObjectMapper,
        private val producer: KafkaProducer<String, String>,
        private val sshConfig: SimpleSSHConfig,
        private val storageConnectionFactory: StorageConnectionFactory
) {
    suspend fun handle(event: SlurmEvent) {
        // TODO This should probably be called from a suspending context
        when (event) {
            is SlurmEventBegan -> {
                val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()
                val appEvent = mapper.writeValueAsString(HPCAppEvent.Started(event.jobId))
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))
            }

            is SlurmEventEnded -> {
                // TODO Not sure if throwing is the right choice here, but not sure how else to handle it
                // A lot of this code can crash, and we really need it to. But at the same time we don't want it to
                // go down. Ideally we can configure Kafka to retry later.

                val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()

                // TODO There must be some kind of way to force it to replay values required for interactive store
                // Because without that, we simply cannot start the application without some other instance already
                // having this information ready.
                val appRequest = interactiveStore.queryJobIdToApp(key).orThrow()
                val app = with (appRequest.event.application) { ApplicationDAO.findByNameAndVersion(name, version) }!!
                val outputs = app.parameters
                        .filterIsInstance<ApplicationParameter.OutputFile>()
                        .map { it.map(appRequest.event.parameters[it.name]!!) }

                val storage = with (appRequest.header.performedFor) {
                    storageConnectionFactory.createForAccount(username, password)
                }.capture() ?: TODO("Handle this")

                SSHConnection.connect(sshConfig).use { ssh ->
                    // Transfer output files
                    for (transfer in outputs) {
                        ssh.scpDownload(transfer.source) { // TODO Source should be relative to working directory
                            storage.files.put(StoragePath.fromURI(transfer.destination), it)
                        }
                    }

                    // TODO Clean up after job
                }

                val appEvent = mapper.writeValueAsString(HPCAppEvent.SuccessfullyCompleted(event.jobId))
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))
            }
        }
    }
}