package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.ComputationDescriptions
import dk.sdu.cloud.app.orchestrator.api.MountMode
import dk.sdu.cloud.app.orchestrator.api.SubmitFileToComputation
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

data class JobDirectory(val path: String, val id: String)

class JobFileService(
    private val serviceClient: AuthenticatedClient,
    private val userClientFactory: (accessToken: String?, refreshToken: String?) -> AuthenticatedClient,
    private val parameterExportService: ParameterExportService
) {
    suspend fun initializeResultFolder(jobWithToken: VerifiedJobWithAccessToken): JobDirectory {
        val (job, accessToken, refreshToken) = jobWithToken

        val userCloud = userClientFactory(accessToken, refreshToken)

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        val jobsFolder = jobsFolder(job.owner)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(jobsFolder, null),
            userCloud
        )

        val path = jobFolder(jobWithToken, true)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path.parent(), null, sensitivity = sensitivityLevel),
            userCloud
        )

        val dirResp = FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path, null),
            userCloud
        ).throwIfInternal()

        val folderId = FileDescriptions.stat.call(
            StatRequest(path, StorageFileAttribute.fileId.name),
            userCloud
        ).orThrow().fileId

        if (!dirResp.statusCode.isSuccess()) {
            // Throw if we didn't allow this case
            throw RPCException.fromStatusCode(dirResp.statusCode)
        }

        return JobDirectory(path, folderId)
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
        fileData: ByteReadChannel,
        needsExtraction: Boolean
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

        if (needsExtraction) {
            FileDescriptions.extract.call(
                ExtractRequest(
                    destPath.path,
                    removeOriginalArchive = true
                ),
                userClient
            ).orThrow()
        }
    }

    private val jobFolderCache = HashMap<String, String>()
    private val jobFolderLock = Mutex()

    suspend fun jobsFolder(ownerUsername: String): String {
        jobFolderLock.withLock {
            val homeFolder = jobFolderCache[ownerUsername] ?: FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(ownerUsername),
                serviceClient
            ).orThrow().path

            jobFolderCache[ownerUsername] = homeFolder

            return joinPath(
                homeFolder,
                "Jobs"
            )
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
        val jobsFolder = jobsFolder(job.owner)
        var folderName = job.id

        if (job.folderId == null) {
            if (new) {
                // Find a name for a new folder using the job ID
                var shortJobId: String
                var folderNameLength = 8

                while (folderNameLength < job.id.length) {
                    shortJobId = job.id.take(folderNameLength)

                    folderName = if (job.name == null) {
                        shortJobId
                    } else {
                        job.name + "-" + shortJobId
                    }

                    if (FileDescriptions.stat.call(
                            StatRequest(
                                joinPath(
                                    jobsFolder,
                                    job.archiveInCollection,
                                    folderName
                                ), StorageFileAttribute.fileId.name
                            ),
                            userClient
                        ).statusCode == HttpStatusCode.NotFound
                    ) {
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
        } else {
            // Reverse lookup of path from file id
            val lookupResult = LookupDescriptions.reverseLookup.call(
                ReverseLookupRequest(job.folderId.toString()),
                serviceClient
            ).orThrow().canonicalPath.first() ?: ""

            cacheJobFolder(job.id, lookupResult)
            return lookupResult
        }

        return joinPath(
            jobsFolder,
            job.archiveInCollection,
            folderName
        ).also { cacheJobFolder(job.id, it) }
    }

    suspend fun transferFilesToBackend(jobWithToken: VerifiedJobWithAccessToken, backend: ComputationDescriptions) {
        val (job) = jobWithToken
        coroutineScope {
            (job.files + job.mounts).map { file ->
                async {
                    runCatching {
                        val userCloud = userClientFactory(jobWithToken.accessToken, jobWithToken.refreshToken)
                        val fileStream = FileDescriptions.download.call(
                            DownloadByURI(file.sourcePath, null),
                            userCloud
                        ).orRethrowAs { throw JobException.TransferError() }.asIngoing()

                        backend.submitFile.call(
                            SubmitFileToComputation(
                                job.id,
                                file.id,
                                BinaryStream.outgoingFromChannel(
                                    fileStream.channel,
                                    contentType = fileStream.contentType
                                        ?: ContentType.Application.OctetStream,
                                    contentLength = fileStream.length
                                )
                            ),
                            serviceClient
                        ).orThrow()
                    }
                }
            }.awaitAll().firstOrNull { it.isFailure }.let {
                if (it != null) {
                    throw it.exceptionOrNull()!!
                }
            }
        }
    }

    suspend fun createWorkspace(jobWithToken: VerifiedJobWithAccessToken): String {
        val (job) = jobWithToken
        val mounts = (job.files + job.mounts).map { file ->
            WorkspaceMount(file.sourcePath, file.destinationPath, readOnly = file.readOnly)
        }

        return WORKSPACE_PATH + WorkspaceDescriptions.create.call(
            Workspaces.Create.Request(
                job.owner,
                mounts,
                allowFailures = false,
                createSymbolicLinkAt = "/input",
                mode =
                    if (job.mountMode == MountMode.COPY_ON_WRITE) WorkspaceMode.COPY_ON_WRITE
                    else WorkspaceMode.COPY_FILES
            ),
            serviceClient
        ).orThrow().workspaceId
    }

    suspend fun transferWorkspace(
        jobWithToken: VerifiedJobWithAccessToken,
        replay: Boolean
    ) {
        val (job) = jobWithToken
        @Suppress("TooGenericExceptionCaught")
        try {
            WorkspaceDescriptions.transfer.call(
                Workspaces.Transfer.Request(
                    workspaceId = job.workspace?.removePrefix(WORKSPACE_PATH) ?: throw RPCException(
                        "No workspace found",
                        HttpStatusCode.InternalServerError
                    ),
                    transferGlobs = job.application.invocation.outputFileGlobs,
                    destination = jobFolder(jobWithToken),
                    deleteWorkspace = true
                ),
                serviceClient
            ).orThrow()
        } catch (ex: Throwable) {
            if (replay) {
                log.info("caught exception while replaying transfer of workspace. ${ex.message}")
                log.debug(ex.stackTraceToString())
            } else {
                throw ex
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val zoneId = ZoneId.of("Europe/Copenhagen")
        private val timestampFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm.ss.SSS")
        private const val WORKSPACE_PATH = "/workspace/"
    }
}
