package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*

// ---

typealias FilesProviderBrowseRequest = ProxiedRequest<FilesBrowseRequest>
typealias FilesProviderBrowseResponse = FilesBrowseResponse

typealias FilesProviderRetrieveRequest = ProxiedRequest<FilesRetrieveRequest>
typealias FilesProviderRetrieveResponse = FilesRetrieveResponse

typealias FilesProviderMoveRequest = ProxiedRequest<FilesMoveRequest>
typealias FilesProviderMoveResponse = FilesMoveResponse

typealias FilesProviderCopyRequest = ProxiedRequest<FilesCopyRequest>
typealias FilesProviderCopyResponse = FilesCopyResponse

typealias FilesProviderDeleteRequest = ProxiedRequest<FilesDeleteRequest>
typealias FilesProviderDeleteResponse = FilesDeleteResponse

typealias FilesProviderCreateFolderRequest = ProxiedRequest<FilesCreateFolderRequest>
typealias FilesProviderCreateFolderResponse = FilesCreateFolderResponse

typealias FilesProviderUpdateAclRequest = ProxiedRequest<FilesUpdateAclRequest>
typealias FilesProviderUpdateAclResponse = FilesUpdateAclResponse

typealias FilesProviderTrashRequest = ProxiedRequest<FilesTrashRequest>
typealias FilesProviderTrashResponse = FilesTrashResponse

typealias FilesProviderCreateDownloadRequest = ProxiedRequest<FilesCreateDownloadRequest>
typealias FilesProviderCreateDownloadResponse = FilesCreateDownloadResponse

typealias FilesProviderCreateUploadRequest = ProxiedRequest<FilesCreateUploadRequest>
typealias FilesProviderCreateUploadResponse = FilesCreateUploadResponse

// ---

open class FilesProvider(namespace: String) : CallDescriptionContainer("files.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/files"

    val browse = call<FilesProviderBrowseRequest, FilesProviderBrowseResponse, CommonErrorMessage>("browse") {
        httpUpdate(baseContext, "browse", roles = Roles.SERVICE) // TODO FIXME
    }

    val retrieve = call<FilesProviderRetrieveRequest, FilesProviderRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpUpdate(baseContext, "retrieve", roles = Roles.SERVICE) // TODO FIXME
    }

    val move = call<FilesProviderMoveRequest, FilesProviderMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move", roles = Roles.SERVICE)
    }

    val copy = call<FilesProviderCopyRequest, FilesProviderCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy", roles = Roles.SERVICE)
    }

    val delete = call<FilesProviderDeleteRequest, FilesProviderDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.SERVICE)
    }

    val createFolder = call<FilesProviderCreateFolderRequest, FilesProviderCreateFolderResponse,
        CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder", roles = Roles.SERVICE)
    }

    val updateAcl = call<FilesProviderUpdateAclRequest, FilesProviderUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl", roles = Roles.SERVICE)
    }

    val trash = call<FilesProviderTrashRequest, FilesProviderTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash", roles = Roles.SERVICE)
    }

    val createUpload = call<FilesProviderCreateUploadRequest, FilesProviderCreateUploadResponse,
        CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload", roles = Roles.SERVICE)
    }

    val createDownload = call<FilesProviderCreateDownloadRequest, FilesProviderCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download", roles = Roles.SERVICE)
    }
}
