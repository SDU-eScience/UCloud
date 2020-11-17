package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.license.api.AppLicenseDescriptions
import dk.sdu.cloud.app.license.api.LicenseServerRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.util.orThrowOnError
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

data class UnverifiedJob(
    val request: JobParameters,
    val username: String,
    val project: String?,
)

data class VerifiedJobWithAccessToken(
    val job: Job,
    val refreshToken: String,
)

class JobVerificationService(
    private val appService: AppStoreCache,
    private val db: DBContext,
    private val jobs: JobQueryService,
    private val serviceClient: AuthenticatedClient,
    private val providers: Providers,
    private val machineTypeCache: MachineTypeCache,
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        userClient: AuthenticatedClient,
    ): VerifiedJobWithAccessToken {
        val application = findApplication(unverifiedJob)
        val tool = application.invocation.tool.tool!!

        // Check provider support
        val (provider, manifest) = providers.fetchManifest(unverifiedJob.request.product.provider)
        run {
            when (application.invocation.applicationType) {
                ApplicationType.BATCH -> {
                    if (!manifest.features.batch) {
                        throw RPCException(
                            "Batch applications are not supported at ${provider.id}",
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                ApplicationType.VNC -> {
                    if (!manifest.features.vnc) {
                        throw RPCException(
                            "Interactive applications are not supported at ${provider.id}",
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                ApplicationType.WEB -> {
                    if (!manifest.features.web) {
                        throw RPCException(
                            "Web applications are not supported at ${provider.id}",
                            HttpStatusCode.BadRequest
                        )
                    }
                }
            }

            when (tool.description.backend) {
                ToolBackend.SINGULARITY -> {
                    throw RPCException("Unsupported UCloud application. Please contact support.",
                        HttpStatusCode.BadRequest)
                }

                ToolBackend.DOCKER -> {
                    if (!manifest.features.docker) {
                        throw RPCException(
                            "Docker applications are not supported at ${provider.id}",
                            HttpStatusCode.BadRequest
                        )
                    }
                }
            }
        }

        // Check product
        val machine = machineTypeCache.find(
            unverifiedJob.request.product.provider,
            unverifiedJob.request.product.id,
            unverifiedJob.request.product.category
        ) ?: throw RPCException("Invalid machine type selected", HttpStatusCode.BadRequest)

        // Check input parameters
        val verifiedParameters = verifyParameters(
            application,
            unverifiedJob
        )

        // Check files
        val files = collectFilesFromParameters(
            unverifiedJob.username,
            application,
            verifiedParameters,
            userClient
        )

        // Check mounts
        collectFilesFromMounts(
            unverifiedJob.username,
            unverifiedJob,
            userClient
        )

        // Check URL
        unverifiedJob.request.resources.filterIsInstance<AppParameterValue.Ingress>().forEach { ingress ->
            val url = ingress.domain
            if (jobs.isUrlOccupied(db, url)) {
                throw RPCException("Provided url not available", HttpStatusCode.BadRequest)
            }

            if (!jobs.canUseUrl(db, unverifiedJob.username, url)) {
                throw RPCException("Not allowed to use selected URL", HttpStatusCode.BadRequest)
            }
        }

        // Check peers
        run {
            val parameterPeers = application.invocation.parameters
                .filterIsInstance<ApplicationParameter.Peer>()
                .map { verifiedParameters[it.name] as AppParameterValue.Peer }

            val allPeers =
                unverifiedJob.request.resources.filterIsInstance<AppParameterValue.Peer>() + parameterPeers

            val duplicatePeers = allPeers
                .map { it.hostname }
                .groupBy { it }
                .filter { it.value.size > 1 }

            if (duplicatePeers.isNotEmpty()) {
                throw JobException.VerificationError(
                    "Duplicate hostname detected: " + duplicatePeers.keys.joinToString()
                )
            }

            TODO()
            /*
            val resolvedPeers = jobs.find(
                db,
                allPeers.map { it.jobId },
                unverifiedJob.username
            )

            val resolvedPeerIds = resolvedPeers.map { it.job.id }

            val missingPeer = allPeers.find { it.jobId !in resolvedPeerIds }
            if (missingPeer != null) {
                throw JobException.VerificationError("Could not find peer with id '${missingPeer.jobId}'")
            }

             */
        }

        // Verify membership of project
        if (unverifiedJob.project != null) {
            Projects.viewMemberInProject.call(
                ViewMemberInProjectRequest(
                    unverifiedJob.project,
                    unverifiedJob.username
                ),
                serviceClient
            ).orNull() ?: throw RPCException("Unable to verify membership of project", HttpStatusCode.Forbidden)
        }

        val id = UUID.randomUUID().toString()
        return VerifiedJobWithAccessToken(
            Job(
                id,
                JobOwner(
                    unverifiedJob.username,
                    unverifiedJob.project
                ),
                listOf(
                    JobUpdate(
                        id,
                        System.currentTimeMillis(),
                        JobState.IN_QUEUE,
                        "UCloud has accepted your job into the queue"
                    )
                ),
                JobBilling(creditsCharged = 0L, pricePerUnit = machine.pricePerUnit),
                unverifiedJob.request.copy(parameters = verifiedParameters),
            ),
            "TO BE REPLACED"
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

    private fun badValue(param: ApplicationParameter): Nothing {
        throw RPCException("Bad value provided for '${param.name}'", HttpStatusCode.BadRequest)
    }

    private suspend fun verifyParameters(
        app: Application,
        job: UnverifiedJob,
    ): Map<String, AppParameterValue> {
        val userParameters = HashMap(job.request.parameters)

        // TODO FIXME IMPORTANT
        // Check if we have any apps which use defaultValue for advanced types

        for (param in app.invocation.parameters) {
            var providedValue = userParameters[param.name]
            if (!param.optional && param.defaultValue == null && providedValue == null) {
                throw RPCException("Missing value for '${param.name}'", HttpStatusCode.BadRequest)
            }

            if (param.defaultValue == null && providedValue == null) {
                providedValue = TODO("Extract default value")
            }

            check(providedValue != null)

            when (param) {
                is ApplicationParameter.InputDirectory, is ApplicationParameter.InputFile -> {
                    if (providedValue !is AppParameterValue.File) badValue(param)
                }

                is ApplicationParameter.Text -> {
                    if (providedValue !is AppParameterValue.Text) badValue(param)
                }

                is ApplicationParameter.Integer -> {
                    if (providedValue !is AppParameterValue.Integer) badValue(param)
                }

                is ApplicationParameter.FloatingPoint -> {
                    if (providedValue !is AppParameterValue.FloatingPoint) badValue(param)
                }

                is ApplicationParameter.Bool -> {
                    if (providedValue !is AppParameterValue.Bool) badValue(param)
                }

                is ApplicationParameter.Enumeration -> {
                    if (providedValue !is AppParameterValue.Text) badValue(param)
                    val enumValue = param.options.find { it.name == providedValue.value } ?: badValue(param)

                    userParameters[param.name] = AppParameterValue.Text(enumValue.value)
                }

                is ApplicationParameter.Peer -> {
                    if (providedValue !is AppParameterValue.Peer) badValue(param)
                }

                is ApplicationParameter.LicenseServer -> {
                    if (providedValue !is AppParameterValue.License) badValue(param)
                    val license = AppLicenseDescriptions.get.call(
                        LicenseServerRequest(providedValue.id),
                        serviceClient
                    ).orRethrowAs { badValue(param) }

                    userParameters[param.name] = AppParameterValue.License(
                        id = license.id,
                        address = license.address,
                        port = license.port,
                        license = license.license
                    )
                }
            }
        }

        return userParameters
    }

    private data class FilePathAndType(
        val value: AppParameterValue.File,
        val desiredFileType: FileType,
        val name: String? = null,
    )

    private suspend fun collectFilesFromMounts(
        username: String,
        job: UnverifiedJob,
        userClient: AuthenticatedClient,
    ) {
        val transfers = job.request.resources
            .filterIsInstance<AppParameterValue.File>()
            .mapIndexed { i, transfer ->
                FilePathAndType(transfer, FileType.DIRECTORY, "mount-$i")
            }
        return collectFileBatch(username, userClient, transfers)
    }

    private suspend fun collectFilesFromParameters(
        username: String,
        application: Application,
        verifiedParameters: Map<String, AppParameterValue>,
        userClient: AuthenticatedClient,
    ) {
        return coroutineScope {
            val transfersFromParameters = application.invocation.parameters
                .asSequence()
                .filter { it is ApplicationParameter.InputFile || it is ApplicationParameter.InputDirectory }
                .mapNotNull { param ->
                    val transfer = verifiedParameters[param.name] ?: return@mapNotNull null
                    check(transfer is AppParameterValue.File)
                    FilePathAndType(
                        transfer,
                        if (param is ApplicationParameter.InputFile) FileType.FILE else FileType.DIRECTORY,
                        param.name
                    )
                }
                .toList()

            collectFileBatch(username, userClient, transfersFromParameters)
        }
    }

    private suspend fun collectFileBatch(
        username: String,
        userClient: AuthenticatedClient,
        transfers: List<FilePathAndType>,
    ) {
        return coroutineScope {
            transfers
                .map { async { collectSingleFile(username, userClient, it) } }
                .map { it.await() }
                .toSet()
        }
    }

    private suspend fun collectSingleFile(
        username: String,
        userClient: AuthenticatedClient,
        fileToCollect: FilePathAndType,
    ) {
        val (param, desiredFileType, paramName) = fileToCollect
        val sourcePath = param.path
        val stat = FileDescriptions.stat.call(StatRequest(sourcePath), userClient)
            .orThrowOnError {
                throw JobException.VerificationError("File not found or permission denied: $sourcePath")
            }
            .result

        if (stat.fileType != desiredFileType) {
            throw JobException.VerificationError(
                "Expected type of ${paramName} to be " +
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

        param.readOnly = param.readOnly || !hasWritePermission
    }

    data class HasPermissionForExistingMount(val hasPermissions: Boolean, val pathToFile: String?)

    suspend fun hasPermissionsForExistingMounts(
        jobWithToken: VerifiedJobWithAccessToken,
    ): HasPermissionForExistingMount {
        val outputFolder = run {
            val path = jobWithToken.job.output?.outputFolder
            if (path != null) {
                listOf(AppParameterValue.File(path, false))
            } else {
                emptyList()
            }
        }

        val job = jobWithToken.job
        val parameters = job.parameters ?: return HasPermissionForExistingMount(false, "/")
        val allFiles =
            parameters.parameters.values.filterIsInstance<AppParameterValue.File>() +
                parameters.resources.filterIsInstance<AppParameterValue.File>() +
                outputFolder
        val readOnlyFiles = allFiles.filter { it.readOnly }.map { it.path }
        val readWriteFiles = allFiles.filter { !it.readOnly }.map { it.path }
        if (readOnlyFiles.isNotEmpty()) {
            val knowledge = FileDescriptions.verifyFileKnowledge.call(
                VerifyFileKnowledgeRequest(
                    jobWithToken.job.owner.launchedBy,
                    readOnlyFiles,
                    KnowledgeMode.Permission(false)
                ),
                serviceClient
            ).orThrow()
            val lackingPermissions = knowledge.responses.indexOf(false)
            if (lackingPermissions != -1) {
                return HasPermissionForExistingMount(false, readOnlyFiles[lackingPermissions])
            }
        }

        if (readWriteFiles.isNotEmpty()) {
            val knowledge = FileDescriptions.verifyFileKnowledge.call(
                VerifyFileKnowledgeRequest(
                    jobWithToken.job.owner.launchedBy,
                    readWriteFiles,
                    KnowledgeMode.Permission(true)
                ),
                serviceClient
            ).orThrow()
            val lackingPermissions = knowledge.responses.indexOf(false)
            if (lackingPermissions != -1) {
                return HasPermissionForExistingMount(false, readWriteFiles[lackingPermissions])
            }
        }

        return HasPermissionForExistingMount(true, null)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
