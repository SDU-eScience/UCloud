package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.every
import io.mockk.spyk
import org.h2.engine.Session
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneTimeTokenDaoTest{

    @Test
    fun `claim test`() {
        withDatabase { db ->
            db.withTransaction {
                val ott = OneTimeTokenHibernateDAO()
                val returnValue = ott.claim(it, "jti", "claimedByMe")
                assertTrue(returnValue)
            }
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

    @Test
    fun `create OTTBlacklistEntity`() {
        val ott = OTTBlackListEntity("jti", "claimedBy")
        assertEquals("jti", ott.jti )
        assertEquals("claimedBy", ott.claimedBy)
    }
}