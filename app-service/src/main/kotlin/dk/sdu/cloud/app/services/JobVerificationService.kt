package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.FileTransferDescription
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.StartJobRequest
import dk.sdu.cloud.app.api.ToolReference
import dk.sdu.cloud.app.api.ValidatedFileForUpload
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.util.orThrowOnError
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.net.URI
import java.util.*

data class UnverifiedJob(
    val request: StartJobRequest,
    val principal: DecodedJWT
)

data class VerifiedJobWithAccessToken(
    val job: VerifiedJob,
    val accessToken: String
)

private typealias ParameterWithTransfer = Pair<ApplicationParameter<FileTransferDescription>, FileTransferDescription>

class JobVerificationService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val applicationDAO: ApplicationDAO<DBSession>,
    private val toolDAO: ToolDAO<DBSession>,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val defaultBackend: String
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        cloud: AuthenticatedClient
    ): VerifiedJobWithAccessToken {
        val jobId = UUID.randomUUID().toString()
        val application = findApplication(unverifiedJob)
        val tool = application.invocation.tool.tool!!
        val verifiedParameters = verifyParameters(application, unverifiedJob)
        val workDir = URI("/$jobId")
        val files = collectFiles(application, verifiedParameters, workDir, cloud).map {
            it.copy(
                destinationPath = "./" + it.destinationPath.removePrefix(workDir.path)
            )
        }

        val mounts = collectCloudFiles(verifyMounts(unverifiedJob), workDir, cloud).map {
            it.copy(
                destinationPath = "./" + it.destinationPath.removePrefix(workDir.path)
            )
        }

        val numberOfJobs = unverifiedJob.request.numberOfNodes ?: tool.description.defaultNumberOfNodes
        val tasksPerNode = unverifiedJob.request.tasksPerNode ?: tool.description.defaultTasksPerNode
        val maxTime = unverifiedJob.request.maxTime ?: tool.description.defaultMaxTime

        val archiveInCollection = unverifiedJob.request.archiveInCollection ?: application.metadata.title

        val token = tokenValidation.decodeToken(unverifiedJob.principal)

        return VerifiedJobWithAccessToken(
            VerifiedJob(
                application = application,
                files = files,
                id = jobId,
                owner = token.principal.username,
                nodes = numberOfJobs,
                tasksPerNode = tasksPerNode,
                maxTime = maxTime,
                jobInput = verifiedParameters,
                backend = resolveBackend(unverifiedJob.request.backend, defaultBackend),
                currentState = JobState.VALIDATED,
                status = "Validated",
                ownerUid = token.principal.uid,
                archiveInCollection = archiveInCollection,
                _mounts = mounts,
                startedAt = null
            ),
            unverifiedJob.principal.token
        )
    }

    private fun findApplication(job: UnverifiedJob): Application = db.withTransaction { session ->
        try {
            val result = with(job.request.application) {
                applicationDAO.findByNameAndVersion(session, job.principal.subject, name, version)
            }

            val toolName = result.invocation.tool.name
            val toolVersion = result.invocation.tool.version
            val loadedTool =
                toolDAO.findByNameAndVersion(session, null, toolName, toolVersion)

            result.copy(invocation = result.invocation.copy(tool = ToolReference(toolName, toolVersion, loadedTool)))
        } catch (ex: ApplicationException.NotFound) {
            throw JobException.VerificationError("Application '${job.request.application}' does not exist")
        }
    }

    private fun verifyParameters(app: Application, job: UnverifiedJob): VerifiedJobInput {
        val userParameters = job.request.parameters
        return VerifiedJobInput(
            app.invocation.parameters.map { appParameter ->
                try {
                    appParameter.name to appParameter.map(userParameters[appParameter.name])
                } catch (ex: IllegalArgumentException) {
                    log.debug(ex.stackTraceToString())
                    log.debug("Invocation: ${app.invocation}")
                    throw JobException.VerificationError("Bad parameter: ${appParameter.name}. ${ex.message}")
                }
            }.toMap()
        )
    }

    private fun verifyMounts(
        job: UnverifiedJob
    ): List<ParameterWithTransfer> {
        val fakeParameter = ApplicationParameter.InputDirectory(name = "mount", optional = false)
        return job.request.mounts
            .mapNotNull { fakeParameter.map(it) }
            .mapIndexed { i, transfer -> Pair(fakeParameter.copy(name = "mount-$i"), transfer) }
    }

    private suspend fun collectFiles(
        application: Application,
        verifiedParameters: VerifiedJobInput,
        workDir: URI,
        cloud: AuthenticatedClient
    ): List<ValidatedFileForUpload> {
        return coroutineScope {
            val transfersFromParameters = application.invocation.parameters
                .asSequence()
                .filter { it is ApplicationParameter.InputFile || it is ApplicationParameter.InputDirectory }
                .mapNotNull {
                    @Suppress("UNCHECKED_CAST")
                    val fileAppParameter = it as ApplicationParameter<FileTransferDescription>
                    val transfer = verifiedParameters[fileAppParameter] ?: return@mapNotNull null
                    ParameterWithTransfer(fileAppParameter, transfer)
                }
                .toList()

            collectCloudFiles(transfersFromParameters, workDir, cloud)
        }
    }

    private suspend fun collectCloudFiles(
        transfers: List<ParameterWithTransfer>,
        workDir: URI,
        cloud: AuthenticatedClient
    ): List<ValidatedFileForUpload> {
        return coroutineScope {
            transfers
                .map { (parameter, transfer) ->
                    async { collectSingleFile(transfer, workDir, cloud, parameter) }
                }
                .toList()
                .mapNotNull { it.await() }
        }
    }

    private suspend fun collectSingleFile(
        transferDescription: FileTransferDescription,
        workDir: URI,
        cloud: AuthenticatedClient,
        fileAppParameter: ApplicationParameter<FileTransferDescription>
    ): ValidatedFileForUpload? {
        val desiredFileType = when (fileAppParameter) {
            is ApplicationParameter.InputDirectory -> FileType.DIRECTORY
            else -> FileType.FILE
        }

        val sourcePath = transferDescription.source
        val stat = FileDescriptions.stat.call(StatRequest(sourcePath), cloud)
            .orThrowOnError {
                throw JobException.VerificationError("Missing file in storage: $sourcePath. Are you sure it exists?")
            }
            .result

        if (stat.fileType != desiredFileType) {
            throw JobException.VerificationError(
                "Expected type of ${fileAppParameter.name} to be " +
                        "$desiredFileType, but instead got a ${stat.fileType}"
            )
        }

        // Resolve relative path against working directory. Ensure that file is still inside of
        // the working directory.
        val destinationPath = File(workDir.path, transferDescription.destination).normalize().path
        if (!destinationPath.startsWith(workDir.path)) {
            throw JobException.VerificationError(
                "Not allowed to leave working directory via relative paths. Please avoid using '..' in paths."
            )
        }

        val name = destinationPath.split("/").last()

        return ValidatedFileForUpload(
            fileAppParameter.name,
            stat,
            name,
            destinationPath,
            sourcePath,
            if (desiredFileType == FileType.DIRECTORY) FileForUploadArchiveType.ZIP else null,
            readOnly = transferDescription.readOnly
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
