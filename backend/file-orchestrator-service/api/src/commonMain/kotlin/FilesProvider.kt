package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
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
        httpBrowse(baseContext)
    }

    val retrieve = call<FilesProviderRetrieveRequest, FilesProviderRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val move = call<FilesProviderMoveRequest, FilesProviderMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move")
    }

    val copy = call<FilesProviderCopyRequest, FilesProviderCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy")
    }

    val delete = call<FilesProviderDeleteRequest, FilesProviderDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val createFolder = call<FilesProviderCreateFolderRequest, FilesProviderCreateFolderResponse,
        CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder")
    }

    val updateAcl = call<FilesProviderUpdateAclRequest, FilesProviderUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    val trash = call<FilesProviderTrashRequest, FilesProviderTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash")
    }

    val createUpload = call<FilesProviderCreateUploadRequest, FilesProviderCreateUploadResponse,
        CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload")
    }

    val createDownload = call<FilesProviderCreateDownloadRequest, FilesProviderCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download")
    }
}
