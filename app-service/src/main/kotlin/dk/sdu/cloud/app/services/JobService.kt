package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.linesInRange
import io.ktor.http.HttpStatusCode
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
        when (job.state) {
            AppState.VALIDATED, AppState.PREPARED -> throw JobServiceException.NotReady()
            AppState.FAILURE, AppState.SUCCESS -> throw JobServiceException.AlreadyComplete()

            AppState.RUNNING, AppState.SCHEDULED -> {
                // We can continue
            }
        }

        if (job.workingDirectory == null) throw JobServiceException.NotReady()

        val shouldFollowStdout = lines.stdoutMaxLines > 0
        val shouldFollowStderr = lines.stderrMaxLines > 0
        if (!shouldFollowStdout && !shouldFollowStderr) {
            return FollowStdStreamsResponse("", 0, "", 0)
        }

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

            return FollowStdStreamsResponse(
                stdout, stdout.count { it == '\n' } + lines.stdoutLineStart,
                stderr, stderr.count { it == '\n' } + lines.stderrLineStart
            )
        }
    }

    fun startJob(who: DecodedJWT, req: AppRequest.Start): String {
        return jobExecutionService.startJob(req, who)
    }
}

sealed class JobServiceException(message: String, val statusCode: HttpStatusCode) : RuntimeException(message) {
    data class NotFound(val entity: String) : JobServiceException("Not found: $entity", HttpStatusCode.NotFound)
    class NotReady : JobServiceException("Not ready yet", HttpStatusCode.BadRequest)
    class AlreadyComplete : JobServiceException("Job already complete", HttpStatusCode.BadRequest)
    class InvalidRequest(why: String) : JobServiceException("Bad request. $why", HttpStatusCode.BadRequest)
}

