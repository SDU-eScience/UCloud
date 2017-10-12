package dk.sdu.escience.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

typealias StoragePath = String

enum class AccessRight {
    NONE,
    READ,
    READ_WRITE,
    OWN
}

class AccessEntry(val entity: Entity, val right: AccessRight)
typealias AccessControlList = List<AccessEntry>

class MetadataEntry(val key: String, val value: String)
typealias Metadata = List<MetadataEntry>

enum class FileType {
    FILE,
    DIRECTORY
}

class StorageFile(
        val name: String,
        val type: FileType,
        val acl: AccessControlList?,
        val metadata: Metadata?
)

class FileStat(
        val path: StoragePath,
        val createdAtUnixMs: Long,
        val modifiedAtUnixMs: Long,
        val ownerName: String,
        val sizeInBytes: Long,
        val systemDefinedChecksum: String
)

enum class ArchiveType {
    TAR,
    TAR_GZ,
    ZIP
}

open class StorageException(cause: String) : RuntimeException(cause)
class PermissionException(cause: String) : StorageException(cause)
class NotFoundException(val resourceType: String, val name: String, val internalCause: String = "Unknown") :
        StorageException("Could not find resource of type '$resourceType' with name '$name' (Cause: $internalCause)")
class NotEmptyException(cause: String) : StorageException(cause)
class ConnectionException(cause: String) : StorageException(cause)

// TODO Doing stuff in a single transaction while supporting iRODS is not really easy.
// Having to support iRODS will obviously change the interface we provide
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

    /**
     * Retrieves a range of bytes from a file at [path] and write them to [output]
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun getAtRange(range: LongRange, path: StoragePath, output: OutputStream)

    /**
     * Downloads a bundle from [path] and write the bundle, without extraction, to [output]
     *
     * @throws PermissionException If a file cannot be created at the given [path]
     * @throws NotFoundException If object at [path] is not a directory or does not exist
     */
    fun bundleGet(path: StoragePath, output: OutputStream, archiveType: ArchiveType)

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

    fun deleteWhere(path: StoragePath, vararg query: Any?)

    /**
     * Empties the trash can.
     *
     * If the underlying system does not supports a trashcan this this method should do nothing. The system _must not_
     * throw an exception just because the underlying method is not supported.
     *
     * @see usesTrashCan
     * @see delete
     */
    fun emptyTrashCan()

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

    /**
     * Verifies that the contents of [localFile] is consistent with [remoteFile]
     *
     * This _should not_ cause the [localFile] to be uploaded to the remote server.
     *
     * @throws PermissionException If a file cannot be created at the given [remoteFile]
     * @throws NotFoundException If object at [remoteFile] is not a directory or does not exist
     */
    fun verifyConsistency(localFile: File, remoteFile: StoragePath): Boolean

    val homeDirectory: StoragePath
}

interface AccessControlOperations {
    /**
     * Updates the ACL for an object at [path]
     *
     * If the [recursive] is given then this will be applied, recursively, to all children.
     */
    fun updateACL(path: StoragePath, rights: AccessControlList, recursive: Boolean = false)

    /**
     * Lists the ACL for a single object at [path]
     *
     * @throws NotFoundException if the object is not found
     */
    fun listAt(path: StoragePath): AccessControlList
}

interface MetadataOperations {
    /**
     * Updates the metadata list for an object at [path]
     */
    fun updateMetadata(path: StoragePath, newOrUpdatesAttributes: Metadata, attributesToDeleteIfExists: List<String>)

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
     * Additionally it is possible to load ACLs for every returned object with [preloadACLs]. Metadata can, similarly,
     * be loaded with [preloadMetadata]. These flags will guarantee that the corresponding fields of StorageFile will
     * be non-null.
     *
     * Note that ACLs and metadata might be retrieved even if the flags are off, but they must be retrieved when
     * they are off. This is up to the underlying implementation of determining what is the most efficient.
     */
    fun listAt(path: StoragePath, preloadACLs: Boolean = false, preloadMetadata: Boolean = false): List<StorageFile>

    fun listAtPathWithMetadata(path: StoragePath, query: Any?): List<StorageFile> // TODO

    /**
     * Retrieve stats about an object
     *
     * @throws NotFoundException If the object could not be found
     */
    fun stat(path: StoragePath): FileStat =
        statBulk(path).firstOrNull() ?: throw NotFoundException("file", path)

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
        return statBulk(path).firstOrNull() != null
    }
}