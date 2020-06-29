package dk.sdu.cloud.task.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.task.api.TaskServiceDescription
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskAsyncDaoTest {
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
    fun `test all dao`() {
        val dao = TaskAsyncDao()
        val user = TestUsers.user
        val processor = TestUsers.service
        runBlocking {
            val paging = NormalizedPaginationRequest(10,0)
            val taskId1 = dao.create(db, "title", "initStatus", user.username, processor)
            val taskId2 = dao.create(db, "title2", "initStatus2", user.username, processor)
            val taskId3 = dao.create(db, "title3", "initStatus3", "AnotherUser", processor)

            val found = dao.findOrNull(db, taskId3, user.username)
            assertNotNull(found)
            assertEquals("title3", found.title)

            val list1 = dao.list(db, paging, user)
            assertEquals(2, list1.itemsInTotal)
            assertEquals(taskId1, list1.items.first().jobId)
            assertEquals("title2", list1.items.last().title)
            assertEquals("initStatus2", list1.items.last().status)

            dao.updateStatus(db, taskId2, "newStatus", user.username)

            val list2 = dao.list(db, paging, user)

            assertEquals(2, list2.itemsInTotal)
            assertEquals("newStatus", list2.items.last().status)
            val modTime = list2.items.last().modifiedAt

            Thread.sleep(3000)
            dao.updateLastPing(db, taskId2, processor)
            val found2 = dao.findOrNull(db, taskId2, user.username)
            assertNotNull(found2)
            assertTrue(found2.modifiedAt > modTime)

            dao.markAsComplete(db, taskId1, processor)
            val lastList = dao.list(db, paging, user)
            assertEquals(1, lastList.itemsInTotal)
            assertEquals(taskId2, lastList.items.first().jobId)
        }
    }

}
