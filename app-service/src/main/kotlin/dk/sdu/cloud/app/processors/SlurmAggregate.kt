package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.api.HPCAppEvent
import dk.sdu.cloud.app.services.HPCStreamService
import dk.sdu.cloud.app.services.JobsDAO
import dk.sdu.cloud.app.services.SlurmPollAgent
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.filterIsInstance
import org.h2.jdbc.JdbcSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.SlurmAggregate")

class SlurmAggregate(
        private val streamServices: HPCStreamService,
        private val slurmPollAgent: SlurmPollAgent
) {
    fun init() {
        val pendingEvents = streamServices.appEvents.filterIsInstance(HPCAppEvent.Pending::class)

        // Save slurm ID to system ID
        pendingEvents.foreach { systemId, value ->
            val slurmId = value.jobId
            val validated = TokenValidation.validateOrNull(value.originalRequest.header.performedFor)
            if (validated != null) {
                try {
                    transaction {
                        JobsDAO.createJob(
                                systemId,
                                validated.subject,
                                slurmId,
                                value.originalRequest.event.application.name,
                                value.originalRequest.event.application.version,
                                value.workingDirectory,
                                value.jobDirectory,
                                value.originalRequest.event.parameters
                        )
                    }
                } catch (ex: JdbcSQLException) {
                    // TODO
                    log.warn("We should really catch this correctly")
                }
            }
        }

        // System job systemId to app
        streamServices.appEvents.foreach { key, value ->
            slurmPollAgent.handle(value)
            transaction {
                JobsDAO.updateJobBySystemId(key, value.toJobStatus())
            }
        }
    }
}
