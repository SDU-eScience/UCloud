package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.license.api.AppLicenseDescriptions
import dk.sdu.cloud.app.license.api.LicenseServerRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.util.orThrowOnError
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.*

data class UnverifiedJob(
    val request: StartJobRequest,
    val decodedToken: SecurityPrincipalToken,
    val refreshToken: String,
    val project: String?
)

data class VerifiedJobWithAccessToken(
    val job: VerifiedJob,
    val accessToken: String?,
    val refreshToken: String?
)

private typealias ParameterWithTransfer = Pair<ApplicationParameter<FileTransferDescription>, FileTransferDescription>

class JobVerificationService(
    private val appService: ApplicationService,
    private val defaultBackend: String,
    private val db: DBContext,
    private val jobs: JobQueryService,
    private val serviceClient: AuthenticatedClient,
    private val machineCache: MachineTypeCache
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        userClient: AuthenticatedClient
    ): VerifiedJobWithAccessToken {
        val application = findApplication(unverifiedJob)
        val tool = application.invocation.tool.tool!!

        // Check input parameters
        val verifiedParameters = verifyParameters(
            application,
            unverifiedJob
        )

        // Check files
        val files = findAndCollectFromInput(
            unverifiedJob.decodedToken.principal.username,
            application,
            verifiedParameters,
            userClient
        )

        // Check mounts
        val mounts = findAndCollectFromMounts(
            unverifiedJob.decodedToken.principal.username,
            unverifiedJob,
            userClient
        )

        // Check name
        val name = unverifiedJob.request.name
        if (name != null) {
            val invalidChars = Regex("""([./\\\n])""")
            if (invalidChars.containsMatchIn(name)) {
                throw RPCException("Provided name not allowed", HttpStatusCode.BadRequest)
            }
        }

        // Check URL
        val url = unverifiedJob.request.url
        if (url != null) {
            if (jobs.isUrlOccupied(db, url)) {
                throw RPCException("Provided url not available", HttpStatusCode.BadRequest)
            }

            if (!jobs.canUseUrl(db, unverifiedJob.decodedToken.principal.username, url)) {
                throw RPCException("Not allowed to use selected URL", HttpStatusCode.BadRequest)
            }
        }

        // Check peers
        val (allPeers, _) = run {
            // TODO we should enforce in the app store that the parameter is a valid hostname
            val parameterPeers = application.invocation.parameters
                .filterIsInstance<ApplicationParameter.Peer>()
                .map { parameter ->
                    val peerApplicationParameter = verifiedParameters[parameter]!!
                    ApplicationPeer(parameter.name, peerApplicationParameter.peerJobId)
                }

            val allPeers = (unverifiedJob.request.peers + parameterPeers)
                .map { ApplicationPeer(it.name.toLowerCase(), it.jobId) }

            val duplicatePeers = allPeers
                .map { it.name }
                .groupBy { it }
                .filter { it.value.size > 1 }

            if (duplicatePeers.isNotEmpty()) {
                throw JobException.VerificationError(
                    "Duplicate hostname detected: " + duplicatePeers.keys.joinToString()
                )
            }

            val resolvedPeers = jobs.find(
                db,
                allPeers.map { it.jobId },
                unverifiedJob.decodedToken.principal.username
            )

            val resolvedPeerIds = resolvedPeers.map { it.job.id }

            val missingPeer = allPeers.find { it.jobId !in resolvedPeerIds }
            if (missingPeer != null) {
                throw JobException.VerificationError("Could not find peer with id '${missingPeer.jobId}'")
            }

            Pair(allPeers, resolvedPeers)
        }

        // Check machine reservation
        val reservation = run {
            val machineName = unverifiedJob.request.reservation
            val reservation =
                if (machineName != null) machineCache.find(machineName)
                else machineCache.findDefault()

            reservation ?: throw JobException.VerificationError("Invalid machine type")
        }

        // Verify membership of project
        if (unverifiedJob.project != null) {
            Projects.viewMemberInProject.call(
                ViewMemberInProjectRequest(
                    unverifiedJob.project,
                    unverifiedJob.decodedToken.principal.username
                ),
                serviceClient
            ).orNull() ?: throw RPCException("Unable to verify membership of project", HttpStatusCode.Forbidden)
        }

        return VerifiedJobWithAccessToken(
            VerifiedJob(
                id = UUID.randomUUID().toString(),
                name = name,
                owner = unverifiedJob.decodedToken.principal.username,
                application = application,
                backend = resolveBackend(unverifiedJob.request.backend, defaultBackend),
                nodes = unverifiedJob.request.numberOfNodes ?: tool.description.defaultNumberOfNodes,
                maxTime = unverifiedJob.request.maxTime ?: tool.description.defaultTimeAllocation,
                tasksPerNode = unverifiedJob.request.tasksPerNode ?: tool.description.defaultTasksPerNode,
                reservation = reservation,
                jobInput = verifiedParameters,
                files = files,
                _mounts = mounts,
                _peers = allPeers.toSet(),
                currentState = JobState.VALIDATED,
                failedState = null,
                status = "Validated",
                archiveInCollection = unverifiedJob.request.archiveInCollection ?: application.metadata.title,
                startedAt = null,
                url = url,
                project = unverifiedJob.project
            ),
            null,
            unverifiedJob.refreshToken
        )
    }

    private suspend fun findApplication(job: UnverifiedJob): Application {
        val result = with(job.request.application) {
            appService.apps.get(NameAndVersion(name, version))
        } ?: throw JobException.VerificationError("Application '${job.request.application}' does not exist")

        val toolName = result.invocation.tool.name
        val toolVersion = result.invocation.tool.version
        val loadedTool = appService.tools.get(NameAndVersion(toolName, toolVersion))

        return result.copy(invocation = result.invocation.copy(tool = ToolReference(toolName, toolVersion, loadedTool)))
    }

    private suspend fun verifyParameters(
        app: Application,
        job: UnverifiedJob
    ): VerifiedJobInput {
        val userParameters = job.request.parameters.toMutableMap()
        return VerifiedJobInput(
            app.invocation.parameters
                .asSequence()
                .map { appParameter ->
                    if (appParameter is ApplicationParameter.LicenseServer) {
                        val licenseServerId = userParameters[appParameter.name]
                        if (licenseServerId != null) {
                            // Transform license server
                            val lookupLicenseServer = runBlocking {
                                AppLicenseDescriptions.get.call(
                                    LicenseServerRequest(licenseServerId.toString()),
                                    serviceClient
                                )
                            }.orThrow()

                            userParameters[appParameter.name] = mapOf(
                                "id" to licenseServerId,
                                "address" to lookupLicenseServer.address,
                                "port" to lookupLicenseServer.port,
                                "license" to lookupLicenseServer.license
                            )
                        }
                    }
                    appParameter
                }
                .map { appParameter ->
                    try {
                        appParameter to appParameter.map(userParameters[appParameter.name])
                    } catch (ex: IllegalArgumentException) {
                        log.debug(ex.stackTraceToString())
                        log.debug("Invocation: ${app.invocation}")
                        throw JobException.VerificationError("Bad parameter: ${appParameter.name}. ${ex.message}")
                    }
                }
                .map { paramWithValue ->
                    // Fix path for InputFiles
                    val (param, value) = paramWithValue
                    if (param is ApplicationParameter.InputFile && value != null) {
                        value as FileTransferDescription
                        param to value.copy(
                            invocationParameter = "/work/${value.source.parent().removeSuffix("/")
                                .fileName()}/${value.source.fileName()}"
                        )
                    } else {
                        paramWithValue
                    }
                }
                .map { paramWithValue ->
                    // Fix path for InputDirectory
                    val (param, value) = paramWithValue
                    if (param is ApplicationParameter.InputDirectory && value != null) {
                        value as FileTransferDescription
                        param to value.copy(
                            invocationParameter = "/work/${value.source.normalize().fileName()}/"
                        )
                    } else {
                        paramWithValue
                    }
                }
                .map { (param, value) ->
                    param.name to value
                }
                .toMap()
        )
    }

    private suspend fun findAndCollectFromMounts(
        username: String,
        job: UnverifiedJob,
        userClient: AuthenticatedClient
    ): Set<ValidatedFileForUpload> {
        val fakeParameter = ApplicationParameter.InputDirectory(name = "mount", optional = false)
        val transfers = job.request.mounts
            .mapNotNull { fakeParameter.map(it) }
            .mapIndexed { i, transfer -> Pair(fakeParameter.copy(name = "mount-$i"), transfer) }
        return collectAllFromTransfers(username, transfers, userClient)
    }

    private suspend fun findAndCollectFromInput(
        username: String,
        application: Application,
        verifiedParameters: VerifiedJobInput,
        userClient: AuthenticatedClient
    ): Set<ValidatedFileForUpload> {
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

            collectAllFromTransfers(username, transfersFromParameters, userClient)
        }
    }

    private suspend fun collectAllFromTransfers(
        username: String,
        transfers: List<ParameterWithTransfer>,
        userClient: AuthenticatedClient
    ): Set<ValidatedFileForUpload> {
        return coroutineScope {
            transfers
                .map { (parameter, transfer) ->
                    async { collectSingleFile(username, transfer, userClient, parameter) }
                }
                .mapNotNull { it.await() }
                .toSet()
        }
    }

    private suspend fun collectSingleFile(
        username: String,
        transferDescription: FileTransferDescription,
        userClient: AuthenticatedClient,
        fileAppParameter: ApplicationParameter<FileTransferDescription>
    ): ValidatedFileForUpload? {
        val desiredFileType = when (fileAppParameter) {
            is ApplicationParameter.InputDirectory -> FileType.DIRECTORY
            else -> FileType.FILE
        }

        val sourcePath = transferDescription.source
        val stat = FileDescriptions.stat.call(StatRequest(sourcePath), userClient)
            .orThrowOnError {
                throw JobException.VerificationError("File not found or permission denied: $sourcePath")
            }
            .result

        if (stat.fileType != desiredFileType) {
            throw JobException.VerificationError(
                "Expected type of ${fileAppParameter.name} to be " +
                    "$desiredFileType, but instead got a ${stat.fileType}"
            )
        }

        val fileToMount = when (desiredFileType) {
            FileType.DIRECTORY -> stat
            FileType.FILE -> {
                val parent = sourcePath.parent()
                FileDescriptions.stat
                    .call(StatRequest(parent), userClient)
                    .orThrowOnError { throw JobException.VerificationError("Permission denied: $parent") }
                    .result
            }
            else -> throw IllegalStateException()
        }

        val res = FileDescriptions.verifyFileKnowledge.call(
            VerifyFileKnowledgeRequest(username, listOf(fileToMount.path), KnowledgeMode.Permission(true)),
            serviceClient
        ).orThrow()

        val hasWritePermission = res.responses.single()

        return ValidatedFileForUpload(
            fileAppParameter.name,
            fileToMount,
            fileToMount.path,
            readOnly = !hasWritePermission
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
