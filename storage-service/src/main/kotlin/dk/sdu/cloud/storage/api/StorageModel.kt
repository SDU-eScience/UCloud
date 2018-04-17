package dk.sdu.cloud.storage.api

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

