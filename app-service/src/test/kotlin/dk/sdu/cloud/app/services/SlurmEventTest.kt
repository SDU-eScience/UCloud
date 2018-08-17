package dk.sdu.cloud.app.services

import org.junit.Test
import kotlin.test.assertEquals

class SlurmEventTest {

    @Test
    fun `simple creation and check of Slurm events - test`() {
        val ended = SlurmEventEnded(1)
        val running = SlurmEventRunning(2)
        val failed = SlurmEventFailed(3)
        val timeout = SlurmEventTimeout(4)

        assertEquals(1, ended.jobId)
        assertEquals(2, running.jobId)
        assertEquals(3, failed.jobId)
        assertEquals(4, timeout.jobId)

    }
}