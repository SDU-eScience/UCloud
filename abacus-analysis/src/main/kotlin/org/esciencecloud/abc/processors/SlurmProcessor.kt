package org.esciencecloud.abc.processors

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.esciencecloud.abc.*
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_HPC_APP_EVENTS
import org.esciencecloud.abc.BashEscaper.safeBashArgument
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.ssh.SSHConnection
import org.esciencecloud.abc.ssh.SimpleSSHConfig
import org.esciencecloud.abc.ssh.scpDownload
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.ext.PermissionException
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.model.StoragePath
import org.irods.jargon.core.exception.FileNotFoundException
import org.slf4j.LoggerFactory
import java.net.URI

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
    companion object {
        private val log = LoggerFactory.getLogger(SlurmProcessor::class.java)
    }

    suspend fun handle(event: SlurmEvent) {
        when (event) {
            is SlurmEventBegan -> {
                val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()
                val appEvent = mapper.writeValueAsString(HPCAppEvent.Started(event.jobId))
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))
            }

            is SlurmEventEnded -> {
                val (key, newEvent) = handleEndedEvent(event)
                producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, mapper.writeValueAsString(newEvent)))
            }
        }
    }

    private suspend fun handleEndedEvent(event: SlurmEventEnded): Pair<String, HPCAppEvent.Ended> {
        // Some of these queries _must_ resolve. Because of this we throw if they do not resolve.
        // The queries should have built-in retries, so if the instances have not yet replayed the data we will
        // give them a chance to do so before crashing. TODO We might have to tweak this slightly for more
        // events.
        val key = interactiveStore.querySlurmIdToInternal(event.jobId).orThrow()
        val pendingEvent = interactiveStore.queryJobIdToApp(key).orThrow()

        val appParameters = pendingEvent.originalRequest.event.parameters
        val app = with(pendingEvent.originalRequest.event.application) {
            ApplicationDAO.findByNameAndVersion(name, version)
        }!!

        // TODO We need to be able to resolve this in a uniform manner. Since we will need these in multiple places.
        // There will also be some logic in handling optional parameters.
        val outputs = app.parameters
                .filterIsInstance<ApplicationParameter.OutputFile>()
                .map { it.map(appParameters[it.name]!!) }

        val storage = with(pendingEvent.originalRequest.header.performedFor) {
            storageConnectionFactory.createForAccount(username, password)
        }.capture() ?: return Pair(key, HPCAppEvent.UnsuccessfullyCompleted(Error.invalidAuthentication()))

        // TODO We should use a connection pool for this stuff
        // Otherwise we will risk opening and closing a lot of connection, we also risk having a lot of concurrent
        // connections.
        SSHConnection.connect(sshConfig).use { ssh ->
            // Transfer output files
            for (transfer in outputs) {
                val workingDirectory = URI(pendingEvent.workingDirectory)
                val source = workingDirectory.resolve(transfer.source)
                if (!source.path.startsWith(workingDirectory.path)) {
                    log.warn("File ${transfer.source} did not resolve to be within working directory " +
                            "($source versus $workingDirectory). Skipping this file")
                } else {
                    var permissionDenied = false
                    ssh.scpDownload(safeBashArgument(source.path)) {
                        try {
                            storage.files.put(StoragePath.fromURI(transfer.destination), it)
                        } catch (ex: FileNotFoundException) {
                            permissionDenied = true
                        } catch (ex: PermissionException) {
                            permissionDenied = true
                        }
                    }

                    if (permissionDenied) return Pair(key, HPCAppEvent.UnsuccessfullyCompleted(
                            Error.permissionDenied("Could not transfer file to ${transfer.destination}")
                    ))
                }
            }

            // TODO Crashing after deletion but before we send event will cause a lot of problems. We should split
            // this into two.
            ssh.exec("rm -rf ${safeBashArgument(pendingEvent.jobDirectory)}") {}
        }

        return Pair(key, HPCAppEvent.SuccessfullyCompleted(event.jobId))
    }
}