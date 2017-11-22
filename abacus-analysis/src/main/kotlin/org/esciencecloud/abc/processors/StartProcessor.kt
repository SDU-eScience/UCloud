package org.esciencecloud.abc.processors

import org.esciencecloud.abc.*
import org.esciencecloud.abc.api.ApplicationDescription
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.HPCAppRequest
import org.esciencecloud.abc.ssh.*
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageException
import org.esciencecloud.storage.model.FileStat
import org.esciencecloud.storage.model.StoragePath
import org.slf4j.LoggerFactory
import java.net.URI

class StartProcessor(
        private val sBatchGenerator: SBatchGenerator,
        private val sshPool: SSHConnectionPool,
        private val sshUser: String
) {
    private val log = LoggerFactory.getLogger(StartProcessor::class.java)

    private data class ValidatedFileForUpload(
            val stat: FileStat,
            val destinationFileName: String,
            val destinationPath: String,
            val sourcePath: StoragePath
    )

    private fun validateInputFiles(app: ApplicationDescription, parameters: Map<String, Any>,
                                   storage: StorageConnection, workDir: URI): Result<List<ValidatedFileForUpload>> {
        val result = ArrayList<ValidatedFileForUpload>()

        for (input in app.parameters.filterIsInstance<ApplicationParameter.InputFile>()) {
            val inputParameter = parameters[input.name] ?:
                    return Error.invalidMessage("Missing input parameter: " + input.name)

            val transferDescription = input.map(inputParameter)
            val sourcePath = StoragePath.fromURI(transferDescription.source)

            val stat = storage.fileQuery.stat(sourcePath).capture() ?:
                    return Error.invalidMessage("Missing file in storage: $sourcePath. Are you sure it exists?")

            // Resolve relative path against working directory. Ensure that file is still inside of
            // the working directory.
            val destinationPath = workDir.resolve(URI(transferDescription.destination)).normalize().path
            if (!destinationPath.startsWith(workDir.path)) {
                return Error.invalidMessage("Not allowed to leave working " +
                        "directory via relative paths. Please avoid using '..' in paths.")
            }

            val name = destinationPath.split("/").last()

            result.add(ValidatedFileForUpload(stat, name, destinationPath, sourcePath))
        }
        return Ok(result)
    }

    private fun handleStartEvent(
            storage: StorageConnection,
            request: Request<HPCAppRequest.Start>
    ): Result<HPCAppEvent.Pending> {
        val event = request.event
        val app = with(event.application) { ApplicationDAO.findByNameAndVersion(name, version) } ?:
                return Error.notFound("Could not find application ${event.application}")

        val parameters = event.parameters

        // Must end in '/'. Otherwise resolve will do the wrong thing
        val homeDir = URI("file:///home/$sshUser/projects/")
        val jobDir = homeDir.resolve("jobid/")
        val workDir = jobDir.resolve("files/")

        val job = try {
            sBatchGenerator.generate(app, parameters, workDir.path)
        } catch (ex: Exception) {
            // TODO Should probably return a result?
            log.warn("Unable to generate slurm job:")
            log.warn(ex.stackTraceToString())
            return Error.internalError()
        }

        val validatedFiles = validateInputFiles(app, parameters, storage, workDir).capture() ?:
                return Result.lastError()

        return sshPool.borrow { ssh ->
            // Transfer (validated) input files
            validatedFiles.forEach { upload ->
                var errorDuringUpload: Error<HPCAppEvent.Pending>? = null
                // TODO FIXME Destination name should be escaped
                // TODO FIXME Destination name should be escaped
                // TODO FIXME Destination name should be escaped
                ssh.scpUpload(upload.stat.sizeInBytes, upload.destinationFileName, upload.destinationPath,
                        "0644") {
                    try {
                        storage.files.get(upload.sourcePath, it) // TODO This will need to change
                    } catch (ex: StorageException) {
                        errorDuringUpload = Error.permissionDenied("Not allowed to access file: ${upload.sourcePath}")
                    }
                }
                if (errorDuringUpload != null) return@borrow errorDuringUpload as Error<HPCAppEvent.Pending>
            }

            // Transfer job file
            val jobLocation = jobDir.resolve("job.sh").normalize()
            val serializedJob = job.toByteArray()
            ssh.scpUpload(serializedJob.size.toLong(), "job.sh", jobLocation.path, "0644") {
                it.write(serializedJob)
            }

            // Submit job file
            val (_, output, slurmJobId) = ssh.sbatch(jobLocation.path)
            // TODO Need to revisit the idea of status codes
            // Crashing right here would cause incorrect resubmission of job to HPC

            if (slurmJobId == null) {
                log.warn("Got back a null slurm job ID!")
                log.warn("Output from job: $output")
                log.warn("Generated slurm file:")
                log.warn(job)

                Error.internalError()
            } else {
                Ok(HPCAppEvent.Pending(slurmJobId, jobDir.path, workDir.path, request))
            }
        }
    }

    fun handle(connection: Result<StorageConnection>, request: Request<HPCAppRequest.Start>): HPCAppEvent {
        // TODO We still need a clear plan for how to deal with this during replays.
        val storage = connection.capture() ?:
                return HPCAppEvent.UnsuccessfullyCompleted(Error.invalidAuthentication())

        val handledEvent = handleStartEvent(storage, request)
        return when (handledEvent) {
            is Ok  -> handledEvent.result
            is Error -> HPCAppEvent.UnsuccessfullyCompleted(handledEvent)
        }
    }
}
