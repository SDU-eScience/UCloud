package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.services.cephfs.FileAttribute
import dk.sdu.cloud.storage.services.cephfs.FileRow
import dk.sdu.cloud.storage.util.FSUserContext
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

    data class User(val user: String): FSACLEntity() {
        override val serializedEntity: String = "u:$user"
    }

    data class Group(val group: String): FSACLEntity() {
        override val serializedEntity: String = "g:$group"
    }

    object Other : FSACLEntity() {
        override val serializedEntity: String = "o"
    }
}

interface LowLevelFileSystemInterface {
    fun copy(
        ctx: FSUserContext,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrModified>>
    
    fun move(
        ctx: FSUserContext,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>>
    
    fun listDirectory(
        ctx: FSUserContext,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    fun delete(
        ctx: FSUserContext,
        path: String
    ): FSResult<List<StorageEvent.Deleted>>
    
    fun openForWriting(
        ctx: FSUserContext,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrModified>>
    
    fun <R> write(
        ctx: FSUserContext,
        writer: (OutputStream) -> R
    ): R
    
    fun tree(
        ctx: FSUserContext,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>
    
    fun makeDirectory(
        ctx: FSUserContext,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrModified>>
    
    fun getExtendedAttribute(
        ctx: FSUserContext,
        path: String,
        attribute: String
    ): FSResult<String>
    
    fun setExtendedAttribute(
        ctx: FSUserContext,
        path: String,
        attribute: String,
        value: String
    ): FSResult<Unit>

    fun listExtendedAttribute(
        ctx: FSUserContext,
        path: String
    ): FSResult<List<String>>

    fun deleteExtendedAttribute(
        ctx: FSUserContext,
        path: String,
        attribute: String
    ): FSResult<Unit>

    fun stat(
        ctx: FSUserContext,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<FileRow>

    fun openForReading(
        ctx: FSUserContext,
        path: String
    ): FSResult<Unit>

    fun <R> read(
        ctx: FSUserContext,
        range: IntRange? = null,
        consumer: (InputStream) -> R
    ): R

    fun createSymbolicLink(
        ctx: FSUserContext,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrModified>>

    fun createACLEntry(
        ctx: FSUserContext,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>

    fun removeACLEntry(
        ctx: FSUserContext,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>
}