package dk.sdu.cloud.storage.model

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
    NONE,
    READ,
    READ_WRITE,
    OWN
}

data class MetadataEntry(val key: String, val value: String)
typealias Metadata = List<MetadataEntry>

data class AccessEntry(val entity: Entity, val right: AccessRight)
typealias AccessControlList = List<AccessEntry>

enum class FileType {
    FILE,
    DIRECTORY
}

data class StorageFile(
    val type: FileType,
    val path: StoragePath,
    val createdAt: Long,
    val modifiedAt: Long,
    val size: Int,
    val acl: List<AccessEntry>,
    val favorited: Boolean,
    val sensitivityLevel: SensitivityLevel
)

enum class SensitivityLevel {
    OPEN_ACCESS,
    CONFIDENTIAL,
    SENSITIVE
}

data class FileStat(
    val path: StoragePath,
    val createdAtUnixMs: Long,
    val modifiedAtUnixMs: Long,
    val ownerName: String,
    val sizeInBytes: Long,
    val systemDefinedChecksum: String,
    val fileType: FileType
)

enum class ArchiveType {
    TAR,
    TAR_GZ,
    ZIP
}
