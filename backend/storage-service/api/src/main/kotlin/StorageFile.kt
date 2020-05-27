package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class AccessRight {
    READ,
    WRITE
}

data class AccessEntry(val entity: ACLEntity, val rights: Set<AccessRight>)

enum class FileType {
    FILE,
    DIRECTORY
}

@JsonTypeInfo(defaultImpl = StorageFileImpl::class, use = JsonTypeInfo.Id.MINIMAL_CLASS)
interface StorageFile {
    @get:JsonProperty("fileType")
    val fileTypeOrNull: FileType?

    /**
     * The canonical path of the file
     *
     * Because UCloud doesn't support links we are guaranteed that each file has exactly one canonical path.
     */
    @get:JsonProperty("path")
    val pathOrNull: String?

    @get:JsonProperty("createdAt")
    val createdAtOrNull: Long?

    @get:JsonProperty("modifiedAt")
    val modifiedAtOrNull: Long?

    /**
     * The UCloud username of the creator of this file
     */
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

    val permissionAlert: Boolean
        get() = false
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
    get() = aclOrNull

val StorageFile.sensitivityLevel: SensitivityLevel
    get() = sensitivityLevelOrNull!!

val StorageFile.ownSensitivityLevel: SensitivityLevel?
    get() = ownSensitivityLevelOrNull

data class StorageFileImpl(
    override val fileTypeOrNull: FileType?,
    override val pathOrNull: String?,
    override val createdAtOrNull: Long?,
    override val modifiedAtOrNull: Long?,
    override val ownerNameOrNull: String?,
    override val sizeOrNull: Long?,
    override val aclOrNull: List<AccessEntry>? = emptyList(),
    override val sensitivityLevelOrNull: SensitivityLevel? = SensitivityLevel.PRIVATE,
    override val ownSensitivityLevelOrNull: SensitivityLevel?,
    override val permissionAlert: Boolean = false
) : StorageFile

fun StorageFile.mergeWith(other: StorageFile): StorageFile {
    return StorageFileImpl(
        fileTypeOrNull = fileTypeOrNull ?: other.fileTypeOrNull,
        pathOrNull = pathOrNull ?: other.pathOrNull,
        createdAtOrNull = createdAtOrNull ?: other.createdAtOrNull,
        modifiedAtOrNull = modifiedAtOrNull ?: other.modifiedAtOrNull,
        ownerNameOrNull = ownerNameOrNull ?: other.ownerNameOrNull,
        sizeOrNull = sizeOrNull ?: other.sizeOrNull,
        aclOrNull = aclOrNull ?: other.aclOrNull,
        sensitivityLevelOrNull = sensitivityLevelOrNull ?: other.sensitivityLevelOrNull,
        ownSensitivityLevelOrNull = ownSensitivityLevelOrNull ?: other.ownSensitivityLevelOrNull,
        permissionAlert = permissionAlert || other.permissionAlert
    )
}

fun StorageFile(
    fileType: FileType,
    path: String,
    createdAt: Long = System.currentTimeMillis(),
    modifiedAt: Long = System.currentTimeMillis(),
    ownerName: String = path.components().getOrElse(1) { "_storage" },
    size: Long = 0,
    acl: List<AccessEntry>? = emptyList(),
    sensitivityLevel: SensitivityLevel = SensitivityLevel.PRIVATE,
    ownSensitivityLevel: SensitivityLevel? = SensitivityLevel.PRIVATE,
    permissionAlert: Boolean = false
): StorageFileImpl {
    return StorageFileImpl(
        fileTypeOrNull = fileType,
        pathOrNull = path,
        createdAtOrNull = createdAt,
        modifiedAtOrNull = modifiedAt,
        ownerNameOrNull = ownerName,
        sizeOrNull = size,
        aclOrNull = acl,
        sensitivityLevelOrNull = sensitivityLevel,
        ownSensitivityLevelOrNull = ownSensitivityLevel
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
