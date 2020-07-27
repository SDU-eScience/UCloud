package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.accounting.utils.insertAll
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class WalletServiceTest {

    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db,embDb) = TestDB.from(AccountingServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDb.close()
        }

    }

    fun truncateAccountingDB() {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        TRUNCATE 
                            wallets,
                            product_categories,
                            products,
                            accounting.job_completed_events,
                            transactions
                    """
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncateAccountingDB()
        runBlocking {
            insertAll(db)
        }
    }

    @AfterTest
    fun after() {
        truncateAccountingDB()
    }

    @Test
    fun `test this`() {

    }


}
