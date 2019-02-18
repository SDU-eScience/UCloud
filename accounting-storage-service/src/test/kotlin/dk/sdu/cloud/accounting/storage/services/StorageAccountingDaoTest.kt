package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.ContextQueryImpl
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.withDatabase
import org.junit.Test
import kotlin.test.assertEquals

class StorageAccountingDaoTest {

    @Test
    fun `insert and list all Test`() {
        val dao = StorageAccountingHibernateDao()
        withDatabase { db ->
            db.withTransaction {
                dao.insert(it, ServicePrincipal("_user", Role.SERVICE), 12345)
            }
            db.withTransaction {
                val result = dao.findAllList(it, ContextQueryImpl(202020), "_user")
                assertEquals(12345, result.first().bytesUsed)
                assertEquals("_user", result.first().user)
            }
        }
    }

    @Test
    fun `insert and list all Page Test`() {
        val dao = StorageAccountingHibernateDao()
        withDatabase { db ->
            db.withTransaction {
                dao.insert(it, ServicePrincipal("_user", Role.SERVICE), 12345)
            }
            db.withTransaction {
                val result = dao.findAllPage(it, NormalizedPaginationRequest(10, 0), ContextQueryImpl(202020), "_user")
                assertEquals(12345, result.items.first().bytesUsed)
                assertEquals("_user", result.items.first().user)
                assertEquals(0, result.pageNumber)
                assertEquals(10, result.itemsPerPage)
                assertEquals(1, result.itemsInTotal)
            }
        }
    }

    @Test
    fun `insert and list all By user Test`() {
        val dao = StorageAccountingHibernateDao()
        withDatabase { db ->
            db.withTransaction {
                dao.insert(it, ServicePrincipal("_user", Role.SERVICE), 12345)
                dao.insert(it, ServicePrincipal("_user2", Role.SERVICE), 6666)

            }
            db.withTransaction {
                val result = dao.findAllByUserId(it, "_user2", NormalizedPaginationRequest(10, 0))
                assertEquals(6666, result.items.first().bytesUsed)
                assertEquals("_user2", result.items.first().user)
                assertEquals(0, result.pageNumber)
                assertEquals(10, result.itemsPerPage)
                assertEquals(1, result.itemsInTotal)
            }
        }
    }
}
