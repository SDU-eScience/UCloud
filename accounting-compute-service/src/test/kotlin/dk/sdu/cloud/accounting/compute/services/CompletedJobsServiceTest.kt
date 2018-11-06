package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.testUtils.withDatabase
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import org.junit.Test
import kotlin.test.assertEquals

class CompletedJobsServiceTest{

    private val dummyEvent = AccountingJobCompletedEvent(
        NameAndVersion("foo", "1.0.0"),
        1,
        SimpleDuration(1, 0, 0),
        "user",
        "job-id",
        System.currentTimeMillis()
    )

    @Test
    fun `Compute Billable Items test`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val events = (0 until 10).map { dummyEvent }
            service.insertBatch(events)

            run {
                val billableItems = service.computeBillableItems(1, System.currentTimeMillis(), "user")
                assertEquals(60, billableItems.first().units)
            }

            run {
                val billableItems = service.computeBillableItems(1, 1, "user")
                assertEquals(0, billableItems.first().units)
            }
        }
    }
}
