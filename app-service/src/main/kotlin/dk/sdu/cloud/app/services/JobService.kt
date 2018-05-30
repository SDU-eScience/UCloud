package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.linesInRange
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import java.io.File
import kotlin.math.min

class JobService(
    private val dao: JobsDAO,
    private val sshPool: SSHConnectionPool,
    private val jobExecutionService: JobExecutionService
) {
    fun recentJobs(who: DecodedJWT, paginationRequest: PaginationRequest): Page<JobWithStatus> =
        dao.transaction { findAllJobsWithStatus(who.subject, paginationRequest.normalize()) }

    fun findJobById(who: DecodedJWT, jobId: String): JobWithStatus? =
        dao.transaction { findJobById(who.subject, jobId) }

    fun findJobForInternalUseById(who: DecodedJWT, jobId: String): JobInformation? =
        dao.transaction { findJobInformationByJobId(who.subject, jobId) }

    fun followStdStreams(lines: FollowStdStreamsRequest, job: JobInformation): FollowStdStreamsResponse {
        fun respond(stdout: String = "", stdoutNext: Int = 0, stderr: String = "", stderrNext: Int = 0) =
            FollowStdStreamsResponse(
                stdout,
                stdoutNext,
                stderr,
                stderrNext,
                NameAndVersion(job.appName, job.appVersion),
                job.state,
                job.status ?: "Unknown",
                job.state.isFinal(),
                job.systemId
            )

        when (job.state) {
            AppState.VALIDATED, AppState.PREPARED -> return respond()
            AppState.FAILURE, AppState.SUCCESS -> return respond()

            AppState.RUNNING, AppState.SCHEDULED -> {
                // We can continue
            }
        }

        val shouldFollowStdout = lines.stdoutMaxLines > 0
        val shouldFollowStderr = lines.stderrMaxLines > 0

        if (!shouldFollowStdout && !shouldFollowStderr) return respond()
        if (job.workingDirectory == null) return respond()

        sshPool.use {
            val stdOutLines = min(lines.stdoutMaxLines, 1000)
            val stdErrLines = min(lines.stderrMaxLines, 1000)

            val workingDirectory = File(job.workingDirectory)
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
                stdout, stdout.count { it == '\n' } + lines.stdoutLineStart,
                stderr, stderr.count { it == '\n' } + lines.stderrLineStart
            )
        }
    }

    suspend fun startJob(who: DecodedJWT, req: AppRequest.Start, cloud: AuthenticatedCloud): String {
        return jobExecutionService.startJob(req, who, cloud)
    }
}

