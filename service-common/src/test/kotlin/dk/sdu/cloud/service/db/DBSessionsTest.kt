package dk.sdu.cloud.service.db

import kotlinx.coroutines.runBlocking
import org.junit.Test

class DBSessionsTest {

    @Test //Unnecessary to test since it does not do anything, but gives coverage
    fun `test fake DBSessionFactory`() = runBlocking {
        val fdbs = FakeDBSessionFactory
        fdbs.openSession()
        fdbs.openTransaction(Unit)
        fdbs.commit(Unit)
        fdbs.close()
        fdbs.closeSession(Unit)
    }
}
