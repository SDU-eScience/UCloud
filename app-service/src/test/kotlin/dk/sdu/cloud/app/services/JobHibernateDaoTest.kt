package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class JobHibernateDaoTest {
    private val user = "User1"
    private val systemId = UUID.randomUUID().toString()
    private val appName = "Name of application"
    private val version = "2.2"

    private val normAppDesc = NormalizedApplicationDescription(
        NameAndVersion(appName, version),
        NameAndVersion(appName, version),
        listOf("authors"),
        "title",
        "description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("Globs"),
        listOf()
    )

    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion(appName, version),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("auther"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    @Test
    fun `create, find and update jobinfo test`() {
        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDao(appDao)

        withDatabase { db ->
            db.withTransaction {
                toolDao.create(it, user, normToolDesc)
                appDao.create(it, user, normAppDesc)
                val app = appDao.findByNameAndVersion(it, null, appName, version)
                val jobWithToken = VerifiedJobWithAccessToken(
                    VerifiedJob(
                        app,
                        emptyList(),
                        systemId,
                        user,
                        1,
                        1,
                        SimpleDuration(0, 1, 0),
                        VerifiedJobInput(emptyMap()),
                        "abacus",
                        JobState.VALIDATED,
                        "Unknown"
                    ),
                    "token"
                )
                jobHibDao.create(it, jobWithToken)

                val result = jobHibDao.findOrNull(it, systemId, user)
                assertEquals(JobState.VALIDATED, result?.job?.currentState)

                jobHibDao.updateStateAndStatus(it, systemId, JobState.SUCCESS)
                val result2 = jobHibDao.findOrNull(it, systemId, user)
                assertEquals(JobState.SUCCESS, result2?.job?.currentState)

            }
        }
    }
}
