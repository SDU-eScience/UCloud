package dk.sdu.cloud.task.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.task.api.CreateRequest
import dk.sdu.cloud.task.api.ListResponse
import dk.sdu.cloud.task.api.Task
import dk.sdu.cloud.task.api.TaskServiceDescription
import dk.sdu.cloud.task.rpc.TaskController
import dk.sdu.cloud.task.services.SubscriptionService
import dk.sdu.cloud.task.services.TaskAsyncDao
import dk.sdu.cloud.task.services.TaskService
import io.ktor.http.HttpMethod
import io.mockk.Runs
import io.mockk.coEvery
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

class TaskControllerTest {

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

    private fun truncate() {
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
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val subscription = mockk<SubscriptionService>()
        coEvery { subscription.onTaskUpdate(any(), any()) } just Runs
        val dao = TaskAsyncDao()
        val taskService = TaskService(db, dao, subscription)
        listOf(TaskController(subscription, taskService))
    }

    @Test
    fun `create, find, update test`() {
        withKtorTest(
            setup = setup,
            test = {
                val serviceUser = TestUsers.service
                val user = TestUsers.user
                val task1 = sendJson(
                    method = HttpMethod.Put,
                    path = "/api/tasks",
                    user = serviceUser,
                    request = CreateRequest(
                        "title",
                        user.username,
                        "initStatus"
                    )
                )
                task1.assertSuccess()
                val task1Response = defaultMapper.readValue<Task>(task1.response.content!!)
                val task2 = sendJson(
                    method = HttpMethod.Put,
                    path = "/api/tasks",
                    user = serviceUser,
                    request = CreateRequest(
                        "title2",
                        user.username,
                        "initStatus2"
                    )
                )
                task2.assertSuccess()
                val task2Response = defaultMapper.readValue<Task>(task2.response.content!!)

                val task3 = sendJson(
                    method = HttpMethod.Put,
                    path = "/api/tasks",
                    user = serviceUser,
                    request = CreateRequest(
                        "title3",
                        "NotThisUser",
                        "initStatus3"
                    )
                )
                task3.assertSuccess()

                val listRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/tasks",
                    user = user
                )
                listRequest.assertSuccess()
                val listResponse = defaultMapper.readValue<ListResponse>(listRequest.response.content!!)
                assertEquals(2, listResponse.itemsInTotal)
                assertEquals(task1Response.jobId, listResponse.items.first().jobId)
                assertEquals(task2Response.jobId, listResponse.items.last().jobId)
                assertEquals("initStatus2", listResponse.items.last().status)
                assertEquals("title2", listResponse.items.last().title)

                val task1ID = task1Response.jobId
                val markCompleteRequest = sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/tasks/$task1ID",
                    user = serviceUser
                )
                markCompleteRequest.assertSuccess()

                val listAfterUpdatesRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/tasks",
                    user = user
                )
                listAfterUpdatesRequest.assertSuccess()
                val listAfterResponse = defaultMapper.readValue<ListResponse>(listAfterUpdatesRequest.response.content!!)
                assertEquals(1, listAfterResponse.itemsInTotal)
                assertEquals(task2Response.jobId, listAfterResponse.items.first().jobId)
            }
        )
    }

}
