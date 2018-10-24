package dk.sdu.cloud.app.services

/*
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.linesInRange
import dk.sdu.cloud.app.services.ssh.use
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import java.io.File
import kotlin.math.min

private const val FIXED_MAX_LINES = 1000

class JobService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: JobDAO<DBSession>,
    private val sshPool: SSHConnectionPool,
    private val jobExecutionService: JobExecutionService<DBSession>
) {
    fun recentJobs(who: String, paginationRequest: PaginationRequest): Page<JobWithStatus> =
        db.withTransaction { dao.findAllJobsWithStatus(it, who, paginationRequest.normalize()) }

    fun findJobById(who: String, jobId: String): JobWithStatus? =
        db.withTransaction { dao.findJobById(it, who, jobId) }

    fun findJobForInternalUseById(who: String, jobId: String): JobInformation? =
        db.withTransaction { dao.findJobInformationByJobId(it, who, jobId) }

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
            val stdOutLines = min(lines.stdoutMaxLines, FIXED_MAX_LINES)
            val stdErrLines = min(lines.stderrMaxLines, FIXED_MAX_LINES)

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

*/
