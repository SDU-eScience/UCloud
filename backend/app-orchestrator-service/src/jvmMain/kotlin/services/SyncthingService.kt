package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.invokeCall
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService

class SyncthingService(
    private val providers: Providers<*>,
    private val serviceClient: AuthenticatedClient,
    private val fileCollections: FileCollectionService,
) {
    // NOTE(Dan): Syncthing is provided in UCloud as an integrated application. This essentially means that the
    // orchestrator is providing a very limited API to the provider centered around pushing configuration. The
    // orchestrator expects the provider to use this configuration to update the storage and compute systems, in such 
    // a way that Syncthing responds to the requested changes.
    //
    // The orchestrator has the following assumptions about how the provider does this:
    //
    // 1. They must register a job with UCloud (through `JobsControl.register`).
    //    a. It is assumed that every user gets their own job.
    //    b. Currently, the frontend does a lot of the work in finding the jobs, but the orchestrator might change to
    //       expose an endpoint which returns the relevant job IDs instead.
    // 2. The application must have at least one parameter called "stateFolder" which should be an input directory.
    // 3. The folder which has the state must contain a file called "ucloud_device_id.txt". This file must contain the
    //    device ID of the Syncthing server.
    //
    // For more details, see the implemention in UCloud/Compute.
    //
    // Almost all functions in this service are simply proxing the relevant information to the provider. The extra
    // information added by this service is mostly related to authorization.

    suspend fun retrieveConfiguration(
        actorAndProject: ActorAndProject,
        request: IAppsRetrieveConfigRequest<SyncthingConfig>
    ): IAppsRetrieveConfigResponse<SyncthingConfig> {
        return providers.invokeCall(
            request.providerId,
            actorAndProject,
            { SyncthingProvider(request.providerId).retrieveConfiguration },
            IAppsProviderRetrieveConfigRequest(
                ResourceOwner(actorAndProject.actor.safeUsername(), null),
            )
        ).also { result ->
            result.copy(
                config = result.config.copy(orchestratorInfo = null)
            )
        }
    }

    suspend fun updateConfiguration(
        actorAndProject: ActorAndProject,
        request: IAppsUpdateConfigRequest<SyncthingConfig>
    ): IAppsUpdateConfigResponse<SyncthingConfig> {
        val newConfig = run {
            // Verify that the user has at least read permissions for all of the folders.
            val collectionIds = request.config.folders.map { extractPathMetadata(it.ucloudPath).collection }.toSet()
            val resolvedCollections = fileCollections.retrieveBulk(
                actorAndProject, 
                collectionIds, 
                listOf(Permission.READ), 
                requireAll = false
            )

            val folderPathToPermission = request.config.folders.mapNotNull { folder ->
                val folderCollectionId = extractPathMetadata(folder.ucloudPath).collection
                val coll = resolvedCollections.find { coll -> coll.id == folderCollectionId }
                    ?: return@mapNotNull null

                val myPermissions = coll.permissions?.myself ?: emptyList()
                if (myPermissions.isEmpty()) return@mapNotNull null

                folder.ucloudPath to myPermissions
            }.toMap()

            val safeFolders = request.config.folders.filter { folder ->
                folderPathToPermission[folder.ucloudPath] != null
            }

            request.config.copy(
                folders = safeFolders,
                orchestratorInfo = SyncthingConfig.OrchestratorInfo(
                    folderPathToPermission = folderPathToPermission,
                )
            )
        }

        return providers.invokeCall(
            request.providerId,
            actorAndProject,
            { SyncthingProvider(request.providerId).updateConfiguration },
            IAppsProviderUpdateConfigRequest(
                ResourceOwner(actorAndProject.actor.safeUsername(), null),
                newConfig,
                request.expectedETag,
            )
        )
    }

    suspend fun resetConfiguration(
        actorAndProject: ActorAndProject,
        request: IAppsResetConfigRequest<SyncthingConfig>
    ): IAppsResetConfigResponse<SyncthingConfig> {
        return providers.invokeCall(
            request.providerId,
            actorAndProject,
            { SyncthingProvider(request.providerId).resetConfiguration },
            IAppsProviderResetConfigRequest(
                ResourceOwner(actorAndProject.actor.safeUsername(), null),
                request.expectedETag,
            )
        )
    }

    suspend fun restart(
        actorAndProject: ActorAndProject,
        request: IAppsRestartRequest<SyncthingConfig>
    ): IAppsRestartResponse<SyncthingConfig> {
        return providers.invokeCall(
            request.providerId,
            actorAndProject,
            { SyncthingProvider(request.providerId).restart },
            IAppsProviderRestartRequest(
                ResourceOwner(actorAndProject.actor.safeUsername(), null),
            )
        )
    }
}

