package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.linesInRange
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.JobState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobTailTest {
    private val connectionPool: SSHConnectionPool = mockk(relaxed = true)
    private val connection: SSHConnection = mockk(relaxed = true)
    private val jobFileService: JobFileService = mockk(relaxed = true)
    private val service: JobTail

    init {
        every { connectionPool.borrowConnection() } returns Pair(0, connection)
        service = JobTail(connectionPool, jobFileService)
    }

    @BeforeTest
    fun before() {
        mockkStatic("dk.sdu.cloud.app.abacus.services.ssh.SFTPKt")
    }

    @Test
    fun `test following streams (bad status)`() = runBlocking {
        val request = InternalFollowStdStreamsRequest(
            JobData.job.copy(currentState = JobState.SUCCESS),
            0, 100,
            0, 100
        )

        val response = service.followStdStreams(request)

        assertTrue(response.stdout.isEmpty())
        assertTrue(response.stderr.isEmpty())
    }

    @Test
    fun `test following lines`() = runBlocking {
        val request = InternalFollowStdStreamsRequest(
            JobData.job.copy(currentState = JobState.RUNNING),
            0, 100,
            0, 100
        )

        val second = "foobar"
        coEvery { connection.linesInRange(any(), any(), any()) } returns Pair(0, second)

        val response = service.followStdStreams(request)
        assertEquals(second, response.stdout)
        assertEquals(second, response.stderr)
    }

    @Test
    fun `test following stdout`() = runBlocking {
        val request = InternalFollowStdStreamsRequest(
            JobData.job.copy(currentState = JobState.RUNNING),
            0, 100,
            0, 0
        )

        val second = "foobar"
        coEvery { connection.linesInRange(any(), any(), any()) } returns Pair(0, second)

        val response = service.followStdStreams(request)
        assertEquals(second, response.stdout)
        assertEquals("", response.stderr)
    }
}
