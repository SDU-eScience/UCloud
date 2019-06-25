package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJobInput
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.orchestrator.utils.normTool
import dk.sdu.cloud.app.orchestrator.utils.normToolDesc
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class JobHibernateDaoTest {
    private val user = TestUsers.user.copy(username = "User1")
    private val systemId = UUID.randomUUID().toString()
    private val appName = normAppDesc.metadata.name
    private val version = normAppDesc.metadata.version

    private val toolDao: ToolStoreService = mockk()
    private val appDao: AppStoreService = mockk()
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var jobHibDao: JobHibernateDao

    @BeforeTest
    fun beforeTest() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        jobHibDao = JobHibernateDao(appDao, toolDao, tokenValidation)
    }

    @Test(expected = JobException.NotFound::class)
    fun `update status - not found tests`() {
        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStatus(it, systemId, "good")
        }
    }

    @Test(expected = JobException.NotFound::class)
    fun `update status and statue - not found tests`() {
        db.withTransaction(autoFlush = true) {
            jobHibDao.updateStateAndStatus(it, systemId, JobState.PREPARED, "good")
        }
    }

    @Test
    fun `create, find and update jobinfo test`() {
        coEvery { toolDao.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version) } returns normTool
        coEvery {
            appDao.findByNameAndVersion(
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )
        } returns normAppDesc

        db.withTransaction(autoFlush = true) {
            val jobWithToken = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc,
                    emptyList(),
                    systemId,
                    user.username,
                    1,
                    1,
                    SimpleDuration(0, 1, 0),
                    VerifiedJobInput(emptyMap()),
                    "abacus",
                    JobState.VALIDATED,
                    "Unknown",
                    archiveInCollection = normAppDesc.metadata.title,
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
            val result = runBlocking { jobHibDao.findOrNull(it, systemId, user.createToken()) }
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
            val result = runBlocking { jobHibDao.findOrNull(it, systemId, user.createToken()) }
            assertEquals(JobState.SUCCESS, result?.job?.currentState)
            assertEquals("better", result?.job?.status)
        }

        db.withTransaction(autoFlush = true) {
            val result =
                runBlocking { jobHibDao.list(it, user.createToken(), NormalizedPaginationRequest(10, 0), null) }
            assertEquals(1, result.itemsInTotal)
        }
    }
}

fun SecurityPrincipal.createToken(): SecurityPrincipalToken = SecurityPrincipalToken(
    this,
    listOf(SecurityScope.ALL_WRITE),
    0,
    Long.MAX_VALUE,
    null
)
