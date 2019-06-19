package dk.sdu.cloud.app.api

import dk.sdu.cloud.app.fs.api.SharedFileSystem

data class SharedFileSystemMountDescription(val sharedFileSystemId: String, val mountedAt: String)
data class SharedFileSystemMount(val sharedFileSystem: SharedFileSystem, val mountedAt: String)
