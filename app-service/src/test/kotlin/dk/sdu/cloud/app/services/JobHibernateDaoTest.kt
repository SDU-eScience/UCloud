package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun tool(date: Date): ToolEntity {
        return ToolEntity(
            user,
            date,
            date,
            normToolDesc,
            "original Document",
            EmbeddedNameAndVersion()
        )
    }

    private fun appEnt(date: Date): ApplicationEntity {
        return ApplicationEntity(
            user,
            date,
            date,
            normAppDesc,
            "original Document",
            tool(date),
            EmbeddedNameAndVersion()
        )
    }

    @Test
    fun `create, find and update slurminfo and update jobinfo test`() {
        val toolDao = ToolHibernateDAO()
        val appDao = ApplicationHibernateDAO(toolDao)
        val jobHibDao = JobHibernateDAO(appDao)

        withDatabase { db ->
            db.withTransaction {
                toolDao.create(it, user, normToolDesc)
                appDao.create(it, user, normAppDesc)
                jobHibDao.createJob(it, user, systemId, appName, version, "$user/USER")

                val result = jobHibDao.findJobInformationByJobId(it, user, systemId)
                assertEquals(AppState.VALIDATED, result?.state)
                assertNull(result?.slurmId)

                val result2 = jobHibDao.findJobById(it, user, systemId)
                assertEquals(AppState.VALIDATED, result2?.state)

                val hits = jobHibDao.findAllJobsWithStatus(it, user, NormalizedPaginationRequest(10, 0))
                val result3 = hits.items.first()
                assertEquals(AppState.VALIDATED, result3.state)

                jobHibDao.updateJobWithSlurmInformation(it, systemId, "sshUser", "jobDir", "workDir", 8282)
                val result4 = jobHibDao.findJobInformationBySlurmId(it, 8282)
                assertEquals("sshUser", result4?.sshUser)
                assertEquals("2.2", result4?.appVersion)

                jobHibDao.updateJobBySystemId(it, systemId, AppState.SUCCESS)
                val result5 = jobHibDao.findJobById(it, user, systemId)
                assertEquals(AppState.SUCCESS, result5?.state)

            }
        }
    }

    @Test(expected = JobBadApplication::class)
    fun `create test - bad job application`() {
        val appDao = mockk<ApplicationHibernateDAO>()
        val jobHibDao = JobHibernateDAO(appDao)

        every { appDao.create(any(), any(), any()) } just runs

        every { appDao.internalByNameAndVersion(any(), any(), any()) } returns null

        withDatabase { db ->
            db.withTransaction {
                jobHibDao.createJob(it, user, systemId, appName, version, "$user/USER")
            }
        }
    }
}
