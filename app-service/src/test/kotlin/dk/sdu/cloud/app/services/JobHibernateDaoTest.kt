package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class JobHibernateDaoTest {
    private val user = "User1"
    private val systemId = UUID.randomUUID().toString()
    private val appName = normAppDesc.metadata.name
    private val version = normAppDesc.metadata.version

    @Test
    fun `create, find and update jobinfo test`() {
        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDao(appDao, toolDao)

        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        db.withTransaction(autoFlush = true) {
            toolDao.create(it, user, normToolDesc)
            appDao.create(it, user, normAppDesc)
        }

        db.withTransaction(autoFlush = true) {
            val app = appDao.findByNameAndVersion(it, null, appName, version)
            val jobWithToken = VerifiedJobWithAccessToken(
                VerifiedJob(
                    app,
                    emptyList(),
                    systemId,
                    user,
                    1,
                    1,
                    SimpleDuration(0, 1, 0),
                    VerifiedJobInput(emptyMap()),
                    "abacus",
                    JobState.VALIDATED,
                    "Unknown"
                ),
                "token"
            )
            jobHibDao.create(it, jobWithToken)
        }

        db.withTransaction(autoFlush = true) {
            val result = jobHibDao.findOrNull(it, systemId, user)
            assertEquals(JobState.VALIDATED, result?.job?.currentState)
        }

        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStateAndStatus(it, systemId, JobState.SUCCESS)
        }

        db.withTransaction(autoFlush = true) {
            val result2 = jobHibDao.findOrNull(it, systemId, user)
            assertEquals(JobState.SUCCESS, result2?.job?.currentState)
        }
    }
}
