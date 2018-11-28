package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.api.ContextQueryImpl
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.util.withDatabase
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.NormalizedPaginationRequest
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletedJobsHibernateDaoTest {

    private val dummyEvent = AccountingJobCompletedEvent(
        NameAndVersion("foo", "1.0.0"),
        1,
        SimpleDuration(1, 0, 0),
        "user",
        "job-id",
        System.currentTimeMillis()
    )

    @Test
    fun `insert single, list and compute usage`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            service.insert(dummyEvent)

            val listedEvents = service.listEvents(
                NormalizedPaginationRequest(null, null),
                ContextQueryImpl(user="user1"),
                dummyEvent.startedBy
            )

            assertEquals(1, listedEvents.itemsInTotal)
            assertEquals(1, listedEvents.items.size)

            val event = listedEvents.items.single()
            assertEquals(dummyEvent, event)

            val usage = service.computeUsage(ContextQueryImpl(user = "user1"), dummyEvent.startedBy)
            assertEquals(dummyEvent.totalDuration.toMillis(), usage)
        }
    }

    @Test
    fun `multiple insert of same`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val events = (0 until 10).map { dummyEvent }
            service.insertBatch(events)

            val listedEvents = service.listEvents(
                NormalizedPaginationRequest(null, null),
                ContextQueryImpl(user = "user1"),
                dummyEvent.startedBy
            )

            assertEquals(1, listedEvents.itemsInTotal)
            assertEquals(1, listedEvents.items.size)

            val event = listedEvents.items.single()
            assertEquals(dummyEvent, event)
        }
    }

    @Test
    fun `insert multiple for same user and list all`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val count = 10
            val events = (0 until count).map { idx ->
                dummyEvent.copy(jobId = "$idx")
            }

            service.insertBatch(events)

            val listedEvents = service.listEvents(
                NormalizedPaginationRequest(null, null),
                ContextQueryImpl(user = "user1"),
                dummyEvent.startedBy
            )

            assertEquals(count, listedEvents.itemsInTotal)
            assertEquals(count, listedEvents.items.size)

            run {
                val expectedJobIds = events.asSequence().map { it.jobId }.toSet()
                val actualJobIds = listedEvents.items.asSequence().map { it.jobId }.toSet()
                assertEquals(expectedJobIds, actualJobIds)
            }

            run {
                val expectedUsage = dummyEvent.totalDuration.toMillis() * 10
                val actualUsage = service.computeUsage(ContextQueryImpl(user = "user1"), dummyEvent.startedBy)
                assertEquals(expectedUsage, actualUsage)
            }

            run {
                val allevents = service.listAllEvents(ContextQueryImpl(since = 1, user = "user1"), dummyEvent.startedBy)
                assertEquals(count, allevents.size)
            }
        }
    }

    @Test
    fun `insert multiple for different users`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val userCount = 3
            val entryCount = 5

            fun username(id: Int) = "user-$id"

            val allEvents = (0 until userCount).flatMap { userId ->
                (0 until entryCount).map {
                    dummyEvent.copy(
                        startedBy = username(userId),
                        jobId = UUID.randomUUID().toString()
                    )
                }
            }

            service.insertBatch(allEvents)

            (0 until userCount).forEach { userId ->
                val events = service.listEvents(
                    NormalizedPaginationRequest(null, null),
                    ContextQueryImpl(user = "user1"),
                    username(userId)
                )

                assertEquals(entryCount, events.itemsInTotal)
                assertEquals(entryCount, events.items.size)
                assertTrue { events.items.all { it.startedBy == username(userId) } }
            }

        }
    }

    @Test
    fun `test since query`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val eventCount = 5

            val allEvents = (0 until eventCount).map {
                dummyEvent.copy(
                    timestamp = it.toLong(),
                    jobId = UUID.randomUUID().toString()
                )
            }
            service.insertBatch(allEvents)

            (0 until eventCount).forEach { id ->
                val events = service.listEvents(
                    NormalizedPaginationRequest(null, null),
                    ContextQueryImpl(since = id.toLong(), user = "user1"),
                    dummyEvent.startedBy
                )

                assertEquals(eventCount - id, events.itemsInTotal)
                assertEquals(eventCount - id, events.items.size)
                assertTrue { events.items.all { it.timestamp >= id } }
            }
        }
    }

    @Test
    fun `test until query`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val eventCount = 5

            val allEvents = (0 until eventCount).map {
                dummyEvent.copy(
                    timestamp = it.toLong(),
                    jobId = UUID.randomUUID().toString()
                )
            }
            service.insertBatch(allEvents)

            (0 until eventCount).forEach { id ->
                val events = service.listEvents(
                    NormalizedPaginationRequest(null, null),
                    ContextQueryImpl(until = id.toLong(), user = "user1"),
                    dummyEvent.startedBy
                )

                assertEquals(id + 1, events.itemsInTotal)
                assertEquals(id + 1, events.items.size)
                assertTrue { events.items.all { it.timestamp <= id } }
            }
        }
    }

    @Test
    fun `test between query`() {
        withDatabase { db ->
            val dao = CompletedJobsHibernateDao()
            val service = CompletedJobsService(db, dao)

            val eventCount = 5

            val allEvents = (0 until eventCount).map {
                dummyEvent.copy(
                    timestamp = it.toLong(),
                    jobId = UUID.randomUUID().toString()
                )
            }
            service.insertBatch(allEvents)

            val events = service.listEvents(
                NormalizedPaginationRequest(null, null),
                ContextQueryImpl(since = 1, until = 3, user = "user1"),
                dummyEvent.startedBy
            )

            assertEquals(3, events.itemsInTotal)
            assertEquals(3, events.items.size)
            assertTrue { events.items.all { it.timestamp in 1..3 } }
        }
    }
}
