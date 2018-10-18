package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import org.junit.Test
import kotlin.test.assertEquals

class JobDaoTest {

    @Test
    fun `create simple job Info`() {
        val jobinfo = JobInformation(
            "SystemID",
            "owner",
            "appName",
            "2.2",
            123456,
            "status",
            "sshUser",
            "jobDir",
            "workDir",
            123456,
            AppState.SUCCESS,
            "owner/USER"
        )

        assertEquals("SystemID", jobinfo.systemId)
        assertEquals("owner", jobinfo.owner)
        assertEquals("appName", jobinfo.appName)
        assertEquals("2.2", jobinfo.appVersion)
        assertEquals(123456, jobinfo.slurmId)
        assertEquals("status", jobinfo.status)
        assertEquals("sshUser", jobinfo.sshUser)
        assertEquals("jobDir", jobinfo.jobDirectory)
        assertEquals("workDir", jobinfo.workingDirectory)
        assertEquals(123456, jobinfo.createdAt)
        assertEquals(AppState.SUCCESS, jobinfo.state)
    }
}
