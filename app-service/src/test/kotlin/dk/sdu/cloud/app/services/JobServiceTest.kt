package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.utils.mockedUser
import dk.sdu.cloud.app.utils.withAuthMock
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.experimental.runBlocking
import org.hibernate.Session
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JobServiceTest {

    private val jobWithStatus = JobWithStatus(
        "JobID",
        "owner",
        AppState.SUCCESS,
        "succes",
        "appName",
        "2.2",
        123457,
        1234567
    )

    private fun createJobInfo(appState: AppState): JobInformation {
        val path = Paths.get(".").toAbsolutePath().normalize().toString()
        return JobInformation(
            "systemId",
            "owner",
            "appName",
            "2.2",
            12345,
            "Succes",
            "sshUser",
            "jobDirectory",
            path,
            1234,
            appState,
            "owner/USER"
        )
    }


    @Test
    fun `Get recent jobs test`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                every { jobDao.findAllJobsWithStatus(any(), any(), any()) } answers {
                    val returnJob = jobWithStatus
                    val page = Page(1, 10, 0, listOf(returnJob))
                    page
                }
                assertEquals(1, jService.recentJobs("user", PaginationRequest(10, 0)).itemsInTotal)
                assertEquals(10, jService.recentJobs("user", PaginationRequest(10, 0)).itemsPerPage)
                assertEquals("JobID", jService.recentJobs("user", PaginationRequest(10, 0)).items.first().jobId)

            }
        }
    }

    @Test
    fun `Find job by ID test`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                every { jobDao.findJobById(any(), any(), any()) } returns jobWithStatus

                assertEquals("JobID", jService.findJobById("user", "JobID")?.jobId)

            }
        }
    }

    @Test
    fun `Find job by ID test - not found`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                every { jobDao.findJobById(any(), any(), any()) } returns null

                assertNull(jService.findJobById("user", "JobID"))
            }
        }
    }

    @Test
    fun `Find job information by ID test`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                every { jobDao.findJobInformationByJobId(any(), any(), any()) } returns createJobInfo(AppState.SUCCESS)

                assertEquals("2.2", jService.findJobForInternalUseById("user", "JobID")?.appVersion.toString())
            }
        }
    }

    @Test
    fun `start job test`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                val app = mockk<AppRequest.Start>()

                coEvery { jobExecution.startJob(any(), any(), any()) } returns "SystemID"

                runBlocking {
                    assertEquals("SystemID", jService.startJob(mockedUser(), app, mockk(relaxed = true)))
                }
            }
        }
    }

    @Test
    fun `FollowStdStreams test`() {
        withAuthMock {
            withDatabase { db ->
                val jobDao = mockk<JobHibernateDAO>()
                val ssh = mockk<SSHConnectionPool>()
                // TODO

                val jobExecution = mockk<JobOrchestrator<Session>>()
                val jService = JobService(db, jobDao, ssh, jobExecution)

                val streamRequest = FollowStdStreamsRequest(
                    "jobID",
                    0,
                    100,
                    0,
                    100
                )

                //Testing when SUCCESS, FAILURE, PREPARED, VALIDATED
                assertEquals(
                    "2.2",
                    jService.followStdStreams(streamRequest, createJobInfo(AppState.SUCCESS)).application.version
                )
                assertEquals(
                    "2.2",
                    jService.followStdStreams(streamRequest, createJobInfo(AppState.FAILURE)).application.version
                )
                assertEquals(
                    "2.2",
                    jService.followStdStreams(streamRequest, createJobInfo(AppState.PREPARED)).application.version
                )
                assertEquals(
                    "2.2",
                    jService.followStdStreams(streamRequest, createJobInfo(AppState.VALIDATED)).application.version
                )

                //Testing When RUNNING, SCHEDULED
                /*assertEquals("2.2",
                    jService.followStdStreams(streamRequest, createJobInfo(AppState.RUNNING)).application.version)
*/
            }
        }
    }
}
