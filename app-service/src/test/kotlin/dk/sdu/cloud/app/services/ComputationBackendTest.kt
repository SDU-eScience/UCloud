package dk.sdu.cloud.app.services

import dk.sdu.cloud.service.test.TestUsers
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComputationBackendTest{

    @Test
    fun `backend Principal name test`() {
        val service = ComputationBackendService(listOf("backend1, backend2"), false)
        val returnName = service.backendPrincipalName("backend1")
        assertEquals("_app-backend1", returnName)
    }

    @Test (expected = ComputationBackendException.UnrecognizedBackend::class)
    fun `get and verify test - backend does not exists`() {
        val service = ComputationBackendService(listOf("backend1", "backend2"), false)
        service.getAndVerifyByName("NotABackend")
    }

    @Test
    fun `get and verify test - principal null`() {
        val service = ComputationBackendService(listOf("backend1", "backend2"), false)
        val returnValues = service.getAndVerifyByName("backend1")
        assertTrue(returnValues is NamedComputationBackendDescriptions)
    }

    @Test (expected = ComputationBackendException.UntrustedSource::class)
    fun `get and verify test - principal not accepted`() {
        val service = ComputationBackendService(listOf("backend1", "backend2"), false)
        service.getAndVerifyByName("backend1", TestUsers.user)
    }

    @Test
    fun `Use cached test`() {
        val service = ComputationBackendService(listOf("backend1", "backend2"), true)
        val backedResult1 = service.getAndVerifyByName("backend1")

        val backedResult2 = service.getAndVerifyByName("backend1")

        assertEquals(backedResult1, backedResult2)
    }
}
