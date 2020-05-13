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
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@Ignore("Testing strategy for new db")
class JobHibernateDaoTest {
    private val user = TestUsers.user.copy(username = "User1")
    private val systemId = UUID.randomUUID().toString()
    private val appName = normAppDesc.metadata.name
    private val version = normAppDesc.metadata.version
    private val appName2 = normAppDesc2.metadata.name
    private val version2 = normAppDesc2.metadata.version

    private val toolDao: ToolStoreService = mockk()
    private val appDao: AppStoreService = mockk()
    private lateinit var db: DBContext
    private lateinit var jobHibDao: JobDao

    @BeforeTest
    fun beforeTest() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        db = TODO()
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        jobHibDao = JobDao(appDao, toolDao)

        coEvery { toolDao.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version) } returns normTool
        coEvery {
            appDao.findByNameAndVersion(
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )
        } returns normAppDesc
        coEvery { toolDao.findByNameAndVersion(normToolDesc.info.name, normToolDesc.info.version) } returns normTool
        coEvery {
            appDao.findByNameAndVersion(
                appName2,
                version2
            )
        } returns normAppDesc2
    }

    @Test(expected = JobException.NotFound::class)
    fun `update status - not found tests`() = runBlocking {
        jobHibDao.updateStatus(db, systemId, "good")
    }

    @Test(expected = JobException.NotFound::class)
    fun `update status and statue - not found tests`() = runBlocking {
        jobHibDao.updateStatus(db, systemId, state = JobState.PREPARED, status = "good")
    }

    @Test
    fun `create, find and update jobinfo test`() = runBlocking {
        val jobWithToken = VerifiedJobWithAccessToken(
            VerifiedJob(
                systemId,
                null,
                user.username,
                normAppDesc,
                "abacus",
                1,
                SimpleDuration(0, 1, 0),
                1,
                MachineReservation.BURST,
                VerifiedJobInput(emptyMap()),
                emptySet(),
                emptySet(),
                emptySet(),
                JobState.VALIDATED,
                null,
                "Unknown",
                archiveInCollection = normAppDesc.metadata.title
            ),
            "token",
            "token"
        )
        jobHibDao.create(db, jobWithToken)
        println("JOB: $jobWithToken")

        run {
            val result = jobHibDao.updateStatus(db, systemId, "good")
        }

        run {
            val result = jobHibDao.find(db, listOf(systemId), user.username).singleOrNull()
            assertEquals(JobState.VALIDATED, result?.job?.currentState)
            assertEquals("good", result?.job?.status)
        }

        run {
            val result = jobHibDao.findJobsCreatedBefore(db, System.currentTimeMillis() + 5000).toList()
            assertEquals(1, result.size)
        }

        jobHibDao.updateStatus(db, systemId, state = JobState.RUNNING, status = "better")
        jobHibDao.updateStatus(db, systemId, state = JobState.SUCCESS, status = "better")

        run {
            val result = jobHibDao.find(db, listOf(systemId), user.username).singleOrNull()
            assertEquals(JobState.SUCCESS, result?.job?.currentState)
            assertEquals("better", result?.job?.status)
        }

        run {
            val result = jobHibDao.list(
                db,
                user.username,
                NormalizedPaginationRequest(10, 0),
                ListRecentRequest()
            )
            assertEquals(1, result.itemsInTotal)
        }
    }

    @Test
    fun `Add and retrieve jobs based on createdAt, both min and max`() = runBlocking {
        db.withSession { session ->
            val firstJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    systemId,
                    null,
                    user.username,
                    normAppDesc,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(session, firstJob)

            Thread.sleep(10)

            val secondJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    UUID.randomUUID().toString(),
                    null,
                    user.username,
                    normAppDesc2,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc2.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(session, secondJob)

        }

        db.withSession {
            val result = fetchAllJobsInPage(it)
            assertEquals(2, result.itemsInTotal)
        }

        db.withSession {
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
    fun `Add and retrieve jobs based on createdAt, either min or max`() = runBlocking {
        db.withSession {
            val firstJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    systemId,
                    null,
                    user.username,
                    normAppDesc,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, firstJob)

            Thread.sleep(10)

            val secondJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    UUID.randomUUID().toString(),
                    null,
                    user.username,
                    normAppDesc2,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc2.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, secondJob)
        }

        db.withSession {
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

    private fun fetchAllJobsInPage(session: DBContext): Page<VerifiedJobWithAccessToken> {
        return runBlocking {
            jobHibDao.list(
                session,
                user.username,
                NormalizedPaginationRequest(100, 0),
                ListRecentRequest(
                    order = SortOrder.DESCENDING,
                    sortBy = JobSortBy.LAST_UPDATE
                )
            )
        }
    }

    private suspend fun creationRangeListing(
        session: DBContext,
        min: Long?,
        max: Long?
    ): Page<VerifiedJobWithAccessToken> {
        return jobHibDao.list(
            session,
            user.username,
            NormalizedPaginationRequest(10, 0),
            ListRecentRequest(
                order = SortOrder.DESCENDING,
                sortBy = JobSortBy.LAST_UPDATE,
                minTimestamp = min,
                maxTimestamp = max
            )
        )
    }

    @Test
    fun `Add and retrieve apps based on state`() = runBlocking {
        db.withSession {
            addJob(it)
        }

        db.withSession {
            val jobs = fetchAllJobsInPage(it)
            assertEquals(1, jobs.items.size)

            val jobByFilter = runBlocking {
                jobHibDao.list(
                    it,
                    user.username,
                    NormalizedPaginationRequest(100, 0),
                    ListRecentRequest(
                        order = SortOrder.DESCENDING,
                        sortBy = JobSortBy.LAST_UPDATE,
                        filter = JobState.VALIDATED
                    )
                )
            }
            assertEquals(1, jobByFilter.items.size)

            val noJobByFilter = runBlocking {
                jobHibDao.list(
                    it,
                    user.username,
                    NormalizedPaginationRequest(100, 0),
                    ListRecentRequest(
                        order = SortOrder.DESCENDING,
                        sortBy = JobSortBy.LAST_UPDATE,
                        filter = JobState.CANCELING
                    )
                )
            }

            assertEquals(0, noJobByFilter.items.size)
        }
    }

    @Test
    fun `test different sort by and order`() = runBlocking {
        val extraID = UUID.randomUUID().toString()
        val userToken = user.createToken()
        db.withSession { session ->
            addJob(session)
            Thread.sleep(1000)
            addJob(
                session,
                VerifiedJobWithAccessToken(
                    VerifiedJob(
                        extraID,
                        "NewName",
                        user.username,
                        normAppDesc2,
                        "abacus",
                        1,
                        SimpleDuration(0, 1, 0),
                        1,
                        MachineReservation.BURST,
                        VerifiedJobInput(emptyMap()),
                        emptySet(),
                        emptySet(),
                        emptySet(),
                        JobState.RUNNING,
                        null,
                        "Unknown",
                        archiveInCollection = normAppDesc.metadata.title
                    ),
                    "token1",
                    "token1"
                )
            )
        }

        db.withSession {
            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.NAME)
                )

                assertEquals(2, page.itemsInTotal)
                assertEquals(page.items.first().job.application.metadata.name, appName2)
                assertEquals(page.items.last().job.application.metadata.name, appName)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(order = SortOrder.ASCENDING, sortBy = JobSortBy.NAME)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName)
                assertEquals(page.items.last().job.application.metadata.name, appName2)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.STATE)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName)
                assertEquals(page.items.last().job.application.metadata.name, appName2)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.LAST_UPDATE)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName2)
                assertEquals(page.items.last().job.application.metadata.name, appName)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.APPLICATION)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName2)
                assertEquals(page.items.last().job.application.metadata.name, appName)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.STARTED_AT)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName2)
                assertEquals(page.items.last().job.application.metadata.name, appName)
            }

            runBlocking {
                val page = jobHibDao.list(
                    it,
                    userToken.principal.username,
                    NormalizedPaginationRequest(10, 0),
                    ListRecentRequest(sortBy = JobSortBy.CREATED_AT)
                )
                assertEquals(page.items.first().job.application.metadata.name, appName2)
                assertEquals(page.items.last().job.application.metadata.name, appName)
            }
        }

    }

    private suspend fun addJob(session: DBContext, verifiedJobWithAccessToken: VerifiedJobWithAccessToken? = null) {
        val firstJob = verifiedJobWithAccessToken ?: VerifiedJobWithAccessToken(
            VerifiedJob(
                systemId,
                null,
                user.username,
                normAppDesc,
                "abacus",
                1,
                SimpleDuration(0, 1, 0),
                1,
                MachineReservation.BURST,
                VerifiedJobInput(emptyMap()),
                emptySet(),
                emptySet(),
                emptySet(),
                JobState.VALIDATED,
                null,
                "Unknown",
                archiveInCollection = normAppDesc.metadata.title
            ),
            "token",
            "token"
        )
        jobHibDao.create(session, firstJob)
    }

    @Test
    fun `Add, retrieve and delete jobs`() = runBlocking {

        db.withSession {
            val firstJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    systemId,
                    null,
                    user.username,
                    normAppDesc,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, firstJob)

            Thread.sleep(10)

            val secondJob = VerifiedJobWithAccessToken(
                VerifiedJob(
                    UUID.randomUUID().toString(),
                    null,
                    user.username,
                    normAppDesc2,
                    "abacus",
                    1,
                    SimpleDuration(0, 1, 0),
                    1,
                    MachineReservation.BURST,
                    VerifiedJobInput(emptyMap()),
                    emptySet(),
                    emptySet(),
                    emptySet(),
                    JobState.VALIDATED,
                    null,
                    "Unknown",
                    archiveInCollection = normAppDesc2.metadata.title
                ),
                "token",
                "token"
            )
            jobHibDao.create(it, secondJob)
        }

        db.withSession {
            val jobs = fetchAllJobsInPage(it)

            assertEquals(2, jobs.itemsInTotal)

            jobHibDao.deleteJobInformation(it, normAppDesc.metadata.name, normAppDesc.metadata.version)

            val jobsAfterDelete = fetchAllJobsInPage(it)

            assertEquals(1, jobsAfterDelete.itemsInTotal)

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
