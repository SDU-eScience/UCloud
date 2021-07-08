package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*

// ---

typealias FilesProviderBrowseRequest = FilesBrowseRequest
typealias FilesProviderBrowseResponse = FilesBrowseResponse

typealias FilesProviderRetrieveRequest = FilesRetrieveRequest
typealias FilesProviderRetrieveResponse = FilesRetrieveResponse

typealias FilesProviderMoveRequest = FilesMoveRequest
typealias FilesProviderMoveResponse = FilesMoveResponse

typealias FilesProviderCopyRequest = FilesCopyRequest
typealias FilesProviderCopyResponse = FilesCopyResponse

typealias FilesProviderDeleteRequest = FilesDeleteRequest
typealias FilesProviderDeleteResponse = FilesDeleteResponse

typealias FilesProviderCreateFolderRequest = FilesCreateFolderRequest
typealias FilesProviderCreateFolderResponse = FilesCreateFolderResponse

typealias FilesProviderUpdateAclRequest = FilesUpdateAclRequest
typealias FilesProviderUpdateAclResponse = FilesUpdateAclResponse

typealias FilesProviderTrashRequest = FilesTrashRequest
typealias FilesProviderTrashResponse = FilesTrashResponse

typealias FilesProviderCreateDownloadRequest = FilesCreateDownloadRequest
typealias FilesProviderCreateDownloadResponse = FilesCreateDownloadResponse

typealias FilesProviderCreateUploadRequest = FilesCreateUploadRequest
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
