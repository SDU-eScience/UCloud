package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.license.api.AppLicenseDescriptions
import dk.sdu.cloud.app.license.api.LicenseServerRequest
import dk.sdu.cloud.app.orchestrator.api.ApplicationPeer
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.orchestrator.api.ValidatedFileForUpload
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJobInput
import dk.sdu.cloud.app.orchestrator.util.orThrowOnError
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.FileTransferDescription
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.VerifyFileKnowledgeRequest
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.*

data class UnverifiedJob(
    val request: StartJobRequest,
    val decodedToken: SecurityPrincipalToken,
    val refreshToken: String
)

data class VerifiedJobWithAccessToken(
    val job: VerifiedJob,
    val accessToken: String?,
    val refreshToken: String?
)

private typealias ParameterWithTransfer = Pair<ApplicationParameter<FileTransferDescription>, FileTransferDescription>

class JobVerificationService<Session>(
    private val appStore: AppStoreService,
    private val toolStore: ToolStoreService,
    private val defaultBackend: String,
    private val db: DBSessionFactory<Session>,
    private val dao: JobDao<Session>,
    private val serviceClient: AuthenticatedClient,
    private val machines: List<MachineReservation> = listOf(MachineReservation.BURST)
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        userClient: AuthenticatedClient
    ): VerifiedJobWithAccessToken {
        val jobId = UUID.randomUUID().toString()
        val name = unverifiedJob.request.name
        val application = findApplication(unverifiedJob)
        val tool = application.invocation.tool.tool!!
        val reservation =
            if (unverifiedJob.request.reservation == null) MachineReservation.BURST
            else (machines.find { it.name == unverifiedJob.request.reservation }
                ?: throw JobException.VerificationError("Bad machine reservation"))
        val verifiedParameters = verifyParameters(application, unverifiedJob)
        val username = unverifiedJob.decodedToken.principal.username

        val files = findAndCollectFromInput(username, application, verifiedParameters, userClient)
        val mounts = findAndCollectFromMounts(username, unverifiedJob, userClient)

        val numberOfJobs = unverifiedJob.request.numberOfNodes ?: tool.description.defaultNumberOfNodes
        val tasksPerNode = unverifiedJob.request.tasksPerNode ?: tool.description.defaultTasksPerNode
        val allocatedTime = unverifiedJob.request.maxTime ?: tool.description.defaultTimeAllocation

        val archiveInCollection = unverifiedJob.request.archiveInCollection ?: application.metadata.title
        val url = unverifiedJob.request.url

        val (allPeers, _) = run {
            // TODO we should enforce in the app store that the parameter is a valid hostname
            val parameterPeers = application.invocation.parameters
                .filterIsInstance<ApplicationParameter.Peer>()
                .map { parameter ->
                    val peerApplicationParameter = verifiedParameters[parameter]!!
                    ApplicationPeer(parameter.name, peerApplicationParameter.peerJobId)
                }

            val allPeers = unverifiedJob.request.peers + parameterPeers
            val duplicatePeers = allPeers
                .map { it.name }
                .groupBy { it }
                .filter { it.value.size > 1 }

            if (duplicatePeers.isNotEmpty()) {
                throw JobException.VerificationError(
                    "Duplicate hostname detected: " + duplicatePeers.keys.joinToString()
                )
            }

            val resolvedPeers = db.withTransaction { session ->
                dao.find(session, allPeers.map { it.jobId }, unverifiedJob.decodedToken)
            }

            val resolvedPeerIds = resolvedPeers.map { it.job.id }

            val missingPeer = allPeers.find { it.jobId !in resolvedPeerIds }
            if (missingPeer != null) {
                throw JobException.VerificationError("Could not find peer with id '${missingPeer.jobId}'")
            }

            Pair(allPeers, resolvedPeers)
        }

        return VerifiedJobWithAccessToken(
            VerifiedJob(
                id = jobId,
                name = name,
                owner = unverifiedJob.decodedToken.principal.username,
                application = application,
                backend = resolveBackend(unverifiedJob.request.backend, defaultBackend),
                nodes = numberOfJobs,
                maxTime = allocatedTime,
                tasksPerNode = tasksPerNode,
                reservation = reservation,
                jobInput = verifiedParameters,
                files = files,
                _mounts = mounts,
                _peers = allPeers.toSet(),
                currentState = JobState.VALIDATED,
                failedState = null,
                status = "Validated",
                archiveInCollection = archiveInCollection,
                startedAt = null,
                url = url
            ),
            null,
            unverifiedJob.refreshToken
        )
    }

    private suspend fun findApplication(job: UnverifiedJob): Application {
        val result = with(job.request.application) {
            appStore.findByNameAndVersion(name, version)
        } ?: throw JobException.VerificationError("Application '${job.request.application}' does not exist")

        val toolName = result.invocation.tool.name
        val toolVersion = result.invocation.tool.version
        val loadedTool = toolStore.findByNameAndVersion(toolName, toolVersion)

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
                            invocationParameter = "/work/${value.source.parent().removeSuffix("/").fileName()}/${value.source.fileName()}"
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
