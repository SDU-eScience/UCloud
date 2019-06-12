package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class JobHibernateDaoTest {
    private val user = "User1"
    private val systemId = UUID.randomUUID().toString()
    private val appName = normAppDesc.metadata.name
    private val version = normAppDesc.metadata.version

    @Test(expected = JobException.NotFound::class)
    fun `update status - not found tests`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDao(appDao, toolDao, tokenValidation)

        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStatus(it, systemId, "good")
        }
    }

    @Test(expected = JobException.NotFound::class)
    fun `update status and statue - not found tests`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDao(appDao, toolDao, tokenValidation)

        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStateAndStatus(it, systemId, JobState.PREPARED, "good")
        }
    }

    @Test
    fun `create, find and update jobinfo test`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDao(appDao, toolDao, tokenValidation)

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
                    "Unknown",
                    archiveInCollection = app.metadata.title,
                    uid = 1337L
                ),
                "token"
            )
            jobHibDao.create(it, jobWithToken)
            println("JOB: $jobWithToken")
        }

        db.withTransaction(autoFlush = true) {
            val result = jobHibDao.updateStatus(it, systemId, "good")
        }

        db.withTransaction(autoFlush = true) {
            val result = jobHibDao.findOrNull(it, systemId, user)
            assertEquals(JobState.VALIDATED, result?.job?.currentState)
            assertEquals("good", result?.job?.status)
        }

        db.withTransaction(autoFlush = true) {
            runBlocking {
                val result = jobHibDao.findJobsCreatedBefore(it, System.currentTimeMillis() + 5000).toList()
                assertEquals(1, result.size)
            }
        }

        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStateAndStatus(it, systemId, JobState.SUCCESS, "better")
        }

        db.withTransaction(autoFlush = true) {
            val result = jobHibDao.findOrNull(it, systemId, user)
            assertEquals(JobState.SUCCESS, result?.job?.currentState)
            assertEquals("better", result?.job?.status)
        }

        db.withTransaction(autoFlush = true) {
            val result = jobHibDao.list(it, user, NormalizedPaginationRequest(10, 0))
            assertEquals(1, result.itemsInTotal)
        }
    }
}
