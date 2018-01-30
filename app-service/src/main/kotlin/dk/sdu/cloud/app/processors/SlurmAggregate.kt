package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.api.HPCAppEvent
import dk.sdu.cloud.app.services.JobsDAO
import dk.sdu.cloud.app.services.SlurmPollAgent
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.filterIsInstance
import org.apache.kafka.streams.kstream.KStream
import org.h2.jdbc.JdbcSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class SlurmAggregate(
        private val appEvents: KStream<String, HPCAppEvent>,
        private val slurmPollAgent: SlurmPollAgent
) {
    fun init() {
        val pendingEvents = appEvents.filterIsInstance(HPCAppEvent.Pending::class)

        // Save slurm ID to system ID
        pendingEvents.foreach { systemId, value ->
            log.info("Handling PENDING event for slurmId=${value.jobId} with systemId=$systemId")

            val slurmId = value.jobId
            val validated = TokenValidation.validateOrNull(value.originalRequest.header.performedFor)
            if (validated != null) {
                try {
                    transaction {
                        // TODO Created at is incorrect, should extract from event
                        JobsDAO.createJob(
                                systemId,
                                validated.subject,
                                slurmId,
                                value.originalRequest.event.application.name,
                                value.originalRequest.event.application.version,
                                value.workingDirectory,
                                value.jobDirectory,
                                value.originalRequest.event.parameters,
                                System.currentTimeMillis()
                        )
                    }
                } catch (ex: JdbcSQLException) {
                    // TODO
                    log.warn("We should really catch this correctly")
                }
            }
        }

        // System job systemId to app
        appEvents.foreach { key, value ->
            log.info("Updating status for job with systemId=$key: $value")
            slurmPollAgent.handle(value)
            transaction {
                // TODO Modified at incorrect, should extract from event
                JobsDAO.updateJobBySystemId(key, value.toJobStatus(), System.currentTimeMillis())
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.SlurmAggregate")
    }
}
