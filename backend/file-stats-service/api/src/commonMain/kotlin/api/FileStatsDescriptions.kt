package dk.sdu.cloud.file.stats.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.api.StorageFile
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class UsageRequest(val path: String? = null)
@Serializable
data class UsageResponse(val bytes: Long, val path: String)

@Serializable
data class DirectorySizesRequest(
    val paths: List<String>
)

@Serializable
data class DirectorySizesResponse(
    val size: Long
)

typealias SearchResult = StorageFile

@TSTopLevel
object FileStatsDescriptions : CallDescriptionContainer("files.stats") {
    private val baseContext = "/api/files/stats"

    val usage = call<UsageRequest, UsageResponse, CommonErrorMessage>("usage") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"usage"
            }

            params {
                +boundTo(UsageRequest::path)
            }
        }
    }

    val directorySize = call<DirectorySizesRequest, DirectorySizesResponse, CommonErrorMessage>("directorySize") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"directory-sizes"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
