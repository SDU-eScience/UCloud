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
    @Deprecated(message = "Replaced with fileType", replaceWith = ReplaceWith("fileType"))
    val type: FileType,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val ownerName: String,
    val size: Long = 0,
    val acl: List<AccessEntry> = emptyList(),
    val favorited: Boolean = false,
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.CONFIDENTIAL,
    val link: Boolean = false,
    val annotations: Set<String> = emptySet(),
    val fileId: String = ""
) {
    val fileType: FileType = type
}

enum class SensitivityLevel {
    OPEN_ACCESS,
    CONFIDENTIAL,
    SENSITIVE
}

