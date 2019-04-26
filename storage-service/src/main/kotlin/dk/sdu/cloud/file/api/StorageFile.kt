package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonProperty
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
    @get:JsonProperty("fileType")
    val fileTypeOrNull: FileType?

    @get:JsonProperty("path")
    val pathOrNull: String?

    @get:JsonProperty("createdAt")
    val createdAtOrNull: Long?

    @get:JsonProperty("modifiedAt")
    val modifiedAtOrNull: Long?

    @get:JsonProperty("ownerName")
    val ownerNameOrNull: String?

    @get:JsonProperty("size")
    val sizeOrNull: Long?

    @get:JsonProperty("acl")
    val aclOrNull: List<AccessEntry>?

    @get:JsonProperty("sensitivityLevel")
    val sensitivityLevelOrNull: SensitivityLevel?

    @get:JsonProperty("ownSensitivityLevel")
    val ownSensitivityLevelOrNull: SensitivityLevel?

    @get:JsonProperty("link")
    val linkOrNull: Boolean?

    @get:JsonProperty("annotations")
    @Deprecated("no longer in use")
    val annotationsOrNull: Set<String>?

    @get:JsonProperty("fileId")
    val fileIdOrNull: String?

    @get:JsonProperty("creator")
    val creatorOrNull: String?
}

val StorageFile.fileType: FileType
    get() = fileTypeOrNull!!

val StorageFile.path: String
    get() = pathOrNull!!

val StorageFile.createdAt: Long
    get() = createdAtOrNull!!

val StorageFile.modifiedAt: Long
    get() = modifiedAtOrNull!!

val StorageFile.ownerName: String
    get() = ownerNameOrNull!!

val StorageFile.size: Long
    get() = sizeOrNull!!

val StorageFile.acl: List<AccessEntry>?
    get() = acl

val StorageFile.sensitivityLevel: SensitivityLevel
    get() = sensitivityLevelOrNull!!

val StorageFile.ownSensitivityLevel: SensitivityLevel?
    get() = ownSensitivityLevel

val StorageFile.link: Boolean
    get() = linkOrNull!!

@Deprecated("no longer in use")
val StorageFile.annotations: Set<String>
    get() = annotationsOrNull!!

val StorageFile.fileId: String
    get() = fileIdOrNull!!

val StorageFile.creator: String
    get() = creatorOrNull!!

data class StorageFileImpl(
    override val fileTypeOrNull: FileType?,
    override val pathOrNull: String?,
    override val createdAtOrNull: Long?,
    override val modifiedAtOrNull: Long?,
    override val ownerNameOrNull: String?,
    override val sizeOrNull: Long?,
    override val aclOrNull: List<AccessEntry>? = emptyList(),
    override val sensitivityLevelOrNull: SensitivityLevel? = SensitivityLevel.PRIVATE,
    override val linkOrNull: Boolean? = false,
    override val annotationsOrNull: Set<String>? = emptySet(),
    override val fileIdOrNull: String?,
    override val creatorOrNull: String?,
    override val ownSensitivityLevelOrNull: SensitivityLevel?
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
    creator: String = ownerName,
    ownSensitivityLevel: SensitivityLevel? = SensitivityLevel.PRIVATE
): StorageFileImpl {
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
        creator,
        ownSensitivityLevel
    )
}

/**
 * Describes the sensitivity classification of a file
 */
enum class SensitivityLevel {
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


