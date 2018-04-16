package dk.sdu.cloud.storage.api

import java.net.URI

// Contains the shared model (used as part of interface) of this service

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}

class StoragePath(
    path: String,
    val host: String = "",
    val name: String = path.substringAfterLast('/')
) {
    // Remove trailing '/' (avoid doing so if the entire path is '/')
    val path = if (path.length > 1) path.removeSuffix("/") else path

    fun pushRelative(relativeURI: String): StoragePath {
        return StoragePath(URI("$path/$relativeURI").normalize().path, host)
    }

    fun push(vararg components: String): StoragePath = pushRelative(components.joinToString("/"))

    fun pop(): StoragePath = StoragePath(URI(path).resolve(".").normalize().path, host)
}

enum class AccessRight {
    READ,
    WRITE,
    EXECUTE
}

data class MetadataEntry(val key: String, val value: String)
typealias Metadata = List<MetadataEntry>

data class AccessEntry(val entity: String, val isGroup: Boolean, val rights: Set<AccessRight>)
typealias AccessControlList = List<AccessEntry>

enum class FileType {
    FILE,
    DIRECTORY,
    LINK
}

data class StorageFile(
    val type: FileType,
    val path: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val ownerName: String,
    val size: Long,
    val acl: List<AccessEntry>? = null,
    val favorited: Boolean? = null,
    val sensitivityLevel: SensitivityLevel? = null
)

enum class SensitivityLevel {
    OPEN_ACCESS,
    CONFIDENTIAL,
    SENSITIVE
}

