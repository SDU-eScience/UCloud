package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.mockk.every
import io.mockk.spyk
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneTimeTokenDaoTest{

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `claim test`() {
        withDatabase { db ->
            val ott = OneTimeTokenHibernateDAO()
            val session = db.openSession()
            val returnValue = ott.claim(session, "jti", "claimedByMe")
            assertTrue(returnValue)


        }
    }

    @Test
    fun `claim test - with exceptiion`() {
        withDatabase { db ->
            val ott = OneTimeTokenHibernateDAO()
            val session = spyk(db.openSession())
            every { session.save(any()) } answers {
                throw Exception()
            }
            val returnValue = ott.claim(session, "jti", "claimedByMe")
            assertFalse(returnValue)
        }
    }
}