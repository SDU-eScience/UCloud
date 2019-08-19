package dk.sdu.cloud.app.orchestrator.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc3
import dk.sdu.cloud.app.orchestrator.utils.normTool
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobVerification {
    val unverifiedJob = UnverifiedJob(
        StartJobRequest(
            NameAndVersion("name", "2.2"),
            "Test job",
            mapOf("int" to 5, "great" to "mojn", "missing" to 23),
            1,
            1,
            SimpleDuration(1, 0, 0),
            "backend"
        ),
        TestUsers.user.createToken(),
        "token"
    )

    lateinit var service: JobVerificationService
    val cloud = ClientMock.authenticatedClient


    private fun beforeTest(
        app: Application = normAppDesc,
        tool: Tool = normTool
    ) {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT

        val toolDao = mockk<ToolStoreService>()
        val appDao = mockk<AppStoreService>()
        coEvery {
            appDao.findByNameAndVersion(
                app.metadata.name,
                app.metadata.version
            )
        } returns app
        coEvery {
            toolDao.findByNameAndVersion(
                tool.description.info.name,
                tool.description.info.version
            )
        } returns tool

        service =
            JobVerificationService(appDao, toolDao, "abacus", SharedMountVerificationService())
    }

    @Test
    fun `test verification`() {
        beforeTest(app = normAppDesc3)

        val verified = runBlocking {
            service.verifyOrThrow(unverifiedJob, cloud)
        }

        val VJob = verified.job
        assertTrue(VJob.jobInput.backingData.keys.contains("missing"))
        assertTrue(VJob.jobInput.backingData.keys.contains("great"))
        assertTrue(VJob.jobInput.backingData.keys.contains("int"))
        assertTrue(VJob.jobInput.backingData.values.toString().contains("23"))
        assertTrue(VJob.jobInput.backingData.values.toString().contains("mojn"))
        assertTrue(VJob.jobInput.backingData.values.toString().contains("5"))
        assertEquals(JobState.VALIDATED, VJob.currentState)
    }

    val unverifiedJobWithWrongParamType = UnverifiedJob(
        StartJobRequest(
            NameAndVersion("name", "2.2"),
            "Test job",
            mapOf("int" to 2, "missing" to "NotAnInt"),
            1,
            1,
            SimpleDuration(1, 0, 0),
            "backend"
        ),
        TestUsers.user.createToken(),
        "token"
    )

    @Test(expected = JobException.VerificationError::class)
    fun `test verification - wrong param`() {
        beforeTest(app = normAppDesc3)
        runBlocking {
            service.verifyOrThrow(unverifiedJobWithWrongParamType, cloud)
        }
    }

    val unverifiedJobWithMissingNonOptional = UnverifiedJob(
        StartJobRequest(
            NameAndVersion("name", "2.2"),
            "Test job",
            mapOf("great" to "mojn"),
            1,
            1,
            SimpleDuration(1, 0, 0),
            "backend"
        ),
        TestUsers.user.createToken(),
        "token"
    )

    @Test(expected = JobException.VerificationError::class)
    fun `test verification - missing non-optional`() {
        beforeTest(app = normAppDesc3)

        runBlocking {
            service.verifyOrThrow(unverifiedJobWithMissingNonOptional, cloud)
        }
    }
}
