package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.Utils.withDatabase
import dk.sdu.cloud.app.abacus.services.JobException
import dk.sdu.cloud.app.abacus.services.JobHibernateDao
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JobHibernateDaoTest{

    @Test
    fun `test Hibernate Dao insert and retrieve`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val jobHibernate = JobHibernateDao()
                jobHibernate.insertMapping(session, "2", 1)

                val resolvedSlurmIDResult = jobHibernate.resolveSlurmId(session, "2")
                assertEquals(1, resolvedSlurmIDResult)

                val resolvedSystemIDResult = jobHibernate.resolveSystemId(session, 1)
                assertEquals("2", resolvedSystemIDResult)
            }
        }
    }

    @Test
    fun `resolve non existing slurmID`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val jobHibernate = JobHibernateDao()
                val resolvedSlurmIDResult = jobHibernate.resolveSlurmId(session, "1")
                val resolvedSystemIDResult = jobHibernate.resolveSystemId(session, 1)

                assertNull(resolvedSlurmIDResult)
                assertNull(resolvedSystemIDResult)
            }
        }
    }

    @Test (expected = JobException.NotUniqueID::class)
    fun `test Hibernate Dao - insert duplicate `() {
        withDatabase { db ->
            db.withTransaction { session ->
            val jobHibernate = JobHibernateDao()
            jobHibernate.insertMapping(session, "1", 1)
            jobHibernate.insertMapping(session, "1", 1)
            }
        }
    }
}
