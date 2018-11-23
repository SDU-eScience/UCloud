package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.SensitivityLevel
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
    fun copy(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    fun move(
        ctx: Ctx,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>>

    fun listDirectory(
        ctx: Ctx,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    fun delete(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.Deleted>>

    fun openForWriting(
        ctx: Ctx,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    fun write(
        ctx: Ctx,
        writer: (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>>

    fun makeDirectory(
        ctx: Ctx,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    fun getExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<String>

    fun setExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String,
        value: String
    ): FSResult<Unit>

    fun listExtendedAttribute(
        ctx: Ctx,
        path: String
    ): FSResult<List<String>>

    fun deleteExtendedAttribute(
        ctx: Ctx,
        path: String,
        attribute: String
    ): FSResult<Unit>

    fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<FileRow>

    fun openForReading(
        ctx: Ctx,
        path: String
    ): FSResult<Unit>

    fun <R> read(
        ctx: Ctx,
        range: IntRange? = null,
        consumer: (InputStream) -> R
    ): R

    fun createSymbolicLink(
        ctx: Ctx,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>

    fun createACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>

    fun removeACLEntry(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean = false,
        recursive: Boolean = false
    ): FSResult<Unit>

    fun chmod(
        ctx: Ctx,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>>
}
