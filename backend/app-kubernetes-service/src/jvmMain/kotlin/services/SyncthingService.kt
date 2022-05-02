package dk.sdu.cloud.app.kubernetes.services

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.k8.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.kubernetes.services.volcano.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.provider.api.Permission
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.*

class SyncthingService(
    private val providerId: String,

    private val jobs: JobManagement,

    private val pathConverter: PathConverter,
    private val memberFiles: MemberFiles,
    private val fs: NativeFS,

    private val serviceClient: AuthenticatedClient,
) : JobManagementPlugin {
    // NOTE(Dan): Syncthing is provided in UCloud as an integrated application. This essentially means that the
    // orchestrator is providing a very limited API to us (the provider) centered around pushing configuration. The
    // orchestrator expects us to use this configuration to update the storage and compute systems, in such a way that
    // Syncthing responds to the requested changes.
    //
    // The orchestrator has the following assumptions about how we do this:
    //
    // 1. We must register a job with UCloud (through `JobsControl.register`).
    //    a. It is assumed that every user gets their own job.
    //    b. Currently, the frontend does a lot of the work in finding the jobs, but the orchestrator might change to
    //       expose an endpoint which returns the relevant job IDs instead.
    // 2. The application must have at least one parameter called "stateFolder" which should be an input directory.
    // 3. The folder which has the state must contain a file called "ucloud_device_id.txt". This file must contain the
    //    device ID of the Syncthing server (which we host).
    //
    // There are no other strict requirements and we have quite a lot of freedom in each step.
    //
    // This provider implements this in a fairly straight forward way. These are the keypoints:
    //
    // 1. Every user is allocated a single state folder (on-demand). This state folder lives in their personal
    //    workspace. The folder is called "Syncthing". You can view the details of this in the
    //    `initializeConfigurationFolder` function.
    // 2. This state folder contains a configuration file called "ucloud_config.json". This file essentially contains
    //    the configuration received by the orchestrator in JSON format. See `retrieveConfiguration` and
    //    `updateConfiguration`.
    // 3. Syncthing is started as a completely normal compute job and is managed by the normal compute capabilities of
    //    this provider. The container consumes these configuration files and writes the device ID file (mentioned
    //    above).
    //    a. Some caveats apply to this job. See the `JobManagement.onCreate` function below.
    //    b. We launch the job on demand, based on invocations of `updateConfiguration`.
    // 4. Using a normal job simplifies the design tremendously. It means that the provider does not have to worry
    //    about permission management and quotas. All of this is already taken care of by the existing systems. This
    //    includes permission checking from the orchestrator and the accounting performed by the provider. This also
    //    means that we get the same isolation that the rest of the compute system has, which should provide
    //    good enough security guarantees.
    // 5. We notify the orchestrator about new mounts when they appear. The container will automatically trigger a
    //    restart based on the new configuration. See `synchronizeChangesWithJob` for details.

    suspend fun retrieveConfiguration(
        request: IAppsProviderRetrieveConfigRequest<SyncthingConfig>
    ): IAppsProviderRetrieveConfigResponse<SyncthingConfig> {
        // NOTE(Dan): We store the configuration in a file in the user's config directory. We read this and attempt to
        // parse it. If this file does not exist, then we will return a default configuration. If the file is corrupt,
        // then we notify the end-user.
        val configFile = findConfigFile(request.principal.createdBy)

        try {
            val rawConfig = fs.openForReading(configFile).reader().readText()
            return IAppsProviderRetrieveConfigResponse(
                "",
                defaultMapper.decodeFromString<SyncthingConfig>(rawConfig),
            )
        } catch (ex: FSException.NotFound) {
            // No configuration yet
            val defaultConfig = SyncthingConfig(emptyList(), emptyList())
            val (_, fileOutput) = fs.openForWriting(configFile, WriteConflictPolicy.REPLACE)
            fileOutput.writer().use { w ->
                w.write(defaultMapper.encodeToString(defaultConfig))
            }

            return IAppsProviderRetrieveConfigResponse("", defaultConfig)
        } catch (ex: Throwable) {
            // Most likely a corrupt file. Could also be an error of the file system, which is why we only suggest
            // resetting the config.
            throw RPCException(
                "It looks like your Syncthing configuration might be corrupt. " + 
                    "If this problem persists try resetting the server. " + 
                    "Contact support for further assistance.", 
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun resetConfiguration(
        request: IAppsProviderResetConfigRequest<SyncthingConfig>
    ): IAppsProviderResetConfigResponse<SyncthingConfig> {
        // NOTE(Dan): Resetting the configuration is done in two steps. First we delete all the files in the config
        // folder. But we _do not_ delete the configuration folder itself. This is important, since this folder might
        // be mounted by a running job.
        val configFolder = initializeConfigurationFolder(request.principal.createdBy)
        fs.listFiles(configFolder).forEach { path ->
            fs.delete(InternalFile(configFolder.path + "/" + path))
        }

        // NOTE(Dan): Following deletion of configuration, we perform a restart. This would not be possible if we had
        // deleted the entire configuration folder.
        doRestart(configFolder)
        return IAppsProviderResetConfigResponse<SyncthingConfig>()
    }

    suspend fun restart(
        request: IAppsProviderRestartRequest<SyncthingConfig>
    ): IAppsProviderRestartResponse<SyncthingConfig> {
        doRestart(initializeConfigurationFolder(request.principal.createdBy))
        return IAppsProviderRestartResponse<SyncthingConfig>()
    }

    private fun doRestart(configFolder: InternalFile) {
        // NOTE(Dan): Restarting works in collaboration with the application. The container will await a `restart.txt`
        // file and restart if it exists. This file is deleted at start-up, so we don't have to worry about the job
        // running when we create it.
        val restartFile = InternalFile(
            joinPath(
                configFolder.path,
                "restart.txt",
                isDirectory = false
            )
        )

        val (_, fileOutput) = fs.openForWriting(restartFile, WriteConflictPolicy.REPLACE)
        fileOutput.writer().use { w ->
            w.write("Restart required")
        }
    }

    suspend fun updateConfiguration(
        request: IAppsProviderUpdateConfigRequest<SyncthingConfig>
    ): IAppsProviderUpdateConfigResponse<SyncthingConfig> {
        val username = request.principal.createdBy
        val configFile = findConfigFile(username)
        val configDir = configFile.parent()
        val temporaryFile = InternalFile(joinPath(configDir.path, "ucloud_config_${UUID.randomUUID()}.json"))
        val (_, fileOutput) = fs.openForWriting(temporaryFile, WriteConflictPolicy.REPLACE)
        fileOutput.writer().use { w ->
            w.write(defaultMapper.encodeToString(request.config.normalize()))
        }

        // NOTE(Dan): We are intentionally not using the normalized configuration for synchronizing changes. We need to
        // know about the orchestrator info.
        synchronizeChangesWithJob(username, request.config, configDir)

        // NOTE(Dan): We create a temporary file and then replace it with a move operation to ensure the container
        // never sees a half-written file. This is because moves are guaranteed to be atomic. The container will
        // either see the entirety of the old file  or the entirety of the new file.
        fs.move(temporaryFile, configFile, WriteConflictPolicy.REPLACE)

        return IAppsProviderUpdateConfigResponse<SyncthingConfig>()
    }

    // Job utilities
    private suspend fun synchronizeChangesWithJob(
        username: String,
        config: SyncthingConfig,
        configFolder: InternalFile
    ) {
        if (config.devices.isEmpty() || config.folders.isEmpty()) {
            // NOTE(Dan): Cancel the job (if it exists) if we no devices or no folders. That is, the job is no longer
            // needed.
            val job = findJobIfExists(username)
            if (job != null) {
                jobs.cancel(job)
            }
        } else {
            // NOTE(Dan): Otherwise, we make sure that the job is running and potentially notify the provider about
            // new mounts. The orchestrator will verify the permissions of these mounts, and we don't have to worry
            // about bad configuration.
            val (job, jobWasCreated) = startJobIfNeeded(username, config, configFolder)
            if (!jobWasCreated) {
                jobs.k8.updateMounts(job.id, config.folders.map { it.ucloudPath })
            } else {
                // We don't need to do anything else. The mounts have already been added to the job.
            }
        }
    }

    private data class SyncthingJobStatus(val job: Job, val wasCreated: Boolean)
    private suspend fun startJobIfNeeded(
        username: String, 
        initialConfig: SyncthingConfig,
        configFolder: InternalFile,
    ): SyncthingJobStatus {
        val orchestratorInfo = initialConfig.orchestratorInfo ?: throw RPCException(
            "Received an invalid message from UCloud/Core. Unable to update configuration of Syncthing!",
            HttpStatusCode.BadRequest
        )

        val existingJob = findJobIfExists(username)
        if (existingJob != null) return SyncthingJobStatus(existingJob, wasCreated = false)

        // TODO Race condition if two calls arrive for the same user

        val newJob = jobs.registerApplication(
            JobSpecification(
                name = "Syncthing Default Server",
                application = syncthingApplication,
                product = ProductReference(productId, productCategory, providerId),

                parameters = mapOf(
                    "stateFolder" to AppParameterValue.File(
                        pathConverter.internalToUCloud(configFolder).path,
                        readOnly = false,
                    )
                ),

                // NOTE(Dan): The orchestrator has already verified that we have at least read permissions for all of
                // these. We can also look up to determine if the folder should be mounted as read only. We need to do
                // some of this since the orchestrator will approve whatever we tell it to approve (since we are
                // registering it and not the user).

                resources = initialConfig.folders.mapNotNull { folder ->
                    val permissions: List<Permission> = orchestratorInfo.folderPathToPermission[folder.ucloudPath]
                        ?: return@mapNotNull null
                    if (permissions.isEmpty()) return@mapNotNull null  

                    val isReadOnly = !permissions.contains(Permission.ADMIN) && !permissions.contains(Permission.EDIT)
                    if (isReadOnly && !permissions.contains(Permission.READ)) {
                        log.warn("We have determined that we should have read only for ${folder.ucloudPath} " + 
                            "but we have no read permission! Config: $initialConfig")
                        return@mapNotNull null
                    }

                    AppParameterValue.File(
                        folder.ucloudPath,
                        readOnly = isReadOnly
                    )
                },

                timeAllocation = null,
                restartOnExit = true,
            ),
            username
        )

        return SyncthingJobStatus(newJob, wasCreated = true)
    }

    private suspend fun findJobIfExists(username: String): Job? {
        val jobPage = JobsControl.browse.call(
            ResourceBrowseRequest(
                JobIncludeFlags(
                    filterApplication = syncthingApplication.name,
                    filterCreatedBy = username,
                    filterProductId = productId,
                    filterProductCategory = productCategory,

                    includeApplication = true,
                    includeParameters = true,
                    includeProduct = true,
                ),
                sortBy = "createdAt",
                sortDirection = SortDirection.descending
            ),
            serviceClient
        ).orThrow()

        return jobPage
            .items
            .filter { job -> !job.status.state.isFinal() && job.owner.project == null }
            .firstOrNull()
    }

    // File utilities
    private suspend fun fileExists(file: InternalFile): Boolean {
        return try {
            fs.stat(file)
            true
        } catch (ex: FSException.NotFound) {
            false
        }
    }

    private suspend fun initializeConfigurationFolder(username: String): InternalFile {
        val targetDirectory = InternalFile(
            joinPath(
                memberFiles.initializeMemberFiles(username, null).path,
                "Syncthing"
            )
        )
        
        // TODO What if this is not a directory?
        if (fileExists(targetDirectory)) return targetDirectory
        
        fs.createDirectories(targetDirectory)
        return targetDirectory
    }

    private suspend fun findConfigFile(username: String): InternalFile {
        return InternalFile(
            joinPath(
                initializeConfigurationFolder(username).path,
                "ucloud_config.json",
                isDirectory = false
            )
        )
    }

    // Config normalization
    private fun SyncthingConfig.normalize(): SyncthingConfig {
        return copy(
            folders = folders.map { it.normalize() },
            orchestratorInfo = null,
        )
    }

    private fun SyncthingConfig.Folder.normalize(): SyncthingConfig.Folder {
        return copy(
            path = "/work/${ucloudPath.fileName()}",
            id = if (id != "") id else UUID.randomUUID().toString()
        )
    }

    // Job management plugin
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        // NOTE(Dan): This plugin is designed to run after all the normal plugins have already run. As a result, this
        // plugin has full range to do whatever modifications it desires. Of course, this plugin is only interested in
        // syncthing related jobs, so we start by verifying that we are on the correct product.

        run {
            // Check if this is an interesting job
            val product = job.specification.product
            val isSyncthing = product.id == productId && product.category == productCategory
            if (!isSyncthing) return
        }

        run {
            // Verify that only the syncthing application will run on this product
            if (job.specification.application != syncthingApplication) {
                throw RPCException(
                    "This product can only be used to launch Syncthing for synchronization purposes",
                    HttpStatusCode.BadRequest,
                )
            }
        }

        run {
            // Verify that we are only running on a single node (orchestrator should also verify this, this is only a
            // sanity check)
            if (job.specification.replicas != 1) {
                throw RPCException(
                    "Syncthing can only be run with a single replica",
                    HttpStatusCode.BadRequest
                )
            }
        }

        // NOTE(Dan): Next, we make some modifications to the scheduling parameters. This ensures that we have better
        // utilization of our cluster resources.
        val vSpec = builder.spec ?: error("no volcano job spec")
        val tasks = (vSpec.tasks ?: emptyList())

        run {
            // Schedule with fewer resources attached
            tasks.forEach { task ->
                val containers = task?.template?.spec?.containers ?: emptyList()
                containers.forEach { c -> 
                    // TODO(Dan): Re-evaluate these numbers based on some proper tests. I am very much guessing at
                    // what the values should be.
                    c.resources = Pod.Container.ResourceRequirements(
                        requests = JsonObject(mapOf(
                            "cpu" to JsonPrimitive("100m"),
                            "memory" to JsonPrimitive("200Mi")
                        )),
                        limits = JsonObject(mapOf(
                            "cpu" to JsonPrimitive("2000m"),
                            "memory" to JsonPrimitive("500Mi")
                        ))
                    )
                }
            }
        }

        run {
            // Reduce shared memory
            tasks.forEach { task ->
                val volumes = task?.template?.spec?.volumes ?: emptyList()
                volumes.forEach { v ->
                    if (v.name == SharedMemoryPlugin.SHARED_MEMORY_VOLUME) {
                        v.emptyDir = Volume.EmptyDirVolumeSource(
                            medium = "Memory",
                            sizeLimit = "200Mi"
                        )
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        val syncthingApplication = NameAndVersion("syncthing", "1")
        const val productId = "syncthing"
        const val productCategory = "syncthing"
    }
}

