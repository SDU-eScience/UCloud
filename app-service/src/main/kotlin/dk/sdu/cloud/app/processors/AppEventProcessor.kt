package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.api.HPCAppEvent
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.services.SlurmPollAgent
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.filterIsInstance
import dk.sdu.cloud.service.stackTraceToString
import org.apache.kafka.streams.kstream.KStream
import org.h2.jdbc.JdbcSQLException
import org.slf4j.LoggerFactory

class AppEventProcessor(
    private val appEvents: KStream<String, HPCAppEvent>,
    private val jobService: JobService,
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
                    jobService.createJob(
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
                } catch (ex: JdbcSQLException) { // TODO Should catch these in service layer?
                    log.warn("Exception while handling PENDING event! systemId=$systemId")
                    log.warn(ex.stackTraceToString())
                }
            }
        }

        // System job systemId to app
        appEvents.foreach { key, value ->
            log.info("Updating status for job with systemId=$key: $value")
            slurmPollAgent.handle(value)
            jobService.updateJobBySystemId(key, value.toJobStatus(), System.currentTimeMillis())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.AppEventProcessor")
    }
}
