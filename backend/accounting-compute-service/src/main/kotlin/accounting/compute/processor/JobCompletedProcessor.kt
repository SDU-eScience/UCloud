package dk.sdu.cloud.accounting.compute.processor

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.app.orchestrator.api.JobCompletedEvent
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.app.orchestrator.api.AccountingEvents as JobAccountingEvents

class JobCompletedProcessor(
    private val eventConsumerFactory: EventStreamService,
    private val completedJobsService: CompletedJobsService
) {
    fun init() {
        eventConsumerFactory.subscribe(JobAccountingEvents.jobCompleted, EventConsumer.Batched(
            maxLatency = 500,
            maxBatchSize = 250
        ) { batch ->
            val events = normalizeEvents(batch)
            val accountingEvents = events.map { event ->
                AccountingJobCompletedEvent(
                    event.application,
                    event.nodes,
                    event.duration,
                    event.jobOwner,
                    event.jobId,
                    event.reservation ?: MachineReservation.BURST,
                    event.project,
                    event.jobCompletedAt
                )
            }

            completedJobsService.insertBatch(accountingEvents)
        })
    }

    /**
     * Normalizes project users to their normalized user (without the suffix)
     */
    private fun normalizeEvents(events: List<JobCompletedEvent>): List<JobCompletedEvent> {
        return events.map { event ->
            val owner = event.jobOwner
            event.copy(jobOwner = owner)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
