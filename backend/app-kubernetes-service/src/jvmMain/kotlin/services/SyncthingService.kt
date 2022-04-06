package dk.sdu.cloud.app.kubernetes.services

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.app.orchestrator.api.*
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
import java.util.*

class SyncthingService(
    private val providerId: String,

    private val jobs: JobManagement,

    private val pathConverter: PathConverter,
    private val memberFiles: MemberFiles,
    private val fs: NativeFS,

    private val serviceClient: AuthenticatedClient,
) {
    suspend fun retrieveConfiguration(
        request: IAppsProviderRetrieveConfigRequest<SyncthingConfig>
    ): IAppsProviderRetrieveConfigResponse<SyncthingConfig> {
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
        fs.delete(initializeConfigurationFolder(request.principal.createdBy))
        return IAppsProviderResetConfigResponse<SyncthingConfig>()
    }

    suspend fun updateConfiguration(
        request: IAppsProviderUpdateConfigRequest<SyncthingConfig>
    ): IAppsProviderUpdateConfigResponse<SyncthingConfig> {
        val username = request.principal.createdBy
        val configFile = findConfigFile(username)
        val configDir = configFile.parent()
        val temporaryFile = InternalFile(joinPath(configDir.path, "ucloud_config_${Time.now()}.json"))
        val (_, fileOutput) = fs.openForWriting(temporaryFile, WriteConflictPolicy.REPLACE)
        fileOutput.writer().use { w ->
            w.write(defaultMapper.encodeToString(request.config.normalize()))
        }

        // NOTE(Dan): We are intentionally not using the normalized configuration for synchronizing changes. We need to
        // know about the orchestrator info.
        synchronizeChangesWithJob(username, request.config, configDir)

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
            // TODO("Shutdown job")
        } else {
            val (job, jobWasCreated) = startJobIfNeeded(username, config, configFolder)
            if (!jobWasCreated) {
                // TODO("Synchronize new mounts")
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
                        pathConverter.internalToUCloud(configFolder).path
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

                // No expiration
                timeAllocation = null,
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

    companion object : Loggable {
        override val log = logger()
        val syncthingApplication = NameAndVersion("syncthing", "1")
        const val productId = "syncthing"
        const val productCategory = "syncthing"
    }
}

