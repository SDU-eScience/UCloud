package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

class OneTimeTokenDaoTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(AuthServiceDescription)
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
    fun before() {
        dbTruncate(db)
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    @Test
    fun `claim test`() {
        runBlocking {
            db.withSession {
                val ott = OneTimeTokenAsyncDAO()
                val returnValue = ott.claim(it, "jti", "claimedByMe")
                assertTrue(returnValue)
            }
        }
    }
    /*
    @Test
    fun `claim test - with exception`() {
        runBlocking {
            val ott = OneTimeTokenAsyncDAO()
            val session = runBlocking { spyk(db.openSession()) }
            coEvery { session.insert(any<SQLTable>(), any()) } answers {
                throw Exception()
            }
            val returnValue = ott.claim(session, "jti", "claimedByMe")
            assertFalse(returnValue)
        }
    }*/
}
