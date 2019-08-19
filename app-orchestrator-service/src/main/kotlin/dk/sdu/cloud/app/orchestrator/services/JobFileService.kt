package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.ComputationDescriptions
import dk.sdu.cloud.app.orchestrator.api.SubmitFileToComputation
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.ExtractRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.WorkspaceDescriptions
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.api.Workspaces
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.sensitivityLevel
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class JobFileService(
    private val serviceClient: AuthenticatedClient,
    private val userClientFactory: (accessToken: String?, refreshToken: String?) -> AuthenticatedClient
) {
    suspend fun initializeResultFolder(
        jobWithToken: VerifiedJobWithAccessToken,
        isReplay: Boolean = false
    ) {
        val (job, accessToken, refreshToken) = jobWithToken

        val userCloud = userClientFactory(accessToken, refreshToken)

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        val path = jobFolder(job)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path.parent(), null, sensitivity = sensitivityLevel),
            userCloud
        )

        val dirResp = FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path, null),
            userCloud
        ).throwIfInternal()

        if (!dirResp.statusCode.isSuccess()) {
            // We allow conflicts during replay
            if (isReplay && dirResp.statusCode == HttpStatusCode.Conflict) return

            // Throw if we didn't allow this case
            throw RPCException.fromStatusCode(dirResp.statusCode)
        }
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

        val rootDestination = File(jobFolder(jobWithToken.job))
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

    suspend fun jobFolder(job: VerifiedJob): String {
        jobFolderLock.withLock {
            val cachedHomeFolder = jobFolderCache[job.owner]
            val homeFolder = if (cachedHomeFolder != null) {
                cachedHomeFolder
            } else {
                FileDescriptions.findHomeFolder.call(
                    FindHomeFolderRequest(job.owner),
                    serviceClient
                ).orThrow().path
            }

            jobFolderCache[job.owner] = homeFolder

            val timestamp = timestampFormatter.format(LocalDateTime.ofInstant(Date(job.createdAt).toInstant(), zoneId))

            val folderName: String = if (job.name == null) {
                timestamp
            } else {
                job.name + "-" + timestamp
            }

            return joinPath(
                homeFolder,
                "Jobs",
                job.archiveInCollection,
                folderName
            )
        }
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
                createSymbolicLinkAt = "/input"
            ),
            serviceClient
        ).orThrow().workspaceId
    }

    suspend fun transferWorkspace(
        jobWithToken: VerifiedJobWithAccessToken,
        replay: Boolean
    ) {
        val (job) = jobWithToken
        try {
            WorkspaceDescriptions.transfer.call(
                Workspaces.Transfer.Request(
                    workspaceId = job.workspace?.removePrefix(WORKSPACE_PATH) ?: throw RPCException(
                        "No workspace found",
                        HttpStatusCode.InternalServerError
                    ),
                    transferGlobs = job.application.invocation.outputFileGlobs,
                    destination = jobFolder(job),
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
        private val timestampFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS")
        private const val WORKSPACE_PATH = "/workspace/"
    }
}
