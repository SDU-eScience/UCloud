package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.jcraft.jsch.JSchException
import dk.sdu.cloud.app.StorageConfiguration
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ssh.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.CreateDirectoryRequest
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.StorageException
import dk.sdu.cloud.storage.model.FileStat
import dk.sdu.cloud.storage.model.StoragePath
import dk.sdu.cloud.tus.api.CreationCommand
import dk.sdu.cloud.tus.api.TusDescriptions
import dk.sdu.cloud.tus.api.internal.start
import dk.sdu.cloud.tus.api.internal.uploader
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.*

class JobExecutionService(
    private val cloud: RefreshingJWTAuthenticator,
    private val producer: MappedEventProducer<String, AppEvent>,
    private val irods: StorageConnectionFactory,
    private val irodsConfig: StorageConfiguration, // TODO This shouldn't be needed
    private val sBatchGenerator: SBatchGenerator,
    private val dao: JobsDAO,
    private val slurmPollAgent: SlurmPollAgent,
    private val sshConnectionPool: SSHConnectionPool,
    private val sshUser: String // TODO This won't be needed in final version
) {
    private var slurmListener: SlurmEventListener? = null

    fun initialize() {
        if (slurmListener != null) throw IllegalStateException("Already initialized!")
        slurmListener = slurmPollAgent.addListener {
            handleSlurmEvent(it)
        }
    }

    fun deinitialize() {
        val slurmListener = slurmListener ?: throw IllegalStateException("Not yet initialized!")
        slurmPollAgent.removeListener(slurmListener)
    }

    // TODO Maybe make the slurm and kafka interfaces behave the exact same way. Accept a callback for onScheduled?

    private fun handleSlurmEvent(event: SlurmEvent) {
        when (event) {
            is SlurmEventBegan -> {
                // TODO Not yet implemented
            }

            is SlurmEventFailed, is SlurmEventEnded -> {
                val slurmId = event.jobId
                val job = dao.transaction {
                    var jobToSlurm: JobInformation? = null
                    for (i in 0..3) {
                        if (i > 0) Thread.sleep(150)

                        jobToSlurm = findJobInformationBySlurmId(slurmId)
                        if (jobToSlurm != null) break
                    }

                    jobToSlurm
                }

                if (job == null) {
                    log.info("Unable to resolve completed Slurm to internal app. JobId = $slurmId, skipping...")
                    return
                }

                val app = ApplicationDAO.findByNameAndVersion(job.appName, job.appVersion) ?: run {
                    log.warn("Unable to find app for job: $job")
                    return
                }

                val tool = ToolDAO.findByNameAndVersion(app.tool.name, app.tool.version) ?: run {
                    log.warn("Unable to find tool for job: $app, $job")
                    return
                }

                val appWithDependencies = ApplicationWithOptionalDependencies(app, tool)
                val success = event is SlurmEventEnded

                // TODO These will have to result in a hard fail.
                // But we cannot do that due to appWithDependencies being required
                val sshUser = job.sshUser ?: run {
                    log.warn("sshUser == null")
                    return
                }
                val jobDirectory = job.jobDirectory ?: run {
                    log.warn("jobDirectory == null")
                    return
                }
                val workingDirectory = job.workingDirectory ?: run {
                    log.warn("workingDirectory == null")
                    return
                }

                runBlocking {
                    producer.emit(
                        AppEvent.CompletedInSlurm(
                            job.systemId,
                            System.currentTimeMillis(),
                            job.owner,
                            appWithDependencies,
                            sshUser,
                            jobDirectory,
                            workingDirectory,
                            success,
                            slurmId
                        )
                    )
                }
            }
        }
    }

    fun handleAppEvent(event: AppEvent) {
        try {
            // First we update DB status depending on the event
            // TODO Test updateJobBySystemId
            val nextState: AppState? = when (event) {
                is AppEvent.Validated -> AppState.VALIDATED
                is AppEvent.ScheduledAtSlurm -> AppState.SCHEDULED
                is AppEvent.CompletedInSlurm -> if (event.success) AppState.SUCCESS else AppState.FAILURE
                is AppEvent.Completed -> if (event.successful) AppState.SUCCESS else AppState.FAILURE
                else -> null
            }

            val message: String? = when (event) {
                is AppEvent.Completed -> event.message
                else -> null
            }

            if (nextState != null) dao.transaction { updateJobBySystemId(event.systemId, nextState, message) }

            // Then we handle the event (and potentially emit new events)
            val eventToEmit: AppEvent? = when (event) {
                is AppEvent.Validated -> prepareJob(event)

                is AppEvent.Prepared -> scheduleJob(event)

                is AppEvent.ScheduledAtSlurm -> handleScheduledEvent(event)

                is AppEvent.CompletedInSlurm ->
                    if (event.success) shipResults(event)
                    else goToCleanupState(event, "Failure in Slurm or non-zero exit code")

                is AppEvent.ExecutionCompleted -> cleanUp(event)

                is AppEvent.Completed -> {
                    // Do nothing
                    null
                }
            }

            if (eventToEmit != null) {
                runBlocking { producer.emit(eventToEmit) }
            }
        } catch (ex: Exception) {
            when (ex) {
                is JobException, is JSchException -> {
                    log.debug("Caught exception while handling app event: event = $event. ${ex.message}")

                    val isCritical = ex is JobInternalException || ex is JSchException
                    if (isCritical) {
                        log.warn("Caught critical exception while handling app event ($event).")
                        log.warn(ex.stackTraceToString())
                    }

                    val message = if (!isCritical) ex.message ?: DEFAULT_ERROR_MESSAGE else "Internal error"

                    val eventToEmit: AppEvent = if (event is AppEvent.NeedsRemoteCleaning) {
                        AppEvent.ExecutionCompleted(
                            event.systemId,
                            System.currentTimeMillis(),
                            event.owner,
                            event.appWithDependencies,
                            event.sshUser,
                            event.jobDirectory,
                            event.workingDirectory,
                            false,
                            message
                        )
                    } else {
                        AppEvent.Completed(
                            event.systemId,
                            System.currentTimeMillis(),
                            event.owner,
                            event.appWithDependencies,
                            false,
                            ex.message ?: DEFAULT_ERROR_MESSAGE
                        )
                    }

                    runBlocking { producer.emit(eventToEmit) }
                }

                else -> {
                    log.warn("Caught unexpected exception while handling app event: $event")
                    log.warn(ex.stackTraceToString())
                    log.warn("Rethrowing exception")
                    throw ex
                }
            }
        }
    }

    fun startJob(req: AppRequest.Start, principal: DecodedJWT): String {
        val validatedJob = validateJob(req, principal)
        dao.transaction {
            createJob(
                validatedJob.systemId,
                principal.subject,
                validatedJob.appWithDependencies.application
            )
        }
        runBlocking { producer.emit(validatedJob) }
        return validatedJob.systemId
    }

    private fun validateJob(req: AppRequest.Start, principal: DecodedJWT): AppEvent.Validated {
        val uuid = UUID.randomUUID().toString()

        val app = with(req.application) { ApplicationDAO.findByNameAndVersion(name, version) } ?: run {
            log.debug("Could not find application: ${req.application.name}@${req.application.version}")
            throw JobNotFoundException("${req.application.name}@${req.application.version}")
        }

        val tool = ToolDAO.findByNameAndVersion(app.tool.name, app.tool.version) ?: run {
            log.warn("Could not find tool for application: $app")
            throw JobInternalException("Internal error")
        }

        useIRods(principal) { connection ->
            // Must end in '/'. Otherwise resolve will do the wrong thing
            val homeDir = URI(PROJECT_DIRECTORY)
            val jobDir = homeDir.resolve("$uuid/")
            val workDir = jobDir.resolve("$PROJECT_FILES_DIRECTORY_NAME/")

            val files = validateAndCollectInputFiles(app, req.parameters, connection, workDir)

            val job = try {
                sBatchGenerator.generate(app, req, workDir.path)
            } catch (ex: IllegalArgumentException) {
                throw JobValidationException(ex.message ?: "Bad request")
            } catch (ex: Exception) {
                log.warn("Unable to generate slurm job:")
                log.warn(ex.stackTraceToString())
                throw ex
            }

            return AppEvent.Validated(
                uuid,
                System.currentTimeMillis(),
                principal.token,
                principal.subject,
                ApplicationWithOptionalDependencies(app, tool),
                jobDir.path,
                workDir.path,
                files,
                job
            )
        }
    }

    private fun validateAndCollectInputFiles(
        app: ApplicationDescription,
        applicationParameters: Map<String, Any>,
        storage: StorageConnection,
        workDir: URI
    ): List<ValidatedFileForUpload> {
        val result = ArrayList<ValidatedFileForUpload>()

        for (input in app.parameters.filterIsInstance<ApplicationParameter.InputFile>()) {
            val inputParameter = applicationParameters[input.name]

            val transferDescription = try {
                input.map(inputParameter)
            } catch (ex: IllegalArgumentException) {
                throw JobValidationException(ex.message ?: DEFAULT_ERROR_MESSAGE)
            } ?: continue
            val sourcePath = storage.paths.parseAbsolute(transferDescription.source, true)

            val stat = storage.fileQuery.stat(sourcePath).capture()
                    ?: throw JobValidationException("Missing file in storage: $sourcePath. Are you sure it exists?")

            // Resolve relative path against working directory. Ensure that file is still inside of
            // the working directory.
            val destinationPath = workDir.resolve(URI(transferDescription.destination)).normalize().path
            if (!destinationPath.startsWith(workDir.path)) {
                throw JobValidationException(
                    "Not allowed to leave working directory via relative paths. Please avoid using '..' in paths."
                )
            }

            val name = destinationPath.split("/").last()

            result.add(ValidatedFileForUpload(stat, name, destinationPath, sourcePath))
        }
        return result
    }

    /**
     * Prepares a job by shipping required information to the execution environment (typically HPC).
     *
     * It is the responsibility of the caller to ensure that cleanup is performed in the event of a failure.
     */
    private fun prepareJob(event: AppEvent.Validated): AppEvent.Prepared {
        log.debug("prepareJob(event = $event)")
        val principal = TokenValidation.validateOrNull(event.jwt) ?: throw JobNotAllowedException()
        sshConnectionPool.use {
            useIRods(principal) { storage ->
                event.files.forEach { upload ->
                    log.debug("Uploading file: $upload")
                    var errorDuringUpload: Exception? = null

                    val uploadStatus = scpUpload(
                        upload.stat.sizeInBytes,
                        upload.destinationFileName,
                        upload.destinationPath,
                        "0600"
                    ) {
                        // TODO Replace with download endpoint
                        try {
                            storage.files.get(upload.sourcePath, it)
                        } catch (ex: StorageException) {
                            errorDuringUpload = ex
                        }
                    }

                    if (errorDuringUpload != null) {
                        // TODO Don't treat all errors like this
                        log.warn("Caught error during upload:")
                        log.warn(errorDuringUpload!!.stackTraceToString())
                        throw JobInternalException("Internal error")
                    }

                    if (uploadStatus != 0) {
                        log.warn("Caught error during upload:")
                        log.warn("SCP Upload returned $uploadStatus")
                        throw JobInternalException("Internal error")
                    }

                    log.debug("$upload successfully completed")
                }
            }

            log.debug("Uploading SBatch job")
            val jobLocation = URI(event.jobDirectory).resolve("job.sh").normalize()
            val serializedJob = event.inlineSBatchJob.toByteArray()
            val uploadStatus = scpUpload(serializedJob.size.toLong(), "job.sh", jobLocation.path, "0600") {
                it.write(serializedJob)
            }

            if (uploadStatus != 0) {
                log.warn("Caught error during upload:")
                log.warn("SCP Upload returned $uploadStatus")
                throw JobInternalException("Internal error")
            }

            log.debug("SBatch job successfully uploaded")

            log.debug("${event.systemId} successfully prepared")
            return AppEvent.Prepared(
                event.systemId,
                System.currentTimeMillis(),
                event.jwt,
                event.appWithDependencies,
                sshUser,
                event.jobDirectory,
                event.workingDirectory,
                jobLocation.path
            )
        }
    }

    private fun scheduleJob(event: AppEvent.Prepared): AppEvent.ScheduledAtSlurm {
        sshConnectionPool.use {
            val (_, output, slurmJobId) = sbatch(event.jobScriptLocation)

            if (slurmJobId == null) {
                log.warn("Got back a null slurm job ID!")
                log.warn("Output from job: $output")

                throw JobInternalException("Unable to schedule slurm job")
            }

            return AppEvent.ScheduledAtSlurm(
                event.systemId,
                System.currentTimeMillis(),
                event.owner,
                event.appWithDependencies,
                event.sshUser,
                event.jobDirectory,
                event.workingDirectory,
                slurmJobId
            )
        }
    }

    private fun handleScheduledEvent(event: AppEvent.ScheduledAtSlurm): AppEvent? {
        slurmPollAgent.startTracking(event.slurmId)
        dao.transaction {
            updateJobWithSlurmInformation(
                event.systemId,
                event.sshUser,
                event.jobDirectory,
                event.workingDirectory,
                event.slurmId
            )
        }

        // New events are pushed by the Slurm polling agent
        return null
    }

    private fun shipResults(event: AppEvent.CompletedInSlurm): AppEvent.ExecutionCompleted {
        val jobId = event.systemId
        val owner = event.owner
        val outputDirectoryWithoutZone = "/home/$owner/Jobs/$jobId"
        val outputDirectory = "/${irodsConfig.zone}$outputDirectoryWithoutZone"
        sshConnectionPool.use {
            log.debug("Creating directory...")
            val directoryCreation = runBlocking {
                FileDescriptions.createDirectory.call(
                    CreateDirectoryRequest(outputDirectoryWithoutZone, owner),
                    cloud
                )
            }

            if (directoryCreation !is RESTResponse.Ok) {
                log.warn("Unable to create directory: $directoryCreation")
                throw JobInternalException("Internal error")
            } else {
                log.debug("Directory created successfully")
            }

            log.debug("Locating output files")
            val outputs = event.appWithDependencies.application.outputFileGlobs
                .flatMap {
                    lsWithGlob(event.workingDirectory, it)
                }
                .map {
                    val file = File(it.first)
                    FileTransferDescription(file.absolutePath, file.name)
                }
            log.debug("Found: $outputs")

            // TODO Refactor this
            // TODO Too many files?
            // Transfer output files
            for (transfer in outputs) {
                log.debug("Transferring file: $transfer")
                val workingDirectory = URI(event.workingDirectory)
                val source = workingDirectory.resolve(transfer.source)

                if (!source.path.startsWith(workingDirectory.path)) {
                    log.warn(
                        "File ${transfer.source} did not resolve to be within working directory " +
                                "($source versus $workingDirectory). Skipping this file"
                    )
                    continue
                }

                log.debug("Looking for file at ${source.path}")
                val sourceFile = stat(source.path)
                log.debug("Got back: $sourceFile")

                if (sourceFile == null) {
                    log.info("Could not find output file at: ${source.path}. Skipping file")
                    continue
                }

                val (fileToTransferFromHPC, fileToTransferSize) = if (!sourceFile.isDir) {
                    Pair(source.path, sourceFile.size)
                } else {
                    log.debug("Source file is a directory. Zipping it up")
                    val zipPath = source.path + ".zip"
                    val status = createZipFileOfDirectory(zipPath, source.path)

                    if (status != 0) {
                        log.warn("Unable to create zip archive of output!")
                        log.warn("Path: ${source.path}")
                        log.warn("Status: $status")
                        throw JobInternalException("Unable to create output zip. Status = $status")
                    }

                    val zipStat = stat(zipPath) ?: run {
                        log.warn("Unable to find zip file after creation. Expected it at: $zipPath")
                        throw JobInternalException("Internal error")
                    }

                    Pair(zipPath, zipStat.size)
                }

                log.debug("Downloading file from $fileToTransferFromHPC")

                val upload = runBlocking {
                    val payload = CreationCommand(
                        fileName = transfer.destination,
                        owner = owner,
                        location = outputDirectory,
                        length = fileToTransferSize,
                        sensitive = false // TODO Sensitivity
                    )
                    log.debug("Upload to create at SDUCloud: $payload")
                    TusDescriptions.create.call(payload, cloud)
                } as? RESTResponse.Ok ?: throw IllegalStateException("Upload failed")

                val uploadLocation = upload.response.headers["Location"]!!
                log.debug("Upload target is: $uploadLocation")

                if (sourceFile.size >= Int.MAX_VALUE) {
                    log.warn("sourceFile.size (${sourceFile.size}) >= Int.MAX_VALUE. Currently not supported")
                    throw JobInternalException("Output file too large")
                }

                scpDownload(source.path) {
                    TusDescriptions.uploader(it, uploadLocation, sourceFile.size.toInt(), cloud).start {
                        log.debug("$jobId: $it/${sourceFile.size} bytes transferred")
                    }
                }
            }

            return AppEvent.ExecutionCompleted(
                event.systemId,
                System.currentTimeMillis(),
                event.owner,
                event.appWithDependencies,
                event.sshUser,
                event.jobDirectory,
                event.workingDirectory,
                true,
                "OK"
            )
        }
    }

    private fun cleanUp(event: AppEvent.ExecutionCompleted): AppEvent {
        sshConnectionPool.use {
            val removeStatus = rm(event.jobDirectory, recurse = true, force = true)
            if (removeStatus != 0) {
                log.warn("Could not successfully delete directory of job!")
                log.warn("Event is: $event")
            }

            return AppEvent.Completed(
                event.systemId,
                System.currentTimeMillis(),
                event.owner,
                event.appWithDependencies,
                event.successful,
                event.message
            )
        }
    }

    private fun <T> goToCleanupState(event: T, message: String): AppEvent
            where T : AppEvent, T : AppEvent.NeedsRemoteCleaning {
        return AppEvent.ExecutionCompleted(
            event.systemId,
            event.timestamp,
            event.owner,
            event.appWithDependencies,
            event.sshUser,
            event.jobDirectory,
            event.workingDirectory,
            false,
            message
        )
    }

    private inline fun <T> useIRods(principal: DecodedJWT, body: (StorageConnection) -> T): T {
        val connection = try {
            irods.createForAccount(
                principal.subject!!,
                principal.token
            ).orThrow()
        } catch (ex: Exception) {
            log.warn("Unable to create connection to iRODS")
            log.warn(ex.stackTraceToString())

            throw JobInternalException("Could not connect to storage back-end")
        }

        return connection.use(body)
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobService::class.java)
        private const val DEFAULT_ERROR_MESSAGE = "An error has occurred"
        private const val PROJECT_DIRECTORY = "file:///scratch/sduescience/projects/"
        private const val PROJECT_FILES_DIRECTORY_NAME = "files"
    }
}

sealed class JobException(message: String, val statusCode: HttpStatusCode) : RuntimeException(message)
class JobValidationException(message: String) : JobException(message, HttpStatusCode.BadRequest)
class JobInternalException(message: String) : JobException(message, HttpStatusCode.InternalServerError)
class JobNotFoundException(entity: String) : JobException("Not found: $entity", HttpStatusCode.NotFound)
class JobNotAllowedException : JobException("Not allowed", HttpStatusCode.Unauthorized)
data class ValidatedFileForUpload(
    val stat: FileStat,
    val destinationFileName: String,
    val destinationPath: String,
    val sourcePath: StoragePath
)
