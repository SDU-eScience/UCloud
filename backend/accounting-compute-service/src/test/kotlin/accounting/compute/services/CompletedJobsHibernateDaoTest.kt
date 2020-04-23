package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.util.withDatabase
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import org.junit.Test
import kotlin.test.assertEquals

class CompletedJobsHibernateDaoTest {

    private val dummyEvent = AccountingJobCompletedEvent(
        NameAndVersion("foo", "1.0.0"),
        1,
        SimpleDuration(1, 0, 0),
        "user",
        "job-id",
        MachineReservation.BURST,
        null,
        System.currentTimeMillis()
    )

    @Test
    fun `insert single, list and compute usage`() {
        withDatabase { db ->
            val dao = CompletedJobsDao()
            val service = CompletedJobsService(db, dao)

            service.insert(dummyEvent)

            val usage = service.computeUsage(ComputeUser.User(dummyEvent.startedBy))
            assertEquals(dummyEvent.totalDuration.toMillis(), usage)
        }
    }

    @Test
    fun `multiple insert of same`() {
        withDatabase { db ->
            val dao = CompletedJobsDao()
            val service = CompletedJobsService(db, dao)

            val events = (0 until 10).map { dummyEvent }
            service.insertBatch(events)
        }
    }
}
