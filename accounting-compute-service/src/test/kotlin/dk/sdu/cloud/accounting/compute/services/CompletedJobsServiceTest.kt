package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.util.withDatabase
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.service.test.ClientMock
import org.junit.Test
import kotlin.test.assertEquals

class CompletedJobsServiceTest {

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
            val service = CompletedJobsService(db, dao, ClientMock.authenticatedClient)

            val events = (0 until 10).map { dummyEvent }
            service.insertBatch(events)

            run {
                val billableItems = service.computeBillableItems(1, System.currentTimeMillis(), "user", Role.USER)
                assertEquals(60, billableItems.first().units)
            }

            run {
                val billableItems = service.computeBillableItems(1, 1, "user", Role.USER)
                assertEquals(0, billableItems.first().units)
            }
        }
    }
}
