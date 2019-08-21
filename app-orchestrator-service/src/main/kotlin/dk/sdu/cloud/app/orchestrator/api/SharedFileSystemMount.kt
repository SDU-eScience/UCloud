package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.app.store.api.SharedFileSystemType

data class SharedFileSystemMountDescription(val sharedFileSystemId: String, val mountedAt: String)

data class SharedFileSystemMount(
    val sharedFileSystem: SharedFileSystem,
    val mountedAt: String,
    val fsType: SharedFileSystemType? = null,
    val exportToPeers: Boolean = false
)
