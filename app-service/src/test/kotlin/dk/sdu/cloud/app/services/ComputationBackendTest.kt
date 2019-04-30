package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationBackend
import dk.sdu.cloud.service.test.TestUsers
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComputationBackendTest {
    private val services =
        ComputationBackendService(listOf(
            ApplicationBackend("backend1"),
            ApplicationBackend("backend2")
        ), false)

    @Test
    fun `backend Principal name test`() {
        val returnName = services.backendPrincipalName("backend1")
        assertEquals("_app-backend1", returnName)
    }

    @Test(expected = ComputationBackendException.UnrecognizedBackend::class)
    fun `get and verify test - backend does not exists`() {
        services.getAndVerifyByName("NotABackend")
    }

    @Test
    fun `get and verify test - principal null`() {
        val returnValues = services.getAndVerifyByName("backend1")
        assertTrue(returnValues is NamedComputationBackendDescriptions)
    }

    @Test(expected = ComputationBackendException.UntrustedSource::class)
    fun `get and verify test - principal not accepted`() {
        services.getAndVerifyByName("backend1", TestUsers.user)
    }

    @Test
    fun `Use cached test`() {
        val backedResult1 = services.getAndVerifyByName("backend1")
        val backedResult2 = services.getAndVerifyByName("backend1")
        assertEquals(backedResult1, backedResult2)
    }
}
