package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.buildSafeBashString
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.BashEscaper
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobVerification{

    val unverifiedJob = UnverifiedJob(
        AppRequest.Start(
            NameAndVersion("name", "2.2"),
            mapOf("int" to 5, "great" to "mojn", "missing" to 23),
            1,
            1,
            SimpleDuration(1,0,0),
            "backend"
        ),
        mockk<DecodedJWT>(relaxed = true).also {
            every { it.subject } returns "user"
        }
    )

    @Test
    fun `test verification`() {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud

        withDatabase { db ->
            val toolDao = ToolHibernateDAO()
            val appDao = ApplicationHibernateDAO(toolDao)
            db.withTransaction {
                toolDao.create(it, "user", normToolDesc)
                appDao.create(it, "user", normAppDesc3)
            }
            val service = JobVerificationService(db, appDao, toolDao)
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
    }

    val unverifiedJobWithWrongParamType = UnverifiedJob(
        AppRequest.Start(
            NameAndVersion("name", "2.2"),
            mapOf("int" to 2, "missing" to "NotAnInt"),
            1,
            1,
            SimpleDuration(1,0,0),
            "backend"
        ),
        mockk<DecodedJWT>(relaxed = true).also {
            every { it.subject } returns "user"
        }
    )

    @Test (expected = JobException.VerificationError::class)
    fun `test verification - wrong param`() {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud

        withDatabase { db ->
            val toolDao = ToolHibernateDAO()
            val appDao = ApplicationHibernateDAO(toolDao)
            db.withTransaction {
                toolDao.create(it, "user", normToolDesc)
                appDao.create(it, "user", normAppDesc3)
            }
            val service = JobVerificationService(db, appDao, toolDao)
            runBlocking {
                service.verifyOrThrow(unverifiedJobWithWrongParamType, cloud)
            }
        }
    }

    val unverifiedJobWithMissingNonOptional = UnverifiedJob(
        AppRequest.Start(
            NameAndVersion("name", "2.2"),
            mapOf("great" to "mojn"),
            1,
            1,
            SimpleDuration(1,0,0),
            "backend"
        ),
        mockk<DecodedJWT>(relaxed = true).also {
            every { it.subject } returns "user"
        }
    )

    @Test (expected = JobException.VerificationError::class)
    fun `test verification - missing non-optional`() {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud

        withDatabase { db ->
            val toolDao = ToolHibernateDAO()
            val appDao = ApplicationHibernateDAO(toolDao)
            db.withTransaction {
                toolDao.create(it, "user", normToolDesc)
                appDao.create(it, "user", normAppDesc3)
            }
            val service = JobVerificationService(db, appDao, toolDao)
            runBlocking {
                service.verifyOrThrow(unverifiedJobWithMissingNonOptional, cloud)
            }
        }
    }
}
