package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.jcraft.jsch.JSchException
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ssh.*
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.RequestOneTimeToken
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.withCausedBy
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.util.*

class JobExecutionService<DBSession>(
    cloud: RefreshingJWTAuthenticatedCloud,

    private val producer: MappedEventProducer<String, AppEvent>,

    private val sBatchGenerator: SBatchGenerator,

    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDAO<DBSession>,
    private val appDao: ApplicationDAO<DBSession>,

    private val slurmPollAgent: SlurmPollAgent,
    private val sshConnectionPool: SSHConnectionPool,
    private val sshUser: String // TODO This won't be needed in final version
) {
    private val cloudContext: CloudContext = cloud.parent
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
        val slurmId = event.jobId
        val job = db.withTransaction {
            var jobToSlurm: JobInformation? = null
            for (i in 0..3) {
                if (i > 0) Thread.sleep(150)

                jobToSlurm = jobDao.findJobInformationBySlurmId(it, slurmId)
                if (jobToSlurm != null) break
            }

            jobToSlurm
        }

        if (job == null) {
            log.info("Unable to resolve completed Slurm to internal app. JobId = $slurmId, skipping...")
            return
        }

        when (event) {
            is SlurmEventRunning -> {
                if (job.state == AppState.RUNNING) return

                db.withTransaction {
                    jobDao.updateJobBySystemId(it, job.systemId, AppState.RUNNING, "Running...")
                }
            }

            is SlurmEventTimeout, is SlurmEventFailed, is SlurmEventEnded -> {
                val appWithDependencies = try {
                    db.withTransaction { appDao.findByNameAndVersion(it, job.owner, job.appName, job.appVersion) }
                } catch (ex: ApplicationException.NotFound) {
                    return
                }

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
                    // TODO Should implement error codes here, before we lose them
                    producer.emit(
                        AppEvent.CompletedInSlurm(
                            job.systemId,
                            System.currentTimeMillis(),
                            job.owner,
                            appWithDependencies,
                            sshUser,
                            jobDirectory,
                            workingDirectory,
                            job.jwt,
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
            runBlocking {
                // First we update DB state depending on the event
                // TODO Test updateJobBySystemId
                val nextState: AppState? = when (event) {
                    is AppEvent.Validated -> AppState.VALIDATED
                    is AppEvent.ScheduledAtSlurm -> AppState.SCHEDULED
                    is AppEvent.Completed -> if (event.successful) AppState.SUCCESS else AppState.FAILURE
                    else -> null
                }

                val message: String? = when (event) {
                    is AppEvent.Completed -> event.message
                    else -> null
                }

                if (nextState != null) db.withTransaction {
                    jobDao.updateJobBySystemId(
                        it,
                        event.systemId,
                        nextState,
                        message
                    )
                }

                // Then we handle the event (and potentially emit new events)
                val eventToEmit: AppEvent? = when (event) {
                    is AppEvent.Validated -> prepareJob(event, cloudContext)

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
                    producer.emit(eventToEmit)
                }
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

    suspend fun startJob(req: AppRequest.Start, principal: DecodedJWT, cloud: AuthenticatedCloud): String {
        val validatedJob = validateJob(req, principal, cloud)
        db.withTransaction {
            jobDao.createJob(
                it,
                principal.subject,
                validatedJob.systemId,
                validatedJob.appWithDependencies.description.info.name,
                validatedJob.appWithDependencies.description.info.version,
                principal.token
            )
        }
        runBlocking { producer.emit(validatedJob) }
        return validatedJob.systemId
    }

    private suspend fun validateJob(
        req: AppRequest.Start,
        principal: DecodedJWT,
        cloud: AuthenticatedCloud
    ): AppEvent.Validated {
        val uuid = UUID.randomUUID().toString()

        val app = try {
            db.withTransaction {
                with(req.application) { appDao.findByNameAndVersion(it, principal.subject, name, version) }
            }
        } catch (ex: ApplicationException.NotFound) {
            log.debug("Could not find application: ${req.application.name}@${req.application.version}")
            throw JobNotFoundException("${req.application.name}@${req.application.version}")
        }

        // Must end in '/'. Otherwise resolve will do the wrong thing
        val homeDir = URI(PROJECT_DIRECTORY)
        val jobDir = homeDir.resolve("$uuid/")
        val workDir = jobDir.resolve("$PROJECT_FILES_DIRECTORY_NAME/")

        val files = validateAndCollectInputFiles(app, req.parameters, workDir, cloud)

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
            app,
            jobDir.path,
            workDir.path,
            files,
            job
        )
    }

    private inline fun <T, E> RESTResponse<T, E>.orThrowOnError(
        onError: (RESTResponse.Err<T, E>) -> Nothing
    ): RESTResponse.Ok<T, E> {
        return when (this) {
            is RESTResponse.Ok -> this
            is RESTResponse.Err -> onError(this)
            else -> throw IllegalStateException()
        }
    }

    private suspend fun validateAndCollectInputFiles(
        app: Application,
        applicationParameters: Map<String, Any>,
        workDir: URI,
        cloud: AuthenticatedCloud
    ): List<ValidatedFileForUpload> {
        val result = ArrayList<ValidatedFileForUpload>()

        loop@ for (input in app.description.parameters) {
            val isDirectory = input is ApplicationParameter.InputDirectory
            val parameter: ApplicationParameter<FileTransferDescription> = when (input) {
                is ApplicationParameter.InputFile -> input
                is ApplicationParameter.InputDirectory -> input
                else -> continue@loop
            }

            val inputParameter = applicationParameters[parameter.name]

            val transferDescription = try {
                parameter.map(inputParameter)
            } catch (ex: IllegalArgumentException) {
                throw JobValidationException(ex.message ?: DEFAULT_ERROR_MESSAGE)
            } ?: continue
            val sourcePath = transferDescription.source
            val stat = FileDescriptions.stat.call(FindByPath(sourcePath), cloud)
                .orThrowOnError {
                    throw JobValidationException("Missing file in storage: $sourcePath. Are you sure it exists?")
                }
                .result

            if (!isDirectory && stat.type != FileType.FILE) {
                throw JobValidationException(
                    "Expected a file for ${input.name}, but instead got " +
                            "a ${stat.type.name.toLowerCase()} (value: $sourcePath)"
                )
            }

            if (isDirectory && stat.type != FileType.DIRECTORY) {
                throw JobValidationException(
                    "Expected a directory for ${input.name}, but instead got " +
                            "a ${stat.type.name.toLowerCase()} (value: $sourcePath)"
                )
            }

            // Resolve relative path against working directory. Ensure that file is still inside of
            // the working directory.
            val destinationPath = File(workDir.toURL().path, transferDescription.destination).normalize().path
            if (!destinationPath.startsWith(workDir.path)) {
                throw JobValidationException(
                    "Not allowed to leave working directory via relative paths. Please avoid using '..' in paths."
                )
            }

            val name = destinationPath.split("/").last()

            result.add(
                ValidatedFileForUpload(
                    stat,
                    name,
                    destinationPath,
                    sourcePath,
                    if (isDirectory) FileForUploadArchiveType.ZIP else null
                )
            )
        }
        return result
    }

    private fun InputStream.copyToWithDebugging(target: OutputStream, bufferSize: Int) {
        var nextTarget = 1024 * 1024
        target.use { out ->
            this.use { ins ->
                var writtenInTotal = 0L
                val buffer = ByteArray(bufferSize)
                var hasMoreData = true
                while (hasMoreData) {
                    var ptr = 0
                    while (ptr < buffer.size && hasMoreData) {
                        val read = ins.read(buffer, ptr, buffer.size - ptr)
                        if (read <= 0) {
                            hasMoreData = false
                            break
                        }
                        ptr += read
                        writtenInTotal += read
                        if (writtenInTotal >= nextTarget) {
                            log.debug("Wrote $writtenInTotal bytes")
                            nextTarget += 1024 * 1024
                        }
                    }
                    out.write(buffer, 0, ptr)
                }
            }
        }
    }

    /**
     * Prepares a job by shipping required information to the execution environment (typically HPC).
     *
     * It is the responsibility of the caller to ensure that cleanup is performed in the event of a failure.
     */
    private suspend fun prepareJob(event: AppEvent.Validated, cloud: CloudContext): AppEvent.Prepared {
        log.debug("prepareJob(event = $event)")
        // TODO This might not be valid for the entire duration if we have long transfers
        val validateOrNull = TokenValidation.validateOrNull(event.jwt)
        validateOrNull ?: throw JobNotAllowedException()
        log.debug("Using token: ${validateOrNull.subject}")

        val userCloud = JWTAuthenticatedCloud(cloud, event.jwt).withCausedBy(event.systemId)
        sshConnectionPool.use {
            mkdir(event.workingDirectory, true)
                .takeIf { it != 0 }
                ?.let {
                    throw JobInternalException("Could not create ${event.workingDirectory}. Returned state: $it")
                }

            event.files.forEach { upload ->
                log.debug("Uploading file: $upload")

                val token =
                    (AuthDescriptions.requestOneTimeTokenWithAudience.call(
                        RequestOneTimeToken(DOWNLOAD_FILE_SCOPE), userCloud
                    ) as? RESTResponse.Ok)?.result?.accessToken ?: run {
                        log.warn("Unable to request a new one time token for download!")
                        throw JobInternalException("Unable to upload input files to Abacus")
                    }

                val fileDownload = (FileDescriptions.download.call(
                    DownloadByURI(upload.sourcePath, token),
                    userCloud
                ) as? RESTResponse.Ok)?.response?.content?.toInputStream() ?: run {
                    log.warn("Unable to download file: ${upload.sourcePath}")
                    throw JobInternalException("Unable to upload input files to Abacus")
                }

                val (uploadSize, fileInputStream) = if (upload.needsExtractionOfType != null) {
                    // Extraction requires us to know the precise size before we send to HPC. This information
                    // needs to be transferred before the file. We cannot do this in memory since we don't know
                    // how large the file is. Thus we have to go through the disk.
                    //
                    // This does, however, make it significantly slower.

                    log.debug("File needs extraction writing to file first!")
                    val output = Files.createTempFile("apparchive", ".zip").toFile()
                    fileDownload.copyToWithDebugging(output.outputStream(), 1024 * 64)
                    Pair(output.length(), output.inputStream())
                } else {
                    Pair(upload.stat.size, fileDownload)
                }

                val uploadStatus = try {
                    scpUpload(
                        uploadSize,
                        upload.destinationFileName,
                        upload.destinationPath,
                        "0600"
                    ) {
                        fileInputStream.copyToWithDebugging(it, 1024 * 64)
                    }
                } catch (ex: Exception) {
                    // TODO Don't treat all errors like this
                    log.warn("Caught error during upload:")
                    log.warn(ex.stackTraceToString())
                    throw JobInternalException("Internal error")
                }

                if (uploadStatus != 0) {
                    log.warn("Caught error during upload:")
                    log.warn("SCP Upload returned $uploadStatus")
                    throw JobInternalException("Internal error")
                }

                val zipStatus: Int = when (upload.needsExtractionOfType) {
                    FileForUploadArchiveType.ZIP -> {
                        unzip(upload.destinationPath, File(upload.destinationPath).parent)
                    }

                    null -> {
                        // Do nothing
                        0
                    }
                }

                if (zipStatus >= 3) {
                    log.warn("Unable to extract input files")
                    throw JobInternalException("Internal error")
                }

                log.debug("$upload successfully completed")
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
        db.withTransaction {
            jobDao.updateJobWithSlurmInformation(
                it,
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

    private fun shipResults(
        event: AppEvent.CompletedInSlurm
    ): AppEvent.ExecutionCompleted {
        val jobId = event.systemId
        val owner = event.owner
        val outputDirectory = "/home/$owner/Jobs/$jobId"
        val cloud = JWTAuthenticatedCloud(cloudContext, event.jwt)

        sshConnectionPool.use {
            log.debug("Creating directory...")
            val directoryCreation = runBlocking {
                FileDescriptions.createDirectory.call(
                    CreateDirectoryRequest(outputDirectory, owner),
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
            val outputs = event.appWithDependencies.description.outputFileGlobs
                .flatMap {
                    lsWithGlob(event.workingDirectory, it)
                }
                .map {
                    val file = File(it.fileName)
                    file.absolutePath
                }
            log.debug("Found: $outputs")

            // TODO Refactor this
            // TODO Too many files?
            // Transfer output files
            for (transfer in outputs) {
                log.debug("Transferring file: $transfer")
                val workingDirectory = URI(event.workingDirectory)
                val source = workingDirectory.resolve(transfer)

                if (!source.path.startsWith(workingDirectory.path)) {
                    log.warn(
                        "File $transfer did not resolve to be within working directory " +
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

                val (fileToTransferFromHPC, _) = if (!sourceFile.isDir) {
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

                if (sourceFile.size >= Int.MAX_VALUE) {
                    log.warn("sourceFile.size (${sourceFile.size}) >= Int.MAX_VALUE. Currently not supported")
                    throw JobInternalException("Output file too large")
                }

                val transferStatus =
                    try {
                        scpDownload(fileToTransferFromHPC) { ins ->
                            MultiPartUploadDescriptions.callUpload(
                                cloudContext,
                                event.jwt,
                                outputDirectory.removeSuffix("/") + "/" + fileToTransferFromHPC.substringAfterLast('/'),
                                writer = { out ->
                                    // Closing 'out' is not something the HTTP client likes.
                                    val buffer = ByteArray(1024 * 64)
                                    var hasMoreData = true
                                    while (hasMoreData) {
                                        var ptr = 0
                                        while (ptr < buffer.size && hasMoreData) {
                                            val read = ins.read(buffer, ptr, buffer.size - ptr)
                                            if (read <= 0) {
                                                hasMoreData = false
                                                break
                                            }
                                            ptr += read
                                        }
                                        out.write(buffer, 0, ptr)
                                    }
                                    out.flush()
                                }
                            )

                        }
                    } catch (ex: Exception) {
                        log.warn("Caught exception while uploading file to SDUCloud")
                        log.warn(ex.stackTraceToString())
                        throw JobInternalException("Upload failed. ${ex.message}")
                    }

                if (transferStatus != 0) {
                    throw JobInternalException("Upload failed. Transfer state != 0 ($transferStatus)")
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
class JobBadApplication : JobException("Application not found", HttpStatusCode.BadRequest)

data class ValidatedFileForUpload(
    val stat: StorageFile,
    val destinationFileName: String,
    val destinationPath: String,
    val sourcePath: String,
    val needsExtractionOfType: FileForUploadArchiveType?
)

enum class FileForUploadArchiveType {
    ZIP
}
