package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.db.withTransaction
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
        123456789
    )

    @Test
    fun `test this`() {
        withDatabase { db ->

            val toolDao = ToolHibernateDAO()
            val appDao = ApplicationHibernateDAO(toolDao)
            val jobDao = JobHibernateDao(appDao, toolDao)

            val cloud = mockk<RefreshingJWTAuthenticatedCloud>(relaxed = true)
            val jobStateChange = mockk<MappedEventProducer<String, JobStateChange>>(relaxed = true)
            val jobCompletedEvent = mockk<MappedEventProducer<String, JobCompletedEvent>>(relaxed = true)
            val orchestrator = JobOrchestrator(
                cloud,
                jobStateChange,
                jobCompletedEvent,
                db,
                JobVerificationService(db, appDao, toolDao),
                ComputationBackendService(listOf(ToolBackend.UDOCKER.name), true),
                JobFileService(cloud),
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
}

