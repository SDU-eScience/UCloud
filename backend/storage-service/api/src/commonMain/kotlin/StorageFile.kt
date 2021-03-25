package dk.sdu.cloud.file.api

import dk.sdu.cloud.service.Time
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccessRight {
    READ,
    WRITE
}

@Serializable
data class AccessEntry(val entity: ACLEntity, val rights: Set<AccessRight>)

@Serializable
enum class FileType {
    FILE,
    DIRECTORY
}

/*
interface StorageFile {
    val fileTypeOrNull: FileType?

    /**
     * The canonical path of the file
     *
     * Because UCloud doesn't support links we are guaranteed that each file has exactly one canonical path.
     */
    val pathOrNull: String?

    val createdAtOrNull: Long?

    val modifiedAtOrNull: Long?

    /**
     * The UCloud username of the creator of this file
     */
    val ownerNameOrNull: String?

    val sizeOrNull: Long?

    val aclOrNull: List<AccessEntry>?

    val sensitivityLevelOrNull: SensitivityLevel?

    val ownSensitivityLevelOrNull: SensitivityLevel?

    val permissionAlert: Boolean
        get() = false
}
 */

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

@Serializable
open class StorageFile(
    @SerialName("fileType")
    val fileTypeOrNull: FileType? = null,
    @SerialName("path")
    val pathOrNull: String? = null,
    @SerialName("createdAt")
    val createdAtOrNull: Long? = null,
    @SerialName("modifiedAt")
    val modifiedAtOrNull: Long? = null,
    @SerialName("ownerName")
    val ownerNameOrNull: String? = null,
    @SerialName("size")
    val sizeOrNull: Long? = null,
    @SerialName("acl")
    val aclOrNull: List<AccessEntry>? = emptyList(),
    @SerialName("sensitivityLevel")
    val sensitivityLevelOrNull: SensitivityLevel? = SensitivityLevel.PRIVATE,
    @SerialName("ownSensitivityLevel")
    val ownSensitivityLevelOrNull: SensitivityLevel? = null,
    @SerialName("permissionAlert")
    val permissionAlert: Boolean = false
)

fun StorageFile.mergeWith(other: StorageFile): StorageFile {
    return StorageFile(
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
    createdAt: Long = Time.now(),
    modifiedAt: Long = Time.now(),
    ownerName: String = path.components().getOrElse(1) { "_storage" },
    size: Long = 0,
    acl: List<AccessEntry>? = emptyList(),
    sensitivityLevel: SensitivityLevel = SensitivityLevel.PRIVATE,
    ownSensitivityLevel: SensitivityLevel? = SensitivityLevel.PRIVATE,
    permissionAlert: Boolean = false
): StorageFile {
    return StorageFile(
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
@Serializable
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
