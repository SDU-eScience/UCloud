package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.orchestrator.utils.verifiedJob
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JobDescriptionTest {

    @Test
    fun `simple Internal Follow Std Streams class test`() {
        val internal = InternalFollowStdStreamsRequest(
            verifiedJob,
            0,
            10,
            0,
            10
        )

        assertEquals("verifiedId", internal.job.id)
        assertEquals(0, internal.stderrLineStart)
        assertEquals(10, internal.stderrMaxLines)
        assertEquals(0, internal.stdoutLineStart)
        assertEquals(10, internal.stdoutMaxLines)
    }

    @Test
    fun `simple Follow WS Request class test`() {
        val follow = FollowWSRequest(
            "jobid",
            0,
            0
        )

        assertEquals(0, follow.stderrLineStart)
        assertEquals(0, follow.stdoutLineStart)
        assertEquals("jobid", follow.jobId)
    }

    @Test
    fun `simple Follow WS Response class test`() {
        val response = FollowWSResponse(
            "stdout",
            "stderr",
            "status",
            JobState.RUNNING,
            null
        )

        assertEquals("stdout", response.stdout)
        assertEquals("stderr", response.stderr)
        assertEquals("status", response.status)
        assertEquals(JobState.RUNNING, response.state)
        assertNull(response.failedState)
    }

    @Test
    fun `simple Follow WS Response class test - default`() {
        val response = FollowWSResponse()

        assertNull(response.stdout)
        assertNull(response.stderr)
        assertNull(response.status)
        assertNull(response.state)
        assertNull(response.failedState)
    }

    @Test
    fun `simple Internal Follow WS Request class test`() {
        val internal = InternalFollowWSStreamRequest(
            verifiedJob,
            0,
            0
        )

        assertEquals(0, internal.stderrLineStart)
        assertEquals(0, internal.stdoutLineStart)
        assertEquals("verifiedId", internal.job.id)
    }

    @Test
    fun `simple Internal Follow WS response class test`() {
        val response = InternalFollowWSStreamResponse(
            "streamID",
            "stdout",
            "stderr"
        )

        assertEquals("streamID", response.streamId)
        assertEquals("stdout", response.stdout)
        assertEquals("stderr", response.stderr)
    }

    @Test
    fun `simple Cancel WS Stream Request class test`() {
        val cancel = CancelWSStreamRequest("id")

        assertEquals("id", cancel.streamId)
    }

    @Test
    fun `simple Find By name and version class test`() {
        val request = FindByNameAndVersion("id", "version")

        assertEquals("id", request.name)
        assertEquals("version", request.version)
    }
}
