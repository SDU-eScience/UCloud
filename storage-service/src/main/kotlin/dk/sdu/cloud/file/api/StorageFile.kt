package dk.sdu.cloud.file.api

enum class AccessRight {
    READ,
    WRITE,
    EXECUTE
}

data class AccessEntry(val entity: String, val isGroup: Boolean, val rights: Set<AccessRight>)

enum class FileType {
    FILE,
    DIRECTORY,
    LINK
}

data class StorageFile(
    val fileType: FileType,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val ownerName: String,
    val size: Long = 0,
    val acl: List<AccessEntry> = emptyList(),
    val favorited: Boolean = false,
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.PRIVATE,
    val link: Boolean = false,
    val annotations: Set<String> = emptySet(),
    val fileId: String = "",
    val creator: String = ownerName
)

/**
 * Describes the sensitivity classification of a file
 */
enum class SensitivityLevel {
    /**
     * Open access means that a file can be read by the public.
     *
     * Having this classification requires the data to be non-sensitive. This classification will also change the
     * access permissions of the file.
     */
    OPEN_ACCESS,

    /**
     * The default sensitivity level. The file is private, but doesn't contain any confidential/sensitive information.
     *
     * The file can only be read by you (or anyone you share it with).
     */
    PRIVATE,

    /**
     * The file contains confidential information. (Non-personal)
     */
    CONFIDENTIAL,

    /**
     * The file contains sensitive information. (Personal)
     */
    SENSITIVE
}

