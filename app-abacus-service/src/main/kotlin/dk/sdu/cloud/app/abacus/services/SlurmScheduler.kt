package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.sbatch
import dk.sdu.cloud.app.abacus.services.ssh.scpUpload
import dk.sdu.cloud.app.abacus.services.ssh.use
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode
import java.nio.file.Files

/**
 * Class for interacting with slurm
 */
class SlurmScheduler(
    private val sshConnectionPool: SSHConnectionPool,
    private val jobFileService: JobFileService,
    private val sBatchGenerator: SBatchGenerator,
    private val slurmPollAgent: SlurmPollAgent
) {
    fun schedule(job: VerifiedJob) {
        val sbatchScript = sBatchGenerator.generate(job, jobFileService.filesDirectoryForJob(job.id).absolutePath)
        val file = Files.createTempFile("sbatch", ".sh").toFile().also { it.writeText(sbatchScript) }
        val fileLocation = jobFileService.rootDirectoryForJob(job.id).resolve("sbatch.sh").absolutePath
        sshConnectionPool.use {
            scpUpload(file, fileLocation, "0700")
            val (_, output, slurmJobId) = sbatch(fileLocation)

            if (slurmJobId == null) {
                log.warn("Got back a null slurm job id!")
                log.warn("Output from job: $output")
                throw SlurmException.BadResponse()
            }

            // TODO Insert a mapping between system ID and slurm ID
            // TODO Restarting the service will cause this information to be lost
            slurmPollAgent.startTracking(slurmJobId)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

sealed class SlurmException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class BadResponse : SlurmException("Got a bad response from slurm!", HttpStatusCode.InternalServerError)
}
