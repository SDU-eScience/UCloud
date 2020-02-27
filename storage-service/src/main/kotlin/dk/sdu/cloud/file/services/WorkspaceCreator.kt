package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.service.DistributedLock
import java.nio.file.Path
import java.nio.file.PathMatcher

interface WorkspaceCreator {
    suspend fun create(
        user: String,
        mounts: List<WorkspaceMount>,
        allowFailures: Boolean,
        createSymbolicLinkAt: String
    ): CreatedWorkspace

    suspend fun transfer(
        id: String,
        manifest: WorkspaceManifest,
        replaceExisting: Boolean,
        matchers: List<PathMatcher>,
        destination: String,
        defaultDestinationDir: Path,
        lock: DistributedLock
    ): List<String>

    suspend fun delete(id: String, manifest: WorkspaceManifest)
}
