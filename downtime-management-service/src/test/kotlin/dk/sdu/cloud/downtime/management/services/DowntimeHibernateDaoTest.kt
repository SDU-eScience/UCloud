package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*

class DowntimeHibernateDaoTest {
    private val user = TestUsers.user
    private val defaultPaginationRequest = NormalizedPaginationRequest(25, 0)

    private fun initMicroAndGetDb(): HibernateSessionFactory {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        return micro.hibernateDatabase
    }

    @Test
    fun `Create downtime`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            dao.add(session, DowntimeWithoutId(Date().time - 1, Date().time + 1, "This is some text"))
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 1)
        }
    }

    @Test
    fun `Remove downtime`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            dao.add(session, DowntimeWithoutId(Date().time - 100, Date().time + 100, "Text for the weary soul."))
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 1)
            val id = dao.listAll(session, defaultPaginationRequest).items[0].id
            dao.remove(session, id)
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 0)
        }
    }

    // Is this necessary? We do test the function DowntimeManagementDescriptions#listAll in almost every other test.
    @Test
    fun `List all downtimes`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            (0..99).forEach { i ->
                dao.add(session, DowntimeWithoutId(Date().time, Date().time, "$i"))
            }
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 100)
        }
    }

    @Test
    fun `List pending downtimes`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            (0..99).forEach { i ->
                dao.add(session, DowntimeWithoutId(Date().time - i, Date().time - i, "$i"))
            }
            dao.add(session, DowntimeWithoutId(Date().time + 500, Date().time + 5000, "Hello"))
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 101)
            assert(dao.listPending(session, defaultPaginationRequest).itemsInTotal == 1)
        }
    }

    @Test
    fun `Remove expired downtimes`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            (0..99).forEach { i ->
                dao.add(session, DowntimeWithoutId(Date().time - i, Date().time - i, "$i"))
            }
            dao.add(session, DowntimeWithoutId(Date().time + 500, Date().time + 5000, "Hello"))
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 101)
            dao.removeExpired(session, user)
            assert(dao.listAll(session, defaultPaginationRequest).itemsInTotal == 1)
        }
    }

    @Test
    fun `Get downtime by id`() = runBlocking {
        val db = initMicroAndGetDb()
        val dao = DowntimeHibernateDao()
        db.withTransaction { session ->
            (0..9).forEach { i ->
                dao.add(session, DowntimeWithoutId(i.toLong(), i.toLong(), "$i"))
            }

            val downtime = dao.listAll(session, defaultPaginationRequest).items[5]

            assert(downtime == dao.getById(session, downtime.id))
        }
    }
}
