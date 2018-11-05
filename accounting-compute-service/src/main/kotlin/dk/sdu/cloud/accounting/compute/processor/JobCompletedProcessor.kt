package dk.sdu.cloud.accounting.compute.processor

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import dk.sdu.cloud.app.api.AccountingEvents as JobAccountingEvents

class JobCompletedProcessor<DBSession>(
    private val eventConsumerFactory: EventConsumerFactory,
    private val completedJobsService: CompletedJobsService<DBSession>,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(JobAccountingEvents.jobCompleted).configure { root ->
            root
                .batched(
                    batchTimeout = 500,
                    maxBatchSize = 1000
                )
                .consumeBatchAndCommit { batch ->
                    // Actually consume event.
                    val accountingEvents = batch.map { (_, event) ->
                        AccountingJobCompletedEvent(
                            event.application,
                            event.nodes,
                            event.duration,
                            event.jobOwner,
                            event.jobId,
                            event.jobCompletedAt
                        )
                    }

                    completedJobsService.insertBatch(accountingEvents)
                }
        }
    }
}
