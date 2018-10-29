package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.slurmJobInfo
import dk.sdu.cloud.app.api.SimpleDuration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SlurmJobInfoTest {
    @Test
    fun `test normal output`() {
        val conn = mockk<SSHConnection>()
        every { conn.execWithOutputAsText(any(), any()) } returns (0 to "00:00:01")

        val jobId = 1234L
        val result = conn.slurmJobInfo(jobId)
        assertEquals(result, SimpleDuration(0, 0, 1))

        verify {
            conn.execWithOutputAsText(match { command ->
                val tokens = command.split(" ")
                assertEquals("sacct", tokens[0])
                val idx = tokens.indexOfFirst { it == "-j" }
                assertNotEquals(-1, idx)
                assertEquals(jobId, tokens[idx + 1].toLong())
                true
            })
        }
    }

    @Test
    fun `test normal output - all set`() {
        val conn = mockk<SSHConnection>()
        every { conn.execWithOutputAsText(any(), any()) } returns (0 to "01:02:03")

        val jobId = 1234L
        val result = conn.slurmJobInfo(jobId)
        assertEquals(result, SimpleDuration(1, 2, 3))

        verify {
            conn.execWithOutputAsText(match { command ->
                val tokens = command.split(" ")
                assertEquals("sacct", tokens[0])
                val idx = tokens.indexOfFirst { it == "-j" }
                assertNotEquals(-1, idx)
                assertEquals(jobId, tokens[idx + 1].toLong())
                true
            })
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `test bad error code`() {
        val conn = mockk<SSHConnection>()
        every { conn.execWithOutputAsText(any(), any()) } returns (1 to "error")

        val jobId = 1234L
        conn.slurmJobInfo(jobId)
    }

    @Test(expected = IllegalStateException::class)
    fun `test bad output`() {
        val conn = mockk<SSHConnection>()
        every { conn.execWithOutputAsText(any(), any()) } returns (1 to "qweassd")

        val jobId = 1234L
        conn.slurmJobInfo(jobId)
    }

    @Test(expected = IllegalStateException::class)
    fun `test bad output with split`() {
        val conn = mockk<SSHConnection>()
        every { conn.execWithOutputAsText(any(), any()) } returns (1 to "aa:bb:cc qweasdeqwe")

        val jobId = 1234L
        conn.slurmJobInfo(jobId)
    }
}
