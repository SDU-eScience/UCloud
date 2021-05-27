package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.util.orThrowOnError
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.*

data class UnverifiedJob(
    val request: JobSpecification,
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
    private val productCache: ProductCache,
    private val providerSupport: ProviderSupportService
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        listeners: List<JobListener> = emptyList(),
    ): VerifiedJobWithAccessToken {
        val application = findApplication(unverifiedJob)
        val tool = application.invocation.tool.tool!!

        if (unverifiedJob.request.parameters == null || unverifiedJob.request.resources == null) {
            throw JobException.VerificationError("Bad client request. Missing parameters or resources.")
        }

        if (unverifiedJob.request.timeAllocation != null) {
            if (unverifiedJob.request.timeAllocation!!.toMillis() <= 0) {
                throw JobException.VerificationError("Time allocated for job is too short.")
            }
        }

        val appBackend = tool.description.backend
        val support = try {
            providerSupport.retrieveProductSupport(unverifiedJob.request.product).support
        } catch (ex: Throwable) {
            throw JobException.VerificationError("Invalid machine type supplied")
        }

        // Check provider support
        val comms = providers.prepareCommunication(unverifiedJob.request.product.provider)
        run {
            if (appBackend == ToolBackend.DOCKER && support.docker.enabled != true) {
                throw JobException.VerificationError("The selected machine does not support this application")
            }

            if (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.enabled != true) {
                throw JobException.VerificationError("The selected machine does not support this application")
            }

            if (appBackend == ToolBackend.SINGULARITY) {
                throw RPCException(
                    "Application is no longer supported. Please contact support.",
                    HttpStatusCode.BadRequest
                )
            }
        }

        // Check product
        val machine = productCache.find<Product.Compute>(
            unverifiedJob.request.product.provider,
            unverifiedJob.request.product.id,
            unverifiedJob.request.product.category
        ) ?: throw RPCException("Invalid machine type selected", HttpStatusCode.BadRequest)

        // Check input parameters
        val verifiedParameters = verifyParameters(
            application,
            unverifiedJob
        )

        // Check peers
        val resources = unverifiedJob.request.resources!!
        run {
            val parameterPeers = application.invocation.parameters
                .filterIsInstance<ApplicationParameter.Peer>()
                .mapNotNull { verifiedParameters[it.name] as AppParameterValue.Peer? }

            val allPeers =
                resources.filterIsInstance<AppParameterValue.Peer>() + parameterPeers

            val duplicatePeers = allPeers
                .map { it.hostname }
                .groupBy { it }
                .filter { it.value.size > 1 }

            if (duplicatePeers.isNotEmpty()) {
                throw JobException.VerificationError(
                    "Duplicate hostname detected: " + duplicatePeers.keys.joinToString()
                )
            }

            val resolvedPeers = jobs.retrievePrivileged(
                db,
                allPeers.map { it.jobId },
                JobDataIncludeFlags()
            )

            if (!resolvedPeers.values.all { it.job.owner.launchedBy == unverifiedJob.username }) {
                throw JobException.VerificationError("You do not have permissions to connect to some of your peers")
            }

            val resolvedPeerIds = resolvedPeers.keys

            val missingPeer = allPeers.find { it.jobId !in resolvedPeerIds }
            if (missingPeer != null) {
                throw JobException.VerificationError("Could not find peer with id '${missingPeer.jobId}'")
            }
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

        val (wallets) = Wallets.retrieveBalance.call(
            RetrieveBalanceRequest(
                unverifiedJob.project ?: unverifiedJob.username,
                if (unverifiedJob.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                includeChildren = false,
                showHidden = true
            ),
            serviceClient
        ).orThrow()

        val allocated = wallets
            .find {
                it.area == ProductArea.COMPUTE &&
                    it.wallet.paysFor.provider == unverifiedJob.request.product.provider &&
                    it.wallet.paysFor.id == unverifiedJob.request.product.category
            }
            ?.allocated ?: 0L

        val id = UUID.randomUUID().toString()
        val verified = VerifiedJobWithAccessToken(
            Job(
                id,
                JobOwner(
                    unverifiedJob.username,
                    unverifiedJob.project
                ),
                listOf(
                    JobUpdate(
                        Time.now(),
                        JobState.IN_QUEUE,
                        "UCloud has accepted your job into the queue"
                    )
                ),
                JobBilling(
                    creditsCharged = 0L,
                    pricePerUnit = machine.pricePerUnit,
                    __creditsAllocatedToWalletDoNotDependOn__ = allocated
                ),
                unverifiedJob.request.copy(
                    parameters = verifiedParameters,
                    timeAllocation = unverifiedJob.request.timeAllocation ?: tool.description.defaultTimeAllocation
                ),
                JobStatus(JobState.IN_QUEUE),
                System.currentTimeMillis()
            ),
            "REPLACED_LATER"
        )

        listeners.forEach { it.onVerified(db, verified.job) }
        return verified
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

        for (param in app.invocation.parameters) {
            var providedValue = userParameters[param.name]
            if (!param.optional && param.defaultValue == null && providedValue == null) {
                log.debug("Missing value: param=${param} providedValue=${providedValue}")
                throw RPCException("Missing value for '${param.name}'", HttpStatusCode.BadRequest)
            }

            if (param.optional && providedValue == null && param.defaultValue == null) {
                continue // Nothing to validate
            }

            if (param.defaultValue != null && providedValue == null) {
                providedValue = when (param) {
                    is ApplicationParameter.InputFile,
                    is ApplicationParameter.InputDirectory,
                    is ApplicationParameter.Peer,
                    is ApplicationParameter.LicenseServer,
                    is ApplicationParameter.Ingress,
                    is ApplicationParameter.NetworkIP,
                    -> null // Not supported and application should not have been validated. Silently fail.

                    is ApplicationParameter.Text -> {
                        ((param.defaultValue as? JsonObject)?.get("value") as? JsonPrimitive)?.let {
                            AppParameterValue.Text(it.content)
                        } ?: (param.defaultValue as? JsonPrimitive)?.let { AppParameterValue.Text(it.content) }
                    }
                    is ApplicationParameter.Integer -> {
                        ((param.defaultValue as? JsonObject)?.get("value") as? JsonPrimitive)
                            ?.content?.toLongOrNull()
                            ?.let { AppParameterValue.Integer(it) }
                            ?: (param.defaultValue as? JsonPrimitive)?.content?.toLongOrNull()?.let {
                                AppParameterValue.Integer(it)
                            }
                    }
                    is ApplicationParameter.FloatingPoint -> {
                        ((param.defaultValue as? JsonObject)?.get("value") as? JsonPrimitive)
                            ?.content
                            ?.toLongOrNull()
                            ?.let { AppParameterValue.FloatingPoint(it.toDouble()) }
                            ?: (param.defaultValue as? JsonPrimitive)?.content?.toDoubleOrNull()?.let {
                                AppParameterValue.FloatingPoint(it)
                            }
                    }
                    is ApplicationParameter.Bool -> {
                        ((param.defaultValue as? JsonObject)?.get("value") as? JsonPrimitive)
                            ?.content?.toBoolean()?.let { AppParameterValue.Bool(it) }
                            ?: (param.defaultValue as? JsonPrimitive)?.content?.toBoolean()
                                ?.let { AppParameterValue.Bool(it) }
                    }
                    is ApplicationParameter.Enumeration -> {
                        (param.defaultValue as? JsonObject)?.let { map ->
                            val value = (map["value"] as? JsonPrimitive)?.content
                            val option = param.options.find { it.value == value }
                            if (option != null) {
                                // Note: We have some applications already in production where this is allowed. We need
                                // to change this in the future
                                log.info("Missing value in enumeration: $value")
                            }

                            if (value != null) AppParameterValue.Text(value) else null
                        }
                    }

                    else -> error("unknown application parameter")
                }

                if (providedValue == null) {
                    log.warn("This shouldn't happen!")
                    log.warn("Param: $param")
                    log.warn("Default value: ${param.defaultValue} ${param.defaultValue?.javaClass}")
                    throw RPCException("Missing value for '${param.name}'", HttpStatusCode.BadRequest)
                }
            }

            check(providedValue != null) { "Missing value for $param" }
            userParameters[param.name] = providedValue

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
                    // We have apps in prod where this isn't true
                    //param.options.find { it.value == providedValue.value } ?: badValue(param)
                }

                is ApplicationParameter.Peer -> {
                    if (providedValue !is AppParameterValue.Peer) badValue(param)
                }

                is ApplicationParameter.LicenseServer -> {
                    if (providedValue !is AppParameterValue.License) badValue(param)
                }

                is ApplicationParameter.Ingress -> {
                    if (providedValue !is AppParameterValue.Ingress) badValue(param)
                }

                is ApplicationParameter.NetworkIP -> {
                    if (providedValue !is AppParameterValue.Network) badValue(param)
                }
            }
        }

        return userParameters
    }

    private data class FilePathAndType(
        val value: AppParameterValue.File,
        val desiredFileType: Any?,
        val name: String? = null,
    )

    private suspend fun collectFilesFromMounts(
        username: String,
        job: UnverifiedJob,
        userClient: AuthenticatedClient,
    ) {
        /*
        val transfers = job.request.resources!!
            .filterIsInstance<AppParameterValue.File>()
            .mapIndexed { i, transfer ->
                FilePathAndType(transfer, FileType.DIRECTORY, "mount-$i")
            }
        return collectFileBatch(username, userClient, transfers)

         */
    }

    private suspend fun collectFilesFromParameters(
        username: String,
        application: Application,
        verifiedParameters: Map<String, AppParameterValue>,
        userClient: AuthenticatedClient,
    ) {
        /*
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
         */
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
        /*
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
         */
    }

    data class HasPermissionForExistingMount(val hasPermissions: Boolean, val pathToFile: String?)

    suspend fun hasPermissionsForExistingMounts(
        jobWithToken: VerifiedJobWithAccessToken,
    ): HasPermissionForExistingMount {
        return HasPermissionForExistingMount(true, null)
        /*
        val outputFolder = run {
            val path = jobWithToken.job.output?.outputFolder
            if (path != null) {
                listOf(AppParameterValue.File(path, false))
            } else {
                emptyList()
            }
        }

        val job = jobWithToken.job
        val parameters = job.specification.parameters ?: return HasPermissionForExistingMount(false, "/")
        val resources = job.specification.resources ?: return HasPermissionForExistingMount(false, "/")
        val allFiles =
            parameters.values.filterIsInstance<AppParameterValue.File>() +
                resources.filterIsInstance<AppParameterValue.File>() +
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
         */
    }

    companion object : Loggable {
        override val log = logger()
    }
}
