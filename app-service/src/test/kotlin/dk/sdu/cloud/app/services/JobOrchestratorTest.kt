package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStreams
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*

class JobOrchestratorTest {

    @Test
    fun `test this`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase

        val client = ClientMock.authenticatedClient
        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobDao = JobHibernateDao(appDao, toolDao)

        val orchestrator = JobOrchestrator(
            client,
            EventServiceMock.createProducer(JobStreams.jobStateEvents),
            EventServiceMock.createProducer(AccountingEvents.jobCompleted),
            db,
            JobVerificationService(db, appDao, toolDao),
            ComputationBackendService(listOf(ToolBackend.UDOCKER.name), true),
            JobFileService(client),
            jobDao
        )
        db.withTransaction { session ->
            toolDao.create(session, "user", normToolDesc)
            appDao.create(session, "user", normAppDesc)
            jobDao.create(session, verifiedJobWithAccessToken)

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

