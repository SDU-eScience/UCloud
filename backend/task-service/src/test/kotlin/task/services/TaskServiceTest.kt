package dk.sdu.cloud.task.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.task.api.TaskServiceDescription
import dk.sdu.cloud.task.api.TaskUpdate
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class TaskServiceTest {

    companion object {
        private lateinit var embDb: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db, embDb) = TestDB.from(TaskServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun after() {
            runBlocking {
                db.close()
            }
            embDb.close()
        }
    }

    internal fun truncate() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        TRUNCATE tasks
                    """.trimIndent()
                )
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        truncate()
    }

    @AfterTest
    fun afterTest() {
        truncate()
    }

    @Test
    fun `create, post, complete without priv`() {
        val taskDao = TaskAsyncDao()
        val subscriptionService = mockk<SubscriptionService>()
        val service = TaskService(db, taskDao, subscriptionService)
        runBlocking {
            try {
                service.create(TestUsers.user, "title", "status", TestUsers.user.username)
            } catch (ex: RPCException) {
                assertEquals(HttpStatusCode.Forbidden, ex.httpStatusCode)
            }
            try {
                service.postStatus(TestUsers.user, TaskUpdate("jobID"))
            } catch (ex: RPCException) {
                assertEquals(HttpStatusCode.Forbidden, ex.httpStatusCode)
            }
            try {
                service.markAsComplete(TestUsers.user, "jobID")
            } catch (ex: RPCException) {
                assertEquals(HttpStatusCode.Forbidden, ex.httpStatusCode)
            }
        }
    }

    @Test
    fun `create find and update test`() {
        val taskDao = TaskAsyncDao()
        val subscriptionService = mockk<SubscriptionService>()
        val service = TaskService(db, taskDao, subscriptionService)
        val serviceUser = TestUsers.service
        val user = TestUsers.user

        coEvery { subscriptionService.onTaskUpdate(any(), any()) } just Runs

        runBlocking {
            val job1 = service.create(serviceUser, "title", "status", user.username)
            val job2 = service.create(serviceUser, "title", "status", user.username)
            val list = service.list(user, NormalizedPaginationRequest(10,0))
            assertEquals(2, list.itemsInTotal)
            assertEquals(job1.jobId, list.items.first().jobId)
            assertEquals(job2.jobId, list.items.last().jobId)

            service.markAsComplete(serviceUser, job1.jobId)
            val listAfterComplete = service.list(user, NormalizedPaginationRequest(10,0))
            assertEquals(1, listAfterComplete.itemsInTotal)
            assertEquals(job2.jobId, listAfterComplete.items.first().jobId)

        }
    }

}
