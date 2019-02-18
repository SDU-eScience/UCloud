package dk.sdu.cloud.indexing.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.file.api.StorageFile
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
object LookupDescriptions : CallDescriptionContainer("indexing") {
    const val baseContext: String = "/api/indexing/lookup"

    val reverseLookup = call<ReverseLookupRequest, ReverseLookupResponse, CommonErrorMessage>("reverseLookup") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"reverse"
            }

            params {
                +boundTo(ReverseLookupRequest::fileId)
            }
        }
    }

    val reverseLookupFiles =
        call<ReverseLookupFilesRequest, ReverseLookupFilesResponse, CommonErrorMessage>("reverseLookupFiles") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

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
}
