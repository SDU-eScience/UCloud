package dk.sdu.cloud.storage.services.ext

import dk.sdu.cloud.storage.api.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

interface PathOperations {
    val localRoot: StoragePath
    val homeDirectory: StoragePath
    fun parseAbsolute(absolutePath: String, isMissingZone: Boolean = false): StoragePath
}

interface FileOperations {
    /**
     * Creates a directory at [path]. If the [recursive] flag is set then parent directories will attempt to be
     * created as well.
     *
     * @throws NotFoundException If the directory could not be created. This will happen if [recursive] flag
     * is missing and parent does not exist. This will also happen if the connected user does not have
     * appropriate permissions.
     */
    fun createDirectory(path: StoragePath, recursive: Boolean = false)

    /**
     * Creates a file, replacing if needed, at [path]. The content will be read from [source].
     *
     * This method will take ownership of the [source] stream and close it when finished.
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun put(path: StoragePath, source: InputStream)

    /**
     * Creates a file, replacing if needed, at [path]. The content will be read from [localFile].
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun put(path: StoragePath, localFile: File) {
        put(path, localFile.inputStream())
    }

    /**
     * Uploads a bundle of files to [path]. Archive will be read from [source].
     *
     * This is a more efficient version than running [put] in a loop. If the underlying system supports it, this will
     * happen in a single transaction.
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun bundlePut(path: StoragePath, source: ZipInputStream)

    /**
     * Retrieves a single file and write its contents to [output].
     *
     * This method will take ownership of the [output] stream and close it when finished.
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun get(path: StoragePath, output: OutputStream)

    /**
     * Retrieves a single file and write its contents to [localFile].
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun get(path: StoragePath, localFile: File) {
        get(path, localFile.outputStream())
    }

    val usesTrashCan: Boolean

    /**
     * Deletes an object from [path]. If the object is a directory and the [recursive] is given, then the
     * contents will be deleted as well.
     *
     * This operation will delete as much as possible, that is everything that the user has permission to delete and
     * fits the deletion criteria. TODO Example
     *
     * Depending on the underlying system this might move the file to a trashcan or it might delete it permanently.
     * For more information see [usesTrashCan]
     *
     * @throws NotEmptyException If [recursive] is not set and the directory is not empty
     * @throws PermissionException If the object was found but the user lacks sufficient permissions to perform this
     * action
     * @throws NotFoundException If the object could not be found by the user
     */
    fun delete(path: StoragePath, recursive: Boolean = false)

    /**
     * Moves an object with path [from] to an object with path [to]
     */
    fun move(from: StoragePath, to: StoragePath)

    /**
     * Copies an object with path [from] to an object with path [to]
     *
     * If this method is called on a directory it will perform a deep copy
     */
    fun copy(from: StoragePath, to: StoragePath)

    fun get(path: StoragePath): InputStream
}

interface AccessControlOperations {
    /**
     * Updates the ACL for an object at [path]
     *
     * If the [recursive] is given then this will be applied, recursively, to all children.
     */
    fun updateACL(path: StoragePath, rights: AccessControlList, recursive: Boolean = false): Unit

    /**
     * Lists the ACL for a single object at [path]
     *
     * @throws NotFoundException if the object is not found
     */
    fun listAt(path: StoragePath): AccessControlList

    /**
     * Get the connected user's permission on an object
     *
     * @throws NotFoundException if the object is not found or the connected user does not have permission to know
     * about the object
     */
    fun getMyPermissionAt(path: StoragePath): AccessRight
}

interface MetadataOperations {
    /**
     * Updates the metadata list for an object at [path]
     */
    fun updateMetadata(path: StoragePath, newOrUpdatesAttributes: Metadata, attributesToDeleteIfExists: Metadata)

    /**
     * Removes all metadata associated with an object at [path]
     */
    fun removeAllMetadata(path: StoragePath)
}

interface FileQueryOperations {
    /**
     * List objects at [path]
     *
     * If the object at [path] is a directory all children in the directory will be listed. This will not recurse.
     * Otherwise only information for that file will be loaded.
     *
     * Note that ACLs and metadata might be retrieved even if the flags are off, but they must be retrieved when
     * they are off. This is up to the underlying implementation of determining what is the most efficient.
     */
    fun listAt(path: StoragePath): List<StorageFile>

    /**
     * Retrieve stats about an object
     *
     * @throws NotFoundException If the object could not be found
     */
    fun stat(path: StoragePath): FileStat {
        val capture = statBulk(path)
        val stat = capture.firstOrNull() ?: throw NotFoundException("file", path.path)
        return stat
    }

    /**
     * Retrieve, in bulk, stats about objects
     *
     * Individual stats will be `null` if they are not found.
     */
    fun statBulk(vararg paths: StoragePath): List<FileStat?>

    /**
     * Checks if an object at [path] exists
     *
     * Default implementation will delegate to [stat]
     */
    fun exists(path: StoragePath): Boolean {
        val capture = statBulk(path)
        return capture.firstOrNull() != null
    }

    fun treeAt(path: StoragePath, modifiedAfter: Long? = null): List<InternalFile>
}

data class InternalFile(
    val fileName: String,
    val parent: String,
    val isDirectory: Boolean,
    val modifiedAt: Long,
    val accessRight: AccessRight,
    val physicalPath: String? = null
)

