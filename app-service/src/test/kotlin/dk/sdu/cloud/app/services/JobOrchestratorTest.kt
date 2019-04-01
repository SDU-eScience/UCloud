package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.ComputationDescriptions
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
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
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
        val compBackend = ComputationBackendService(listOf(ToolBackend.UDOCKER.name), true)

        val decodedJWT = mockk<DecodedJWT>(relaxed = true).also {
            every { it.subject } returns "user"
        }

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

        val backend = compBackend.getAndVerifyByName(ToolBackend.UDOCKER.name)
        ClientMock.mockCallSuccess(
            backend.jobVerified,
            Unit
        )

        val returnedID = runBlocking {
            orchestrator.startJob(
                startJobRequest,
                decodedJWT,
                client
            )
        }


    }
}

