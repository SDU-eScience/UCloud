package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.ComputationDescriptions
import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.InternalStdStreamsResponse
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.JobStreams
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.hibernate.Session
import org.junit.Test
import kotlin.test.assertEquals

class JobOrchestratorTest {

    private val decodedJWT = mockk<DecodedJWT>(relaxed = true).also {
        every { it.subject } returns "user"
    }

    private val client = ClientMock.authenticatedClient
    private lateinit var backend: NamedComputationBackendDescriptions

    private fun setup() :JobOrchestrator<Session> {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase

        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobDao = JobHibernateDao(appDao, toolDao)
        val compBackend = ComputationBackendService(listOf(ToolBackend.UDOCKER.name), true)

        db.withTransaction {
            toolDao.create(it, "user", normToolDesc)
            appDao.create(it, "user", normAppDesc)
        }
        val orchestrator = JobOrchestrator(
            client,
            EventServiceMock.createProducer(JobStreams.jobStateEvents),
            EventServiceMock.createProducer(AccountingEvents.jobCompleted),
            db,
            JobVerificationService(db, appDao, toolDao),
            compBackend,
            JobFileService(client),
            jobDao
        )

        backend = compBackend.getAndVerifyByName(ToolBackend.UDOCKER.name)
        ClientMock.mockCallSuccess(
            backend.jobVerified,
            Unit
        )

        return orchestrator
    }

    @Test
    fun `orchestrator start job, handle proposed state, lookup test `() {
        val orchestrator = setup()

        val returnedID = runBlocking {
            orchestrator.startJob(
                startJobRequest,
                decodedJWT,
                client
            )
        }

        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.PREPARED),
                "newStatus",
                TestUsers.user
            )
        }

        // Same state for branch check
        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.FAILURE),
                "newFAILStatus",
                TestUsers.user
            )
        }
        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID, JobState.FAILURE),
                "newStatus",
                TestUsers.user
            )
        }

        val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
        assertEquals("newFAILStatus", job.status)

        orchestrator.handleAddStatus(returnedID, "Status Is FAIL", TestUsers.user)

        val jobAfterStatusChange = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
        assertEquals("Status Is FAIL", jobAfterStatusChange.status)

        // Checking bad transition - Prepared -> Validated not legal
        runBlocking {
            try {
                orchestrator.handleProposedStateChange(
                    JobStateChange(returnedID, JobState.VALIDATED),
                    "newerStatus",
                    TestUsers.user
                )
            } catch (ex: JobException.BadStateTransition) {
                println("Caught Expected Exception")
            }
        }

        runBlocking {
            orchestrator.removeExpiredJobs()
        }

    }

    @Test
    fun `orchestrator handle job complete fail and success test`() {
        val orchestrator = setup()

        //Success
        run {
            val returnedID = runBlocking {
                orchestrator.startJob(
                    startJobRequest,
                    decodedJWT,
                    client
                )
            }

            runBlocking {
                orchestrator.handleJobComplete(
                    returnedID,
                    SimpleDuration(1, 0, 0),
                    true,
                    TestUsers.user
                )
            }

            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.SUCCESS, job.currentState)
        }

        //failed
        run {
            val returnedID = runBlocking {
                orchestrator.startJob(
                    startJobRequest,
                    decodedJWT,
                    client
                )
            }

            runBlocking {
                orchestrator.handleJobComplete(
                    returnedID,
                    SimpleDuration(1, 0, 0),
                    false,
                    TestUsers.user
                )
            }

            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.FAILURE, job.currentState)
        }
    }

    @Test
    fun `handle state change from event test `() {
        val orchestrator = setup()

        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        run {
            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.VALIDATED, job.currentState)
        }

        ClientMock.mockCallSuccess(
            backend.jobPrepared,
            Unit
        )

        val stateChangeValidate = JobStateChange(returnedID, JobState.VALIDATED)

        orchestrator.handleStateChange(stateChangeValidate)

        run {
            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.PREPARED, job.currentState)
        }

        // faking dual emit from kakfa
        orchestrator.handleStateChange(stateChangeValidate)
        run {
            val job = orchestrator.lookupOwnJob(returnedID, TestUsers.user)
            assertEquals(JobState.PREPARED, job.currentState)
        }

        ClientMock.mockCallError(
            backend.cleanup,
            statusCode = HttpStatusCode.NotFound
        )

        val stateChangeSuccess = JobStateChange(returnedID, JobState.SUCCESS)

        orchestrator.handleStateChange(stateChangeSuccess)
    }

    @Test
    fun `handle incoming files`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/")
        )

        ClientMock.mockCallSuccess(
            MultiPartUploadDescriptions.simpleUpload,
            Unit
        )
        ClientMock.mockCallSuccess(
            FileDescriptions.extract,
            Unit
        )

        runBlocking {
            orchestrator.handleIncomingFile(
                returnedID,
                TestUsers.user,
                "path/to/file",
                1234,
                ByteReadChannel.Empty,
                true
            )
        }
    }

    @Test
    fun `followStreams test`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        ClientMock.mockCallSuccess(
            FileDescriptions.findHomeFolder,
            FindHomeFolderResponse("/home/")
        )

        ClientMock.mockCallSuccess(
            backend.follow,
            InternalStdStreamsResponse("stdout", 10, "stderr", 10)
        )
        runBlocking {
            val result = orchestrator.followStreams(FollowStdStreamsRequest(returnedID,0, 0, 0, 0))
            assertEquals("stdout", result.stdout)
            assertEquals("stderr", result.stderr)
            assertEquals(10, result.stderrNextLine)
        }
    }

    @Test (expected = JobException.BadStateTransition::class)
    fun `test with job exception`() {
        val orchestrator = setup()
        val returnedID = runBlocking {
            orchestrator.startJob(startJobRequest, decodedJWT, client)
        }

        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange(returnedID,JobState.VALIDATED),
                null,
                TestUsers.user)
        }
    }

    @Test (expected = JobException.NotFound::class)
    fun `test with job exception2`() {
        val orchestrator = setup()

        runBlocking {
            orchestrator.handleProposedStateChange(
                JobStateChange("lalala",JobState.VALIDATED),
                null,
                TestUsers.user)
        }
    }
}

