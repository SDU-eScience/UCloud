package dk.sdu.cloud.app.abacus.service.ssh

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.sbatch
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SBatchTest {
    private val sshConnection: SSHConnection = mockk(relaxed = true)

    @Test
    fun `test with valid output`() {
        val command = CapturingSlot<String>()
        every { sshConnection.execWithOutputAsText(capture(command), any()) } returns Pair(0, "Submitted batch job 42")

        val result = sshConnection.sbatch("someFile")
        assertEquals(0, result.exitCode)
        assertEquals(42, result.jobId)
    }

    @Test
    fun `test with bad output`() {
        val command = CapturingSlot<String>()
        every { sshConnection.execWithOutputAsText(capture(command), any()) } returns Pair(0, "asdq2weasdq")

        val result = sshConnection.sbatch("someFile")
        assertEquals(0, result.exitCode)
        assertEquals(null, result.jobId)
    }

    @Test
    fun `test with bad status code`() {
        val command = CapturingSlot<String>()
        every { sshConnection.execWithOutputAsText(capture(command), any()) } returns Pair(42, "Something went wrong")

        val result = sshConnection.sbatch("someFile")
        assertEquals(42, result.exitCode)
        assertEquals(null, result.jobId)
    }

    @Test
    fun `test with reservation`() {
        val command = CapturingSlot<String>()
        every { sshConnection.execWithOutputAsText(capture(command), any()) } returns Pair(0, "Submitted batch job 42")

        val result = sshConnection.sbatch("someFile", reservation = "test")
        assertTrue { command.captured.contains("--reservation=test") }
        assertEquals(0, result.exitCode)
        assertEquals(42, result.jobId)
    }
}
