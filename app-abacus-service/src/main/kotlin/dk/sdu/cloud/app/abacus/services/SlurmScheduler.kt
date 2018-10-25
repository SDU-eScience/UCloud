package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.sbatch
import dk.sdu.cloud.app.abacus.services.ssh.scpUpload
import dk.sdu.cloud.app.abacus.services.ssh.use
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.StateChangeRequest
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.nio.file.Files

/**
 * Class for interacting with slurm
 */
class SlurmScheduler<DBSession>(
    private val sshConnectionPool: SSHConnectionPool,
    private val jobFileService: JobFileService,
    private val sBatchGenerator: SBatchGenerator,
    private val slurmPollAgent: SlurmPollAgent,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val cloud: AuthenticatedCloud,
    private val reservation: String? = null
) {
    suspend fun schedule(job: VerifiedJob) {
        val sbatchScript = sBatchGenerator.generate(job, jobFileService.filesDirectoryForJob(job.id).absolutePath)
        val file = Files.createTempFile("sbatch", ".sh").toFile().also { it.writeText(sbatchScript) }
        val fileLocation = jobFileService.rootDirectoryForJob(job.id).resolve("sbatch.sh").absolutePath
        val slurmJobId: Long = sshConnectionPool.use {
            scpUpload(file, fileLocation, "0700")
            if (reservation != null) {
                log.info("SCHEDULING JOB WITH A RESERVATION '$reservation'")
            }
            val (_, output, slurmJobId) = sbatch(fileLocation, reservation = reservation)

            if (slurmJobId == null) {
                log.warn("Got back a null slurm job id!")
                log.warn("Output from job: $output")
                throw SlurmException.BadResponse()
            }
            slurmJobId
        }

        // TODO Restarting the service will cause this information to be lost
        db.withTransaction { jobDao.insertMapping(it, job.id, slurmJobId) }
        slurmPollAgent.startTracking(slurmJobId)

        ComputationCallbackDescriptions.requestStateChange.call(StateChangeRequest(job.id, JobState.SCHEDULED), cloud)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

sealed class SlurmException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class BadResponse : SlurmException("Got a bad response from slurm!", HttpStatusCode.InternalServerError)
}
