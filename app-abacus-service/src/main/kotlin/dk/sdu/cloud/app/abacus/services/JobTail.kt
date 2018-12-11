package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.linesInRange
import dk.sdu.cloud.app.abacus.services.ssh.use
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.InternalStdStreamsResponse
import dk.sdu.cloud.app.api.JobState
import kotlin.math.max
import kotlin.math.min

class JobTail(
    private val sshPool: SSHConnectionPool,
    private val jobFileService: JobFileService
) {
    suspend fun followStdStreams(lines: InternalFollowStdStreamsRequest): InternalStdStreamsResponse {
        val job = lines.job

        fun respond(stdout: String = "", stdoutNext: Int = 0, stderr: String = "", stderrNext: Int = 0) =
            InternalStdStreamsResponse(
                stdout,
                stdoutNext,
                stderr,
                stderrNext
            )

        when (job.currentState) {
            JobState.RUNNING, JobState.SCHEDULED -> {
                // We can continue
            }

            else -> return respond()
        }

        val shouldFollowStdout = lines.stdoutMaxLines > 0
        val shouldFollowStderr = lines.stderrMaxLines > 0

        if (!shouldFollowStdout && !shouldFollowStderr) return respond()

        sshPool.use {
            val stdOutLines = min(lines.stdoutMaxLines, FIXED_MAX_LINES)
            val stdErrLines = min(lines.stderrMaxLines, FIXED_MAX_LINES)

            val workingDirectory = jobFileService.filesDirectoryForJob(job.id)
            val stdoutFile = workingDirectory.resolve("stdout.txt")
            val stderrFile = workingDirectory.resolve("stderr.txt")

            val stdout = if (shouldFollowStdout) {
                linesInRange(stdoutFile.absolutePath, lines.stdoutLineStart, stdOutLines).second
            } else {
                ""
            }

            val stderr = if (shouldFollowStderr) {
                linesInRange(stderrFile.absolutePath, lines.stderrLineStart, stdErrLines).second
            } else {
                ""
            }

            return respond(
                stdout, stdout.count { it == '\n' } + max(1, lines.stdoutLineStart),
                stderr, stderr.count { it == '\n' } + max(1, lines.stderrLineStart)
            )
        }
    }

    companion object {
        private const val FIXED_MAX_LINES = 1000
    }
}
