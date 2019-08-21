package dk.sdu.cloud.app.fs.api

data class SharedFileSystem(
    val id: String,
    val owner: String,
    val backend: String,
    val title: String,
    val createdAt: Long
)

/**
 * A shared file system JSON compatible with StorageFile of the storage-service.
 *
 *  This is intentionally not extending the same type. Many fields are provided purely for comparability purposes.
 */
class SharedFileSystemFileWrapper(fs: SharedFileSystem, size: Long) {
    val fileType: String = "SHARED_FS"
    val path: String = "/shared-fs/${fs.title}-${fs.id.split("-").first().toUpperCase()}"
    val createdAt = fs.createdAt
    val modifiedAt = fs.createdAt
    val ownerName = fs.owner
    val size: Long = size
    val acl: List<Any?> = emptyList()
    val annotations: Any? = null
    val link: Boolean? = null
    val fileId: String = fs.id
    val creator: String = fs.owner
    val canonicalPath = path
    val sensitivityLevel: String = "PRIVATE"
    val ownSensitivityLevel: String = "PRIVATE"
}
