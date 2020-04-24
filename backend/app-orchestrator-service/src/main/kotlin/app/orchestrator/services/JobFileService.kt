package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.Loggable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.isSuccess
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.core.ExperimentalIoApi
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class JobDirectory(val path: String)

class JobFileService(
    private val userClientFactory: (accessToken: String?, refreshToken: String?) -> AuthenticatedClient,
    private val parameterExportService: ParameterExportService,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun initializeResultFolder(jobWithToken: VerifiedJobWithAccessToken): JobDirectory {
        val (job, accessToken, refreshToken) = jobWithToken
        val userClient = userClientFactory(accessToken, refreshToken)

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        val project = job.project
        val jobsFolder = jobsFolder(job.owner, project)
        if (project != null) {
            // Create the personal repository lazily
            FileDescriptions.createPersonalRepository.call(
                CreatePersonalRepositoryRequest(project, job.owner),
                serviceClient
            )
        }

        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(jobsFolder, null),
            userClient
        )

        val path = jobFolder(jobWithToken, true)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path.parent(), null, sensitivity = sensitivityLevel),
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
    @UseExperimental(ExperimentalIoApi::class)
    suspend fun exportParameterFile(
        jobFolder: String,
        jobWithToken: VerifiedJobWithAccessToken,
        rawParameters: Map<String, Any?>
    ) {
        val userClient = userClientFactory(jobWithToken.accessToken, jobWithToken.refreshToken)

        val fileData =
            defaultMapper.writeValueAsBytes(parameterExportService.exportParameters(jobWithToken.job, rawParameters))

        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                joinPath(jobFolder, "JobParameters.json"),
                sensitivity = null,
                file = BinaryStream.outgoingFromChannel(
                    fileData.inputStream().toByteReadChannel(),
                    fileData.size.toLong(),
                    ContentType.Application.Json
                ),
                policy = WriteConflictPolicy.RENAME
            ),
            userClient
        )
    }

    suspend fun acceptFile(
        jobWithToken: VerifiedJobWithAccessToken,

        filePath: String,
        length: Long,
        fileData: ByteReadChannel
    ) {
        val userClient = run {
            val (_, accessToken, refreshToken) = jobWithToken
            userClientFactory(accessToken, refreshToken)
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

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                location = destPath.path,
                sensitivity = sensitivityLevel,
                file = BinaryStream.outgoingFromChannel(
                    fileData,
                    contentLength = length,
                    contentType = ContentType.defaultForFilePath(filePath)
                )
            ),
            userClient
        ).orThrow()
    }

    fun jobsFolder(
        ownerUsername: String,
        project: String?
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

        val (job, accessToken, refreshToken) = jobWithToken
        val userClient = userClientFactory(accessToken, refreshToken)

        val outputFolder = job.outputFolder
        if (outputFolder != null) {
            return outputFolder
        } else {
            val jobsFolder = jobsFolder(job.owner, job.project)
            var folderName = job.id

            if (new) {
                // Find a name for a new folder using the job ID
                var shortJobId: String
                var folderNameLength = 8

                while (folderNameLength < job.id.length) {
                    shortJobId = job.id.take(folderNameLength)

                    folderName = if (job.name.isNullOrBlank()) {
                        shortJobId
                    } else {
                        job.name + "-" + shortJobId
                    }

                    val statStatusCode = FileDescriptions.stat.call(
                        StatRequest(
                            joinPath(
                                jobsFolder,
                                job.archiveInCollection,
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
                    timestampFormatter.format(LocalDateTime.ofInstant(Date(job.createdAt).toInstant(), zoneId))

                folderName = if (job.name == null) {
                    timestamp
                } else {
                    job.name + "-" + timestamp
                }
            }

            return joinPath(
                jobsFolder,
                job.archiveInCollection,
                folderName
            ).also { cacheJobFolder(job.id, it) }
        }
    }

    suspend fun cleanupAfterMounts(jobWithToken: VerifiedJobWithAccessToken) {
        // Some minor cleanup #1358 (TODO This probably needs to be more centralized)
        val job = jobWithToken.job
        val outputFolder = job.outputFolder ?: return

        val mounts = job.mounts.map { it.toMountName() } + job.files.map { it.toMountName() }
        val userClient = userClientFactory(jobWithToken.accessToken, jobWithToken.refreshToken)
        log.debug("access" + jobWithToken.accessToken)
        log.debug("refresh" + jobWithToken.refreshToken)

        mounts.forEach { mountName ->
            FileDescriptions.deleteFile.call(
                DeleteFileRequest(joinPath(outputFolder, mountName)),
                userClient
            )
        }
    }

    private fun ValidatedFileForUpload.toMountName(): String = sourcePath.normalize().fileName()

    companion object : Loggable {
        override val log = logger()

        private val zoneId = ZoneId.of("Europe/Copenhagen")
        private val timestampFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm.ss.SSS")
    }
}
