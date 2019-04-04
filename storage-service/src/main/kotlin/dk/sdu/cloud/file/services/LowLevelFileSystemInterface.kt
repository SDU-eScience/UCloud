package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
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
    suspend fun copy(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun move(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>>

    suspend fun listDirectory(
        ctx: Ctx,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    suspend fun delete(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.Deleted>>

    suspend fun openForWriting(
        ctx: Ctx,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun write(
        ctx: Ctx,
        writer: suspend (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    suspend fun makeDirectory(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun getExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<String>

    suspend fun setExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean = true
    ): FSResult<Unit>

    suspend fun listExtendedAttribute(
        ctx: Ctx,
        path: String
    ): FSResult<List<String>>

    suspend fun deleteExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<Unit>

    suspend fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<FileRow>

    suspend fun openForReading(
        ctx: Ctx,
        path: String
    ): FSResult<Unit>

    suspend fun <R> read(
        ctx: Ctx,
        range: LongRange? = null,
        consumer: suspend (InputStream) -> R
    ): R

    suspend fun createSymbolicLink(
        ctx: Ctx,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    suspend fun createACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>

    suspend fun removeACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>

    suspend fun chmod(
        ctx: Ctx,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>
}
