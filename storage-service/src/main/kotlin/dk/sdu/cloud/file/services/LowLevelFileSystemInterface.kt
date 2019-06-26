package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.util.FSException
import java.io.InputStream
import java.io.OutputStream

class FSResult<T>(
    val statusCode: Int,
    value: T? = null
) {
    private val _value: T? = value
    val value: T get() = _value!!
}

sealed class FSACLEntity {
    abstract val serializedEntity: String

    data class User(val user: String) : FSACLEntity() {
        override val serializedEntity: String = "u:$user"
    }

    data class Group(val group: String) : FSACLEntity() {
        override val serializedEntity: String = "g:$group"
    }

    object Other : FSACLEntity() {
        override val serializedEntity: String = "o"
    }
}

interface LowLevelFileSystemInterface<in Ctx : CommandRunner> {
    /**
     * Copies the underlying the file at [from] to the new path at [to].
     *
     * File sensitivity attribute is copied from the old file, all other attributes are discarded.
     *
     * If the file located at [from] is a directory this will create a new directory at [to]. The contents of the
     * directory is _not_ copied.
     *
     * @throws FSException.PermissionException
     * @throws FSException.BadRequest If [to] already exists and is not of the same as [from].
     * @throws FSException.AlreadyExists If [allowOverwrite] is true and [to] already exists.
     *
     * @return A list of files updated. This will usually only be a single file.
     */
    suspend fun copy(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    /**
     * Moves the file at [from] to the new path at [to].
     *
     * @throws FSException.PermissionException
     * @throws FSException.BadRequest If [to] already exists and is not of the same as [from].
     * @throws FSException.AlreadyExists If [allowOverwrite] is true and [to] already exists.
     *
     * @return A list of files updated. If the file at [from] is a directory this will include all of its children.
     */
    suspend fun move(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>>

    /**
     * Lists the file at the path given by [directory].
     *
     * The attributes included in the [FileRow] depends on the [mode] parameter.
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound If the file at [directory] does not exist.
     */
    suspend fun listDirectory(
        ctx: Ctx,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    /**
     * Deletes the file at [path] and all of its children recursively.
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     *
     * @return A list of all files that were deleted.
     */
    suspend fun delete(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.Deleted>>


    /**
     * Opens a file for writing. The contents of [path] will be truncated immediately.
     *
     * This method must be called before using [write]. [write] must be called after calling this method.
     * Calling [openForWriting] twice without calling [write] is considered an error.
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     * @throws FSException.AlreadyExists If [allowOverwrite] is true and [path] already exists.
     *
     * @return A list of files created. This is usually only a single file.
     */
    suspend fun openForWriting(
        ctx: Ctx,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    /**
     * Writes to the file opened by [openForWriting].
     *
     * Must be called after calling [openForWriting]. It not possible to call [write] twice in a row without also
     * calling [openForWriting].
     */
    suspend fun write(
        ctx: Ctx,
        writer: suspend (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    /**
     * Opens a file for reading.
     *
     * This method must be called before using [read]. [read] must be called after calling this method. Calling
     * [openForReading] twice without calling [read] is considered an error.
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     */
    suspend fun openForReading(
        ctx: Ctx,
        path: String
    ): FSResult<Unit>

    /**
     * Reads the file opened by [openForReading].
     *
     * This method must be called after calling [openForReading]. It is not possible to call [read] twice in a row
     * without also calling [openForReading].
     *
     * If [range] is not null the bytes specified in [range] will be read.
     *
     * @return The result of [consumer]
     */
    suspend fun <R> read(
        ctx: Ctx,
        range: LongRange? = null,
        consumer: suspend (InputStream) -> R
    ): R

    /**
     * Returns a 'tree'-view of the files starting at [path].
     *
     * This will include all children of [path] including the file located at [path]. [tree] provides no guarantees
     * for ordering.
     *
     * Symbolic links that are children of [path] will not be followed.
     *
     * If [path] is a file then the result will contain just the file itself.
     *
     * If [path] is a symbolic link it will be followed and the result will be equivalent to calling [tree] on its
     * target.
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     *
     * @return A list of all children of [path]. This will include the file at [path].
     */
    suspend fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    /**
     * Creates a directory at [path].
     *
     * @throws FSException.AlreadyExists if [path] already exists
     * @throws FSException.PermissionException
     *
     * @return A list of files created. This will usually only be a single file.
     */
    suspend fun makeDirectory(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    /**
     * Returns the attribute named [attribute] for the file at [path].
     *
     * @throws FSException.NotFound if [path] does not exist.
     * @throws FSException.NotFound if [attribute] does not exist.
     * @throws FSException.PermissionException
     */
    suspend fun getExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<String>

    /**
     * Sets [attribute] to [value] at [path].
     *
     * @throws FSException.NotFound if [path] does not exist
     * @throws FSException.AlreadyExists if [allowOverwrite] is true and [attribute] exists
     * @throws FSException.PermissionException
     */
    suspend fun setExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean = true
    ): FSResult<Unit>

    /**
     * Returns a list of attribute keys for the file at [path].
     *
     * @throws FSException.NotFound
     * @throws FSException.PermissionException
     */
    suspend fun listExtendedAttribute(
        ctx: Ctx,
        path: String
    ): FSResult<List<String>>

    /**
     * Deletes [attribute] at [path].
     *
     * @throws FSException.NotFound if [path] does not exist.
     * @throws FSException.NotFound if [attribute] does not exist.
     * @throws FSException.PermissionException
     */
    suspend fun deleteExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<Unit>

    /**
     * Returns file attributes specified by [mode] for the file at [path].
     *
     * @throws FSException.NotFound
     * @throws FSException.PermissionException
     */
    suspend fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<FileRow>

    /**
     * Creates a symbolic link at [linkPath] pointing to [targetPath].
     *
     * @throws FSException.PermissionException
     */
    suspend fun createSymbolicLink(
        ctx: Ctx,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    /**
     * Creates an entry in the ACL for [path].
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     */
    suspend fun createACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        transferOwnershipTo: String? = null
    ): FSResult<Unit>

    /**
     * Deletes an entry in the ACL for [path].
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     */
    suspend fun removeACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean = false,
        transferOwnershipTo: String? = null
    ): FSResult<Unit>

    /**
     * Changes the permissions of the file at [path].
     *
     * @throws FSException.PermissionException
     * @throws FSException.NotFound
     */
    suspend fun chmod(
        ctx: Ctx,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun chown(
        ctx: Ctx,
        path: String,
        owner: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun checkPermissions(ctx: Ctx, path: String, requireWrite: Boolean): FSResult<Boolean>
}
