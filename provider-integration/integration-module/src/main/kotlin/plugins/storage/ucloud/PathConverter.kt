package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.child
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.service.SimpleCache

const val driveProjectHomeName = "project-home"
const val driveShareName = "share"

class PathConverter(
    private val sensitiveProjects: Set<String>,
    private val serviceClient: AuthenticatedClient,
    val locator: DriveLocator,
) {
    private val shareCache = SimpleCache<String, UCloudFile>(
        maxAge = 60_000,
        lookup = { shareId ->
            UCloudFile.create(
                SharesControl.retrieve.call(
                    ResourceRetrieveRequest(ShareFlags(), shareId),
                    serviceClient
                ).orThrow().specification.sourceFilePath
            )
        }
    )

    suspend fun ucloudToInternal(file: UCloudFile): InternalFile {
        val components = file.components()
        val collectionId = components[0].toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val driveAndSystem = locator.resolveDrive(collectionId)
        if (driveAndSystem.drive is UCloudDrive.Share) {
            val resolvedShare = shareCache.get(driveAndSystem.drive.shareId)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val internalShare = ucloudToInternal(resolvedShare)
            return internalShare.child(components.drop(1).joinToString("/"))
        }

        val driveRoot =
            driveAndSystem.driveRoot ?: throw RPCException("No drive root? $file", HttpStatusCode.InternalServerError)

        return if (components.size == 1) {
            InternalFile(driveRoot.path.removeSuffix("/"))
        } else {
            driveRoot.child(components.drop(1).joinToString("/").removeSuffix("/"))
        }
    }

    suspend fun internalToUCloud(file: InternalFile): UCloudFile {
        val drive = locator.resolveDriveByInternalFile(file)
        val driveRoot = drive.driveRoot ?: throw RPCException("No drive root? $file", HttpStatusCode.InternalServerError)
        val internalPath = file.path.removePrefix(driveRoot.path.removeSuffix("/") + "/").removeSuffix("/")
        if (file.path.removeSuffix("/") == driveRoot.path.removeSuffix("/")) {
            return UCloudFile.createFromPreNormalizedString("/${drive.drive.ucloudId}")
        }
        return UCloudFile.createFromPreNormalizedString("/${drive.drive.ucloudId}/$internalPath")
    }

    private val collectionCache = SimpleCache<String, FileCollection> { collId ->
        FileCollectionsControl.retrieve.call(
            ResourceRetrieveRequest(FileCollectionIncludeFlags(), collId),
            serviceClient
        ).orThrow()
    }

    suspend fun findProjectOwner(driveId: String): String? {
        return (collectionCache.get(driveId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound))
            .owner.project
    }

    suspend fun driveIsSensitive(driveId: String): Boolean {
        return findProjectOwner(driveId) in sensitiveProjects
    }

    suspend fun shouldAllowUsageOfFilesTogether(paths: List<UCloudFile>): Boolean {
        val projectOwners = paths
            .mapNotNull { it.components().firstOrNull() }
            .map { findProjectOwner(it) }
            .toSet()

        if (projectOwners.any { it in sensitiveProjects }) {
            return projectOwners.size == 1
        }

        return true
    }

    suspend fun pathIsSensitive(path: UCloudFile): Boolean {
        val components = path.components()
        val collectionId = components[0]
        return findProjectOwner(collectionId) in sensitiveProjects
    }

    companion object {
        const val HOME_DIRECTORY = "home"
        const val PROJECT_DIRECTORY = "projects"
        const val COLLECTION_DIRECTORY = "collections"
        const val PERSONAL_REPOSITORY = "Members' Files"
        const val INVALID_FILE_ERROR_CODE = "INVALID_FILE"
    }
}
