package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.UserClientFactory
import dk.sdu.cloud.app.orchestrator.api.files
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class JobDirectory(val path: String)

class JobFileService(
    private val userClientFactory: UserClientFactory,
    private val serviceClient: AuthenticatedClient,
    private val appStoreCache: AppStoreCache,
) {
    suspend fun initializeResultFolder(jobWithToken: VerifiedJobWithAccessToken): JobDirectory {
        val (job, refreshToken) = jobWithToken
        val userClient = userClientFactory(refreshToken)

        val project = job.owner.project
        val jobsFolder = jobsFolder(job.owner.createdBy, project)
        if (project != null) {
            // Create the personal repository lazily
            FileDescriptions.createPersonalRepository.call(
                CreatePersonalRepositoryRequest(project, job.owner.launchedBy),
                serviceClient
            )
        }

        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(jobsFolder, null),
            userClient
        )

        val path = jobFolder(jobWithToken, true)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path.parent()),
            userClient
        )

        val dirResp = FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path, null),
            userClient
        ).throwIfInternal()

        if (!dirResp.statusCode.isSuccess()) {
            // Throw if we didn't allow this case
            throw RPCException.fromStatusCode(dirResp.statusCode)
        }

        return JobDirectory(path)
    }

    /**
     * Export the parameter file to the job directory at the beginning of the job.
     *
     * @param jobFolder The folder to put the parameters file in
     * @param jobWithToken The job with access token
     * @param rawParameters The raw input parameters before parsing
     */
    @OptIn(KtorExperimentalAPI::class)
    suspend fun exportParameterFile(
        jobFolder: String,
        jobWithToken: VerifiedJobWithAccessToken,
        fileData: ByteArray,
    ) {
        val userClient = userClientFactory(jobWithToken.refreshToken)

        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(jobFolder, "JobParameters.json"),
                sensitivity = null,
                policy = WriteConflictPolicy.RENAME
            ),
            userClient.withHttpBody(
                ContentType.Application.Json,
                fileData.size.toLong(),
                fileData.inputStream().toByteReadChannel()
            )
        ).orThrow()
    }

    suspend fun acceptFile(
        jobWithToken: VerifiedJobWithAccessToken,

        filePath: String,
        length: Long,
        fileData: ByteReadChannel,
    ) {
        val userClient = run {
            val (_, refreshToken) = jobWithToken
            userClientFactory(refreshToken)
        }

        log.debug("Accepting file at $filePath for ${jobWithToken.job.id}")

        val parsedFilePath = File(filePath)
        val relativePath = if (parsedFilePath.isAbsolute) {
            ".$filePath" // TODO This might be a bad idea
        } else {
            filePath
        }

        log.debug("The relative path is $relativePath")

        val rootDestination = File(jobFolder(jobWithToken))
        val destPath = File(rootDestination, relativePath)

        log.debug("The destination path is ${destPath.path}")

        try {
            val uploadResp = MultiPartUploadDescriptions.simpleUpload.call(
                SimpleUploadRequest(
                    location = destPath.path,
                    sensitivity = null,
                ),
                userClient.withHttpBody(
                    ContentType.defaultForFilePath(filePath),
                    length,
                    fileData
                )
            )
            if (uploadResp is IngoingCallResponse.Error) {
                when (uploadResp.statusCode) {
                    HttpStatusCode.Forbidden -> {
                        // Permissions might have been lost for output directory. We silently ignore this.
                    }

                    else -> uploadResp.orThrow()
                }
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Forbidden || ex.httpStatusCode == HttpStatusCode.Unauthorized) {
                log.debug("Attempted to submit a file after token has been invalidated - Silently ignoring this error")
            } else {
                throw ex
            }
        }
    }

    fun jobsFolder(
        ownerUsername: String,
        project: String?,
    ): String {
        return if (project == null) {
            joinPath(homeDirectory(ownerUsername), "Jobs")
        } else {
            joinPath("/projects", project, PERSONAL_REPOSITORY, ownerUsername, "Jobs")
        }
    }

    private val resultFolderCacheIdToFolder = HashMap<String, String>()
    private val resultFolderMutex = Mutex()

    private suspend fun cacheJobFolder(id: String, path: String) {
        resultFolderMutex.withLock {
            resultFolderCacheIdToFolder[id] = path
        }
    }

    suspend fun jobFolder(jobWithToken: VerifiedJobWithAccessToken, new: Boolean = false): String {
        val cachedPath = resultFolderMutex.withLock {
            resultFolderCacheIdToFolder[jobWithToken.job.id]
        }

        if (cachedPath != null) return cachedPath
        val parameters = jobWithToken.job.specification ?: error("missing job parameters")
        val application = appStoreCache.apps.get(parameters.application)
            ?: throw RPCException("Unknown application", HttpStatusCode.InternalServerError)
        val (job, refreshToken) = jobWithToken
        val userClient = userClientFactory(refreshToken)

        val outputFolder = job.output?.outputFolder
        if (outputFolder != null) {
            return outputFolder
        } else {
            val jobsFolder = jobsFolder(job.owner.createdBy, job.owner.project)
            var folderName = job.id

            if (new) {
                // Find a name for a new folder using the job ID
                var shortJobId: String
                var folderNameLength = 8

                while (folderNameLength < job.id.length) {
                    shortJobId = job.id.take(folderNameLength)

                    folderName = if (job.specification?.name.isNullOrBlank()) {
                        shortJobId
                    } else {
                        job.specification?.name + "-" + shortJobId
                    }

                    val statStatusCode = FileDescriptions.stat.call(
                        StatRequest(
                            joinPath(
                                jobsFolder,
                                application.metadata.title,
                                folderName
                            )
                        ),
                        userClient
                    ).statusCode


                    if (statStatusCode == HttpStatusCode.NotFound) {
                        break
                    } else if (statStatusCode == HttpStatusCode.Forbidden) {
                        // This could be a project user who has not yet created a personal repository
                        // We will let this fail later if we really don't have permissions.
                        break
                    }

                    folderNameLength++
                }
            } else {
                // No file id was found, nor is this `new`. Assume old format (timestamp)
                val timestamp =
                    timestampFormatter.format(
                        LocalDateTime.ofInstant(Date(job.updates.first().timestamp).toInstant(), zoneId)
                    )

                folderName = if (parameters.name == null) {
                    timestamp
                } else {
                    parameters.name + "-" + timestamp
                }
            }

            return joinPath(
                jobsFolder,
                application.metadata.title,
                folderName
            ).normalize().also { cacheJobFolder(job.id, it) }
        }
    }

    suspend fun cleanupAfterMounts(jobWithToken: VerifiedJobWithAccessToken) {
        try {
            // Some minor cleanup #1358 (TODO This probably needs to be more centralized)
            val job = jobWithToken.job
            val outputFolder = job.output?.outputFolder ?: return

            val mounts = job.files.map { it.toMountName() }
            val userClient = userClientFactory(jobWithToken.refreshToken)

            mounts.forEach { mountName ->
                FileDescriptions.deleteFile.call(
                    DeleteFileRequest(joinPath(outputFolder, mountName)),
                    userClient
                )
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Unauthorized) {
                // Ignore
            } else {
                throw ex
            }
        }
    }

    private fun AppParameterValue.File.toMountName(): String = path.normalize().fileName()

    companion object : Loggable {
        override val log = logger()

        private val zoneId = ZoneId.of("Europe/Copenhagen")
        private val timestampFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm.ss.SSS")
    }
}
