package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.downtime.management.api.DowntimeManagementServiceDescription
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class DowntimeServiceTest {
    companion object {
        private lateinit var db: AsyncDBSessionFactory
        private lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db,embDB) = TestDB.from(DowntimeManagementServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun beforeEach() {
        truncate()
    }

    @AfterTest
    fun afterEach() {
        truncate()
    }

    private fun truncate() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        TRUNCATE downtimes
                    """.trimIndent()
                )
            }
        }
    }

    private val user = TestUsers.user
    private val defaultPaginationRequest = NormalizedPaginationRequest(25, 0)

    @Test
    fun `Create downtime`() {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        runBlocking {
            service.add(TestUsers.admin, DowntimeWithoutId(Time.now() - 1, Time.now() + 1, "This is some text"))
            assertEquals(1, service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal)
        }

    }

    @Test
    fun `Remove downtime`() {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        runBlocking {
            service.add(TestUsers.admin, DowntimeWithoutId(Time.now() - 100, Time.now() + 100, "Text for the weary soul."))
            assert(service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal == 1)
            val id = service.listAll(TestUsers.admin, defaultPaginationRequest).items[0].id
            service.remove(TestUsers.admin, id)
            assert(service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal == 0)
        }
    }

    // Is this necessary? We do test the function DowntimeManagementDescriptions#listAll in almost every other test.
    @Test
    fun `List all downtimes`() = runBlocking {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        (0..99).forEach { i ->
            service.add(TestUsers.admin, DowntimeWithoutId(Time.now(), Time.now(), "$i"))
        }
        assert(service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal == 100)
    }

    @Test
    fun `List pending downtimes`() = runBlocking {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        (0..99).forEach { i ->
            service.add(TestUsers.admin, DowntimeWithoutId(Time.now() - i, Time.now() - i, "$i"))
        }
        service.add(TestUsers.admin, DowntimeWithoutId(Time.now() + 5000, Time.now() + 50000, "Hello"))
        assert(service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal == 101)
        Thread.sleep(1000)
        val result = service.listPending(defaultPaginationRequest)
        assertEquals(1, result.itemsInTotal)
    }

    @Test
    fun `Remove expired downtimes`() = runBlocking {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        (0..99).forEach { i ->
            service.add(TestUsers.admin, DowntimeWithoutId(Time.now() - i, Time.now() - i, "$i"))
        }
        service.add(TestUsers.admin, DowntimeWithoutId(Time.now() + 5000, Time.now() + 50000, "Hello"))
        assert(service.listAll(TestUsers.admin, defaultPaginationRequest).itemsInTotal == 101)
        Thread.sleep(3000)
        service.removeExpired(TestUsers.admin)
        val all = service.listAll(TestUsers.admin, defaultPaginationRequest)
        assertEquals(1, all.itemsInTotal)
    }

    @Test
    fun `Get downtime by id`() = runBlocking {
        val dao = DowntimeDao()
        val service = DowntimeManagementService(db, dao)
        (0..9).forEach { i ->
            service.add(TestUsers.admin, DowntimeWithoutId(i.toLong(), i.toLong(), "$i"))
        }

        val downtime = service.listAll(TestUsers.admin, defaultPaginationRequest).items[5]

        assert(downtime == service.getById(downtime.id))
    }
}
