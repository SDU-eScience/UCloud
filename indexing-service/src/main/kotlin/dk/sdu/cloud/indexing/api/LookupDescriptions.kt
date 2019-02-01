package dk.sdu.cloud.indexing.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import io.ktor.http.HttpMethod

/**
 * @see [LookupDescriptions.reverseLookup]
 */
data class ReverseLookupRequest(val fileId: String) {
    constructor(fileIds: List<String>) : this(fileIds.joinToString(","))

    @get:JsonIgnore
    val allIds: List<String>
        get() = fileId.split(",")
}

/**
 * @see [LookupDescriptions.reverseLookup]
 */
data class ReverseLookupResponse(val canonicalPath: List<String?>)

typealias ReverseLookupFilesRequest = ReverseLookupRequest
data class ReverseLookupFilesResponse(val files: List<StorageFile?>)

/**
 * Provides REST calls for looking up files in the efficient file index.
 */
object LookupDescriptions : RESTDescriptions("indexing") {
    const val baseContext: String = "/api/indexing/lookup"

    val reverseLookup = callDescription<ReverseLookupRequest, ReverseLookupResponse, CommonErrorMessage> {
        name = "reverseLookup"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"reverse"
        }

        params {
            +boundTo(ReverseLookupRequest::fileId)
        }
    }

    val reverseLookupFiles =
        callDescription<ReverseLookupFilesRequest, ReverseLookupFilesResponse, CommonErrorMessage> {
            name = "reverseLookupFiles"
            method = HttpMethod.Get

            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ
            }

            path {
                using(baseContext)
                +"reverse"
                +"file"
            }

            params {
                +boundTo(ReverseLookupFilesRequest::fileId)
            }
        }
}
