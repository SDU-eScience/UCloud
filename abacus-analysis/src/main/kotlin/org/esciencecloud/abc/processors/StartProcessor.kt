package org.esciencecloud.abc.processors

import org.esciencecloud.abc.ApplicationDAO
import org.esciencecloud.abc.SBatchGenerator
import org.esciencecloud.abc.api.ApplicationDescription
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.HPCAppRequest
import org.esciencecloud.abc.internalError
import org.esciencecloud.abc.ssh.SSHConnection
import org.esciencecloud.abc.ssh.SimpleSSHConfig
import org.esciencecloud.abc.ssh.sbatch
import org.esciencecloud.abc.ssh.scpUpload
import org.esciencecloud.abc.stackTraceToString
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
        private val sshConfig: SimpleSSHConfig
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

    private fun handleStartEvent(storage: StorageConnection, request: HPCAppRequest.Start): Result<Long> {
        val app = with(request.application) { ApplicationDAO.findByNameAndVersion(name, version) } ?:
                return Error.notFound("Could not find application ${request.application}")

        val parameters = request.parameters

        // Must end in '/'. Otherwise resolve will do the wrong thing
        val homeDir = URI("file:///home/${sshConfig.user}/projects/")
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

        SSHConnection.connect(sshConfig).use { ssh ->
            // Transfer (validated) input files
            validatedFiles.forEach { upload ->
                var errorDuringUpload: Error<Long>? = null
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
                if (errorDuringUpload != null) return errorDuringUpload as Error<Long>
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
            return if (slurmJobId == null) {
                log.warn("Got back a null slurm job ID!")
                log.warn("Output from job: $output")
                log.warn("Generated slurm file:")
                log.warn(job)

                Error.internalError()
            } else {
                Ok(slurmJobId)
            }
        }
    }

    fun handle(connection: Result<StorageConnection>, request: HPCAppRequest.Start): HPCAppEvent {
        // TODO We still need a clear plan for how to deal with this during replays.
        val storage = connection.capture() ?:
                return HPCAppEvent.UnsuccessfullyCompleted(Error.invalidAuthentication())

        val result = handleStartEvent(storage, request)
        return when (result) {
            is Ok<Long> -> HPCAppEvent.Started(result.result)
            is Error<Long> -> HPCAppEvent.UnsuccessfullyCompleted(result)
        }
    }
}
