package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.db
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class JobException(message: String, code: HttpStatusCode) : RPCException(message, code) {
    class VerificationError(message: String) : JobException(message, HttpStatusCode.BadRequest)
}

class JobVerificationService(
    private val appService: ApplicationCache,
    private val orchestrator: JobResourceService,
    private val fileCollections: FileCollectionService,
) {
    suspend fun verifyOrThrow(
        actorAndProject: ActorAndProject,
        specification: JobSpecification,
    ) {
        val application = appService.resolveApplication(specification.application)
            ?: throw JobException.VerificationError("Application does not exist")

        if (specification.parameters == null || specification.resources == null) {
            throw JobException.VerificationError("Bad client request. Missing parameters or resources.")
        }

        if (specification.timeAllocation != null) {
            if (specification.timeAllocation!!.toMillis() <= 0) {
                throw JobException.VerificationError("Time allocated for job is too short.")
            }
            if (application.invocation.tool.tool?.description?.backend == ToolBackend.VIRTUAL_MACHINE) {
                specification.timeAllocation = null
            }
        }

        val sshMode = application.invocation.ssh?.mode ?: SshDescription.Mode.DISABLED
        if (specification.sshEnabled == true && sshMode == SshDescription.Mode.DISABLED) {
            throw JobException.VerificationError("This application does not support SSH but sshEnabled was true")
        }

        val verifiedParameters = verifyParameters(application, specification)
        val resources = specification.resources!!
        val newResources = ArrayList<AppParameterValue>()

        // Check peers
        run {
            val parameterPeers = application.invocation.parameters
                .filterIsInstance<ApplicationParameter.Peer>()
                .mapNotNull { verifiedParameters[it.name] as AppParameterValue.Peer? }

            val resourcePeers = resources.filterIsInstance<AppParameterValue.Peer>()

            val allPeers = resourcePeers + parameterPeers

            val duplicatePeers = allPeers
                .map { it.hostname }
                .groupBy { it }
                .filter { it.value.size > 1 }

            if (duplicatePeers.isNotEmpty()) {
                throw JobException.VerificationError(
                    "Duplicate hostname detected: " + duplicatePeers.keys.joinToString()
                )
            }

            if (parameterPeers.size != checkAndReturnValidPeers(actorAndProject, parameterPeers).size) {
                throw JobException.VerificationError("You are not allowed to use one or more of your connected jobs")
            }

            newResources.addAll(checkAndReturnValidPeers(actorAndProject, resourcePeers))
        }

        // Check mounts
        run {
            val mounts = resources.filterIsInstance<AppParameterValue.File>()
            newResources.addAll(checkAndReturnValidFiles(actorAndProject, mounts))
        }

        // Check ingress
        run {
            // NOTE(Dan): Already checked, just need to add the resources
            val ingresses = resources.filterIsInstance<AppParameterValue.Ingress>()
            newResources.addAll(ingresses)
        }

        // Check networks
        run {
            // NOTE(Dan): Already checked, just need to add the resources
            val networks = resources.filterIsInstance<AppParameterValue.Network>()
            newResources.addAll(networks)
        }

        // Check parameters files
        val parameters = specification.parameters!!.values
        run {
            val files = parameters.filterIsInstance<AppParameterValue.File>()
            val validFiles = checkAndReturnValidFiles(actorAndProject, files)
            if (files.size != validFiles.size) {
                throw JobException.VerificationError("You are not allowed to use one or more of your files")
            }
        }

        specification.resources = newResources
    }

    private suspend fun checkAndReturnValidPeers(
        actorAndProject: ActorAndProject,
        peers: List<AppParameterValue.Peer>
    ): List<AppParameterValue.Peer> {
        val validPeers = orchestrator.validatePermission(
            actorAndProject,
            peers.map { it.jobId },
            Permission.EDIT
        )

        return peers.filter { input ->
            validPeers.any { valid -> valid == input.jobId }
        }
    }

    private suspend fun translatePotentialShares(
        actorAndProject: ActorAndProject,
        files: List<AppParameterValue.File>
    ): List<AppParameterValue.File> {
        return files.map { file ->
            val components = file.path.components()

            if (components.size == 1 && components.first().toLongOrNull() == null) {
                // In this case, we might be looking at a share
                val matchingShares = db
                    .withSession { session ->
                        session.sendPreparedStatement(
                            {
                                setParameter("user", actorAndProject.actor.safeUsername())
                                setParameter("path", components.first())
                            },
                            """
                                select available_at
                                from file_orchestrator.shares s 
                                where
                                    shared_with = :user
                                    and regexp_substr(original_file_path, '[^\/]*$') = :path
                            """
                        )
                    }
                    .rows
                    .map { it.getString(0)!! }

                if (matchingShares.size > 1) {
                    throw RPCException(
                        "Multiple shares found with same name. Please select subfolder inside selected mount.",
                        HttpStatusCode.BadRequest
                    )
                } else if (matchingShares.size == 1) {
                    AppParameterValue.File(path = matchingShares.single(), file.readOnly)
                } else {
                    file
                }
            } else {
                file
            }
        }
    }

    suspend fun checkAndReturnValidFiles(
        actorAndProject: ActorAndProject,
        files: List<AppParameterValue.File>
    ): List<AppParameterValue.File> {
        val actualFiles = ArrayList<AppParameterValue.File>()

        val translatedFiles = translatePotentialShares(actorAndProject, files)
        val requiredCollections = translatedFiles.map { file -> extractPathMetadata(file.path).collection }.toSet()
        val retrievedCollections = fileCollections
            .retrieveBulk(actorAndProject, requiredCollections, listOf(Permission.READ), requireAll = false)
            .associateBy { it.id }

        // Check if we attempt to mount files with same name -> conflict in k8
        val filenames = translatedFiles.map { it.path.split("/").last() }.toSet()
        if (translatedFiles.size != filenames.size) {
            throw RPCException("Cannot mount files with same name", HttpStatusCode.BadRequest)
        }

        for (file in translatedFiles) {
            val perms = retrievedCollections[extractPathMetadata(file.path).collection]?.permissions?.myself
                ?: continue

            val allowWrite = perms.any { it == Permission.EDIT || it == Permission.ADMIN }
            file.readOnly = !allowWrite
            actualFiles.add(file)
        }
        return actualFiles
    }

    private fun badValue(param: ApplicationParameter): Nothing {
        throw RPCException("Bad value provided for '${param.name}'", HttpStatusCode.BadRequest)
    }

    private fun verifyParameters(
        app: Application,
        specification: JobSpecification,
    ): Map<String, AppParameterValue> {
        val userParameters = HashMap(specification.parameters)

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

                    is ApplicationParameter.Text,
                    is ApplicationParameter.TextArea -> {
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
                            ?.toDoubleOrNull()
                            ?.let { AppParameterValue.FloatingPoint(it) }
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

                    else -> error("unknown application parameter: ${param}")
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

                is ApplicationParameter.Text, is ApplicationParameter.TextArea -> {
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

    companion object : Loggable {
        override val log = logger()
    }
}
