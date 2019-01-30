package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

const val DEFAULT_RESOURCES_TO_LOAD = "fav"

enum class FileResource(val text: String) {
    FAVORITES("fav")
}

private fun fileResourcesToString(load: Set<FileResource>) =
    load.joinToString(",") { it.text }

interface LoadFileResource {
    val load: String?
}

val LoadFileResource.resourcesToLoad: Set<FileResource>
    get() = (load ?: DEFAULT_RESOURCES_TO_LOAD).split(",").mapNotNull { param ->
        FileResource.values().find { it.text == param }
    }.toSet()

class StorageFileWithMetadata(
    delegate: StorageFile,

    // custom resources
    val favorited: Boolean?
) : StorageFile by delegate

data class ListAtDirectoryRequest internal constructor(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    val order: SortOrder?,
    val sortBy: FileSortBy?,

    override val load: String?
) : WithPaginationRequest, LoadFileResource

fun ListAtDirectoryRequest(
    path: String,
    itemsPerPage: Int?,
    page: Int?,
    order: SortOrder?,
    sortBy: FileSortBy?,
    load: Set<FileResource>
): ListAtDirectoryRequest {
    return ListAtDirectoryRequest(path, itemsPerPage, page, order, sortBy, fileResourcesToString(load))
}

typealias ListAtDirectoryResponse = Page<StorageFileWithMetadata>

data class LookupFileInDirectoryRequest(
    val path: String,
    val itemsPerPage: Int,
    val order: SortOrder,
    val sortBy: FileSortBy,
    override val load: String?
) : LoadFileResource

fun LookupFileInDirectoryRequest(
    path: String,
    itemsPerPage: Int,
    order: SortOrder,
    sortBy: FileSortBy,
    load: Set<FileResource>
): LookupFileInDirectoryRequest = LookupFileInDirectoryRequest(
    path, itemsPerPage, order,
    sortBy, fileResourcesToString(load)
)

typealias LookupFileInDirectoryResponse = Page<StorageFileWithMetadata>

data class StatRequest(
    val path: String,
    override val load: String?
) : LoadFileResource

typealias StatResponse = StorageFileWithMetadata

/**
 * The file gateway provides additional information to file lookups.
 *
 * It mirrors the API provided by the underlying file API but adds additional arguments for loading more
 * associated data. A client can request additional data by setting the load query parameter to a set of resources to
 * load (see [FileResource.text]). This list should be comma separated.
 */
object FileGatewayDescriptions : RESTDescriptions("${FileDescriptions.namespace}.gateway") {
    val baseContext = "/api/files"

    val listAtDirectory = callDescription<ListAtDirectoryRequest, ListAtDirectoryResponse, CommonErrorMessage> {
        name = "listAtDirectory"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
            desiredScope = FileDescriptions.listAtPath.requiredAuthScope
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListAtDirectoryRequest::path)
            +boundTo(ListAtDirectoryRequest::itemsPerPage)
            +boundTo(ListAtDirectoryRequest::page)
            +boundTo(ListAtDirectoryRequest::order)
            +boundTo(ListAtDirectoryRequest::sortBy)

            +boundTo(ListAtDirectoryRequest::load)
        }
    }

    val lookupFileInDirectory =
        callDescription<LookupFileInDirectoryRequest, LookupFileInDirectoryResponse, CommonErrorMessage> {
            name = "lookupFileInDirectory"
            method = HttpMethod.Get

            auth {
                access = AccessRight.READ
                desiredScope = FileDescriptions.lookupFileInDirectory.requiredAuthScope
            }

            path {
                using(baseContext)
                +"lookup"
            }

            params {
                +boundTo(LookupFileInDirectoryRequest::path)
                +boundTo(LookupFileInDirectoryRequest::itemsPerPage)
                +boundTo(LookupFileInDirectoryRequest::sortBy)
                +boundTo(LookupFileInDirectoryRequest::order)
                +boundTo(LookupFileInDirectoryRequest::load)
            }
        }

    val stat = callDescription<StatRequest, StatResponse, CommonErrorMessage> {
        name = "stat"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
            desiredScope = FileDescriptions.stat.requiredAuthScope
        }

        path {
            using(baseContext)
            +"stat"
        }

        params {
            +boundTo(StatRequest::path)
            +boundTo(StatRequest::load)
        }
    }
}
