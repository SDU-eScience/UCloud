package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc2
import dk.sdu.cloud.app.orchestrator.utils.normTool
import dk.sdu.cloud.app.orchestrator.utils.normToolDesc
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
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
import kotlin.math.max
import kotlin.math.min
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

        jobHibDao = JobHibernateDao(appDao, toolDao)

        coEvery { toolDao.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version) } returns normTool
        coEvery {
            appDao.findByNameAndVersion(
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )
        } returns normAppDesc
        coEvery { toolDao.findByNameAndVersion("app", "1.2") } returns normTool
        coEvery {
            appDao.findByNameAndVersion(
                "app",
                "1.2"
            )
        } returns normAppDesc
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

        db.withTransaction(autoFlush = true) {
            val jobWithToken = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc,
                    null,
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
                    null,
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
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
                runBlocking { jobHibDao.list(
                    it,
                    user.createToken(),
                    NormalizedPaginationRequest(10, 0),
                    application = null,
                    version = null
                ) }
            assertEquals(1, result.itemsInTotal)
        }
    }

    @Test
    fun `Add and retrieve jobs based on createdAt, both min and max`() {

        db.withTransaction(autoFlush = true) {
            val firstJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc,
                    null,
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
                    null,
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, firstJob)

            Thread.sleep(10)

            val secondJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc2,
                    null,
                    emptyList(),
                    UUID.randomUUID().toString(),
                    user.username,
                    1,
                    1,
                    SimpleDuration(0, 1, 0),
                    VerifiedJobInput(emptyMap()),
                    "abacus",
                    JobState.VALIDATED,
                    "Unknown",
                    null,
                    archiveInCollection = normAppDesc2.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, secondJob)

        }

        db.withTransaction(autoFlush = true) {
            val result = fetchAllJobsInPage(it)
            assertEquals(2, result.itemsInTotal)
        }

        db.withTransaction(autoFlush = true) {
            val result = fetchAllJobsInPage(it)

            val firstJobCreatedAt = result.items[0].job.createdAt
            val secondJobCreatedAt = result.items[1].job.createdAt

            val firstJob = runBlocking {
                creationRangeListing(it, firstJobCreatedAt, firstJobCreatedAt + 5)
            }

            assertEquals(1, firstJob.itemsInTotal)
            assertEquals(firstJobCreatedAt, firstJob.items.first().job.createdAt)

            val secondJob = runBlocking {
                creationRangeListing(it, secondJobCreatedAt, secondJobCreatedAt + 1)
            }

            assertEquals(1, secondJob.itemsInTotal)
            assertEquals(secondJobCreatedAt, secondJob.items.first().job.createdAt)

            val firstCreation = min(firstJobCreatedAt, secondJobCreatedAt)
            val secondCreation = max(firstJobCreatedAt, secondJobCreatedAt)

            val noJobs = runBlocking {
                creationRangeListing(it, firstCreation + 1, secondCreation - 1)
            }

            assertEquals(0, noJobs.itemsInTotal)
        }
    }

    @Test
    fun `Add and retrieve jobs based on createdAt, either min or max`() {

        db.withTransaction(autoFlush = true) {
            val firstJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc,
                    null,
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
                    null,
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, firstJob)

            Thread.sleep(10)

            val secondJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    normAppDesc2,
                    null,
                    emptyList(),
                    UUID.randomUUID().toString(),
                    user.username,
                    1,
                    1,
                    SimpleDuration(0, 1, 0),
                    VerifiedJobInput(emptyMap()),
                    "abacus",
                    JobState.VALIDATED,
                    "Unknown",
                    null,
                    archiveInCollection = normAppDesc2.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, secondJob)
        }

        db.withTransaction(autoFlush = true) {
            val jobs = fetchAllJobsInPage(it)
            val jobOneCreation = jobs.items.first().job.createdAt
            val jobTwoCreation = jobs.items.last().job.createdAt
            val firstCreatedAt = min(jobOneCreation, jobTwoCreation)
            val secondCreatedAt = max(jobOneCreation, jobTwoCreation)

            val bothLower = runBlocking {
                creationRangeListing(it, firstCreatedAt, null)
            }

            assertEquals(2, bothLower.itemsInTotal)

            val oneLower = runBlocking {
                creationRangeListing(it, firstCreatedAt + 1, null)
            }

            assertEquals(1, oneLower.itemsInTotal)

            val noneLower = runBlocking {
                creationRangeListing(it, secondCreatedAt + 1, null)
            }

            assertEquals(0, noneLower.itemsInTotal)

            val bothUpper = runBlocking {
                creationRangeListing(it, null, secondCreatedAt)
            }

            assertEquals(2, bothUpper.itemsInTotal)

            val oneUpper = runBlocking {
                creationRangeListing(it, null, secondCreatedAt - 1)
            }

            assertEquals(1, oneUpper.itemsInTotal)

            val noneUpper = runBlocking {
                creationRangeListing(it, null, firstCreatedAt - 1)
            }

            assertEquals(0, noneUpper.itemsInTotal)
        }
    }

    private fun fetchAllJobsInPage(session: HibernateSession): Page<VerifiedJobWithAccessToken> {
        return runBlocking {
            jobHibDao.list(
                session,
                user.createToken(),
                NormalizedPaginationRequest(100, 0),
                SortOrder.DESCENDING,
                JobSortBy.LAST_UPDATE,
                null,
                null,
                application = null,
                version = null
            )
        }
    }

    private suspend fun creationRangeListing(
        session: HibernateSession,
        min: Long?,
        max: Long?
    ): Page<VerifiedJobWithAccessToken> {
        return jobHibDao.list(
            session,
            user.createToken(),
            NormalizedPaginationRequest(10, 0),
            SortOrder.DESCENDING,
            JobSortBy.LAST_UPDATE,
            min,
            max,
            application = null,
            version = null
        )
    }

    @Test
    fun `Add and retrieve apps based on state`() {

        db.withTransaction {
            addJob1(it)
        }

        db.withTransaction {
            val jobs = fetchAllJobsInPage(it)
            assertEquals(1, jobs.items.size)

            val jobByFilter = runBlocking {
                jobHibDao.list(
                    it,
                    user.createToken(),
                    NormalizedPaginationRequest(100, 0),
                    SortOrder.DESCENDING,
                    JobSortBy.LAST_UPDATE,
                    null,
                    null,
                    JobState.VALIDATED,
                    null,
                    null
                )
            }
            assertEquals(1, jobByFilter.items.size)

            val noJobByFilter = runBlocking {
                jobHibDao.list(
                    it,
                    user.createToken(),
                    NormalizedPaginationRequest(100, 0),
                    SortOrder.DESCENDING,
                    JobSortBy.LAST_UPDATE,
                    null,
                    null,
                    JobState.CANCELING,
                    null,
                    null
                )
            }

            assertEquals(0, noJobByFilter.items.size)
        }
    }

    private fun addJob1(session: HibernateSession) {
        val firstJob = VerifiedJobWithAccessToken(
            VerifiedJob(
                normAppDesc,
                null,
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
                null,
                archiveInCollection = normAppDesc.metadata.title
            ),
            "token",
            "token"
        )
        jobHibDao.create(session, firstJob)
    }
}

fun SecurityPrincipal.createToken(): SecurityPrincipalToken = SecurityPrincipalToken(
    this,
    listOf(SecurityScope.ALL_WRITE),
    0,
    Long.MAX_VALUE,
    null
)
