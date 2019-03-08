package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.kafka.MappedEventProducer
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*

class JobOrchestratorTest {
    val verifiedJob = VerifiedJob(
        normAppDesc,
        emptyList(),
        "verifiedId",
        "owner",
        1,
        1,
        SimpleDuration(0, 1, 0),
        VerifiedJobInput(emptyMap()),
        "backend",
        JobState.SCHEDULED,
        "scheduled",
        12345678,
        123456789,
        archiveInCollection = normAppDesc.metadata.title
    )

    @Test
    fun `test this`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase

        val client = ClientMock.authenticatedClient
        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobDao = JobHibernateDao(appDao, toolDao)

        val jobStateChange = mockk<MappedEventProducer<String, JobStateChange>>(relaxed = true)
        val jobCompletedEvent = mockk<MappedEventProducer<String, JobCompletedEvent>>(relaxed = true)
        val orchestrator = JobOrchestrator(
            client,
            jobStateChange,
            jobCompletedEvent,
            db,
            JobVerificationService(db, appDao, toolDao),
            ComputationBackendService(listOf(ToolBackend.UDOCKER.name), true),
            JobFileService(client),
            jobDao
        )
        db.withTransaction { session ->
            toolDao.create(session, "user", normToolDesc)
            appDao.create(session, "user", normAppDesc)
            jobDao.create(session, VerifiedJobWithAccessToken(verifiedJob, "token"))

            runBlocking {
                jobDao.findJobsCreatedBefore(session, Date().time).forEach {
                    println(it.job)
                }
            }
        }

        runBlocking {
            orchestrator.removeExpiredJobs()
        }

        db.withTransaction { session ->
            runBlocking {
                println(jobDao.findOrNull(session, "verifiedId", "owner"))
            }
        }
    }
}

