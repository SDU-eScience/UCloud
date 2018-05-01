package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonIgnore

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
    val acl: List<AccessEntry>,
    val favorited: Boolean,
    val sensitivityLevel: SensitivityLevel,
    val link: Boolean,
    @get:JsonIgnore val inode: Long = 0
)

enum class SensitivityLevel {
    OPEN_ACCESS,
    CONFIDENTIAL,
    SENSITIVE
}

