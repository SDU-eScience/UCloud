package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService

interface PathLike<T> {
    val path: String
    fun withNewPath(path: String): T
}

inline class InternalFile(override val path: String) : PathLike<InternalFile> {
    override fun withNewPath(path: String): InternalFile = InternalFile(path)
}

inline class UCloudFile(override val path: String) : PathLike<UCloudFile> {
    override fun withNewPath(path: String): UCloudFile = UCloudFile(path)
}

fun <T : PathLike<T>> T.parent(): T = withNewPath(path.parent())
fun <T : PathLike<T>> T.parents(): List<T> = path.parents().map { withNewPath(it) }
fun <T : PathLike<T>> T.normalize(): T = withNewPath(path.normalize())
fun <T : PathLike<T>> T.components(): List<String> = path.components()
fun <T : PathLike<T>> T.fileName(): String = path.fileName()

class FileQueries(
    private val aclService: AclService,
    private val rootDirectory: InternalFile,
) {
    fun convertUCloudPathToInternalFile(file: UCloudFile): InternalFile {
        TODO()
    }

    fun retrieve(actor: Actor, file: UCloudFile, flags: FilesIncludeFlags): UFile {
        TODO()
    }

    fun retrieveTypeInternal(actor: Actor, file: InternalFile): FileType {
        TODO()
    }

    fun retrieveInternal(actor: Actor, file: InternalFile, flags: FilesIncludeFlags): UFile {
        TODO()
    }

    fun fileExists(actor: Actor, file: UCloudFile): Boolean {
        TODO()
    }

    fun fileExistsInternal(actor: Actor, file: InternalFile): Boolean {
        TODO()
    }

    fun listInternalFilesOrNull(actor: Actor, file: InternalFile): List<InternalFile>? {
        TODO()
    }

    fun convertInternalFileToUFile(file: InternalFile, flags: FilesIncludeFlags): UFile {
        TODO()
    }

    fun browseFiles(
        actor: Actor,
        path: String,
        flags: FilesIncludeFlags,
        pagination: PaginationRequestV2,
    ): PageV2<UFile> {
        TODO()
    }

    fun renameAccordingToPolicy(desiredPath: String, policy: WriteConflictPolicy): String {
        TODO()
    }
}
