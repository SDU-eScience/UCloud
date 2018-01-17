package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.services.AuthenticatedStream
import dk.sdu.cloud.app.services.SBatchGenerator
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.sbatch
import dk.sdu.cloud.app.services.ssh.scpUpload
import dk.sdu.cloud.app.util.BashEscaper
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.StorageException
import dk.sdu.cloud.storage.model.FileStat
import dk.sdu.cloud.storage.model.StoragePath
import org.slf4j.LoggerFactory
import stackTraceToString
import java.net.URI

class StartCommandProcessor(
        private val connectionFactory: StorageConnectionFactory,
        private val sBatchGenerator: SBatchGenerator,
        private val sshPool: SSHConnectionPool,
        private val sshUser: String,
        private val appRequests: AuthenticatedStream<String, AppRequest>
) {
    private val log = LoggerFactory.getLogger(StartCommandProcessor::class.java)

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
            val inputParameter = parameters[input.name]

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

    private fun handleStartCommand(
            storage: StorageConnection,
            request: KafkaRequest<AppRequest.Start>
    ): HPCAppEvent {
        val event = request.event
        val app = with(event.application) { ApplicationDAO.findByNameAndVersion(name, version) } ?:
                return run {
                    log.debug("Could not find application: ${event.application.name}@ ${event.application.version}")
                    HPCAppEvent.UnsuccessfullyCompleted
                }

        val parameters = event.parameters

        // Must end in '/'. Otherwise resolve will do the wrong thing
        val homeDir = URI("file:///home/$sshUser/projects/")
        val jobDir = homeDir.resolve("${request.header.uuid}/")
        val workDir = jobDir.resolve("files/")

        val (mkdirStatus, text) = sshPool.use {
            execWithOutputAsText("mkdir -p ${BashEscaper.safeBashArgument(workDir.path)}")
        }

        if (mkdirStatus != 0 && mkdirStatus != -1) { // TODO This is bugged. Sometimes status comes back as -1
            log.warn("Unable to create directory: ${workDir.path}")
            log.warn("Got back status: $mkdirStatus")
            log.warn("stdout: $text")
            return HPCAppEvent.UnsuccessfullyCompleted
        }


        val job = try {
            sBatchGenerator.generate(app, parameters, workDir.path)
        } catch (ex: Exception) {
            log.warn("Unable to generate slurm job:")
            log.warn(ex.stackTraceToString())
            return HPCAppEvent.UnsuccessfullyCompleted
        }

        val validatedFiles = validateInputFiles(app, parameters, storage, workDir).capture() ?:
                return run {
                    log.debug(Result.lastError<Any>().message)
                    HPCAppEvent.UnsuccessfullyCompleted
                }

        return sshPool.use {
            // Transfer (validated) input files
            validatedFiles.forEach { upload ->
                var errorDuringUpload: Error<HPCAppEvent.Pending>? = null
                scpUpload(
                        upload.stat.sizeInBytes,
                        upload.destinationFileName,
                        upload.destinationPath,
                        "0644"
                ) {
                    try {
                        storage.files.get(upload.sourcePath, it)
                    } catch (ex: StorageException) {
                        errorDuringUpload = Error.permissionDenied("Not allowed to access file: ${upload.sourcePath}")
                    }
                }
                if (errorDuringUpload != null) return@use run {
                    log.debug("Caught error during upload:")
                    log.debug(errorDuringUpload!!.message)
                    HPCAppEvent.UnsuccessfullyCompleted
                }
            }

            // Transfer job file
            val jobLocation = jobDir.resolve("job.sh").normalize()
            val serializedJob = job.toByteArray()
            scpUpload(serializedJob.size.toLong(), "job.sh", jobLocation.path, "0644") {
                it.write(serializedJob)
            }

            // Submit job file
            val (_, output, slurmJobId) = sbatch(jobLocation.path)
            // TODO Need to revisit the idea of status codes
            // Crashing right here would cause incorrect resubmission of job to HPC

            if (slurmJobId == null) {
                log.warn("Got back a null slurm job ID!")
                log.warn("Output from job: $output")
                log.warn("Generated slurm file:")
                log.warn(job)

                HPCAppEvent.UnsuccessfullyCompleted
            } else {
                HPCAppEvent.Pending(slurmJobId, jobDir.path, workDir.path, request)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handle(connection: StorageConnection, request: KafkaRequest<AppRequest>): HPCAppEvent =
            when (request.event) {
                is AppRequest.Start -> {
                    log.info("Handling event: $request")
                    handleStartCommand(connection, request as KafkaRequest<AppRequest.Start>).also {
                        log.info("${request.header.uuid}: $it")
                    }
                }

                is AppRequest.Cancel -> {
                    // TODO This won't really cancel anything
                    HPCAppEvent.UnsuccessfullyCompleted
                }
            }

    fun init() {
        appRequests.respond(
                target = HPCStreams.AppEvents,

                onAuthenticated = { _, e ->
                    val connection = connectionFactory.createForAccount(
                            e.decoded.subject!!,
                            e.header.performedFor
                    ).capture()

                    connection?.use { handle(it, e.originalRequest) } ?: HPCAppEvent.UnsuccessfullyCompleted
                },

                onUnauthenticated = { _, _ ->
                    HPCAppEvent.UnsuccessfullyCompleted
                }
        )
    }
}
