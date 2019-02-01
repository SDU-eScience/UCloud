package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonTypeInfo

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

@JsonTypeInfo(defaultImpl = StorageFileImpl::class, use = JsonTypeInfo.Id.MINIMAL_CLASS)
interface StorageFile {
    val fileType: FileType
    val path: String
    val createdAt: Long
    val modifiedAt: Long
    val ownerName: String
    val size: Long
    val acl: List<AccessEntry>?
    val sensitivityLevel: SensitivityLevel
    val link: Boolean
    val annotations: Set<String>
    val fileId: String
    val creator: String
}

data class StorageFileImpl(
    override val fileType: FileType,
    override val path: String,
    override val createdAt: Long,
    override val modifiedAt: Long,
    override val ownerName: String,
    override val size: Long,
    override val acl: List<AccessEntry>?,
    override val sensitivityLevel: SensitivityLevel,
    override val link: Boolean,
    override val annotations: Set<String>,
    override val fileId: String,
    override val creator: String
) : StorageFile

fun StorageFile(
    fileType: FileType,
    path: String,
    createdAt: Long = System.currentTimeMillis(),
    modifiedAt: Long = System.currentTimeMillis(),
    ownerName: String,
    size: Long = 0,
    acl: List<AccessEntry>? = emptyList(),
    sensitivityLevel: SensitivityLevel = SensitivityLevel.PRIVATE,
    link: Boolean = false,
    annotations: Set<String> = emptySet(),
    fileId: String = "",
    creator: String = ownerName
): StorageFile {
    return StorageFileImpl(
        fileType,
        path,
        createdAt,
        modifiedAt,
        ownerName,
        size,
        acl,
        sensitivityLevel,
        link,
        annotations,
        fileId,
        creator
    )
}


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

