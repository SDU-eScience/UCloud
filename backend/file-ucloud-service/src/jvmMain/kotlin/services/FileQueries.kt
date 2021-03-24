package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2
import dk.sdu.cloud.file.orchestrator.api.FilesIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.UFile
import dk.sdu.cloud.file.ucloud.services.acl.AclService

inline class InternalFile(val path: String)
inline class UCloudFile(val path: String)

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
}
