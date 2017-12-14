package esciencecloudui

data class AccessEntry(val entity: iRODSUser, val right: AccessRight)

enum class FileType {
    FILE,
    DIRECTORY
}

class StoragePath(val uri: String, val path: String, val host: String, val name: String)

class StorageFile(
        val type: FileType,
        val path: StoragePath,
        val createdAt: Long,
        val modifiedAt: Long,
        val size: Int,
        val acl: List<AccessEntry>
)

enum class AccessRight {
    NONE,
    READ,
    READ_WRITE,
    OWN
}

data class iRODSUser(val name: String, val displayName: String, val zone: String, val type: String)