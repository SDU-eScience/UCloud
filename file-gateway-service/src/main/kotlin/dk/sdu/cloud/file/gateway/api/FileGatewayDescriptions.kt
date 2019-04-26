package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class ListAtDirectoryRequest internal constructor(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    val order: SortOrder?,
    val sortBy: FileSortBy?,

    override val attributes: String?
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
    override val attributes: String?
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
    override val attributes: String?
) : LoadFileResource

typealias StatResponse = StorageFileWithMetadata

/**
 * The file gateway provides additional information to file lookups.
 *
 * It mirrors the API provided by the underlying file API but adds additional arguments for loading more
 * associated data. A client can request additional data by setting the attributes query parameter to a set of resources to
 * attributes (see [FileResource.text]). This list should be comma separated.
 */
object FileGatewayDescriptions : CallDescriptionContainer("${FileDescriptions.namespace}.gateway") {
    val baseContext = "/api/files"

    val listAtDirectory = call<ListAtDirectoryRequest, ListAtDirectoryResponse, CommonErrorMessage>("listAtDirectory") {
        auth {
            access = AccessRight.READ
            requiredScope = FileDescriptions.listAtPath.requiredAuthScope
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListAtDirectoryRequest::path)
                +boundTo(ListAtDirectoryRequest::itemsPerPage)
                +boundTo(ListAtDirectoryRequest::page)
                +boundTo(ListAtDirectoryRequest::order)
                +boundTo(ListAtDirectoryRequest::sortBy)

                +boundTo(ListAtDirectoryRequest::attributes)
            }
        }
    }

    val lookupFileInDirectory =
        call<LookupFileInDirectoryRequest, LookupFileInDirectoryResponse, CommonErrorMessage>("lookupFileInDirectory") {
            auth {
                access = AccessRight.READ
                requiredScope = FileDescriptions.lookupFileInDirectory.requiredAuthScope
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"lookup"
                }

                params {
                    +boundTo(LookupFileInDirectoryRequest::path)
                    +boundTo(LookupFileInDirectoryRequest::itemsPerPage)
                    +boundTo(LookupFileInDirectoryRequest::sortBy)
                    +boundTo(LookupFileInDirectoryRequest::order)
                    +boundTo(LookupFileInDirectoryRequest::attributes)
                }
            }
        }

    val stat = call<StatRequest, StatResponse, CommonErrorMessage>("stat") {
        auth {
            access = AccessRight.READ
            requiredScope = FileDescriptions.stat.requiredAuthScope
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"stat"
            }

            params {
                +boundTo(StatRequest::path)
                +boundTo(StatRequest::attributes)
            }
        }
    }
}
